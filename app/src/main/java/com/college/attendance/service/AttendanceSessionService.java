package com.college.attendance.service;

import com.college.attendance.config.CurrentUser;
import com.college.attendance.domain.AttendanceRecord;
import com.college.attendance.domain.AttendanceSession;
import com.college.attendance.domain.AttendanceStatus;
import com.college.attendance.domain.SessionStatus;
import com.college.attendance.domain.Student;
import com.college.attendance.repository.AttendanceRecordRepository;
import com.college.attendance.repository.AttendanceSessionRepository;
import com.college.attendance.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Opening, marking and closing a teaching session.
 *
 * <p>Attendance is no longer typed in by faculty one student at a time.
 * Faculty opens the session, students mark themselves while it is open, and
 * closing it writes an absence for everyone who did not. That shifts the
 * effort from the person teaching to the people attending, and it means the
 * record is made during the class rather than reconstructed afterwards.
 *
 * <p>Business rules enforced here:
 *
 * <ul>
 *   <li><b>BR-09</b> — a student may only mark attendance while the session
 *       is open. Marking a closed session is refused, which is what stops a
 *       student recording themselves present for a class that has
 *       finished.</li>
 *   <li><b>BR-10</b> — closing a session writes {@code ABSENT} for every
 *       active student who did not mark. A closed session is therefore a
 *       complete record of the class, not a list of the students who
 *       happened to volunteer.</li>
 *   <li><b>BR-11</b> — a student may mark a given session only once. The
 *       uniqueness constraint behind BR-01 already makes a second attempt
 *       impossible at the database level; this service refuses it with a
 *       readable message first.</li>
 * </ul>
 */
@Service
public class AttendanceSessionService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceSessionService.class);

    private final AttendanceSessionRepository sessions;
    private final AttendanceRecordRepository records;
    private final StudentRepository students;
    private final CurrentUser currentUser;

    public AttendanceSessionService(AttendanceSessionRepository sessions,
                                    AttendanceRecordRepository records,
                                    StudentRepository students,
                                    CurrentUser currentUser) {
        this.sessions = sessions;
        this.records = records;
        this.students = students;
        this.currentUser = currentUser;
    }

    // ---- faculty ----------------------------------------------------------

    /**
     * Open a session so students can mark themselves present.
     *
     * @throws DuplicateRecordException if this subject, date and period has
     *                                  already been taken
     */
    @Transactional
    public AttendanceSession open(String subjectCode, LocalDate sessionDate, int periodNumber) {
        if (sessionDate.isAfter(LocalDate.now())) {
            throw new SessionNotOpenException("A session cannot be opened for a future date.");
        }
        if (sessions.existsBySubjectCodeAndSessionDateAndPeriodNumber(
                subjectCode, sessionDate, periodNumber)) {
            throw new DuplicateSessionException(subjectCode, sessionDate, periodNumber);
        }
        AttendanceSession session = sessions.save(
                new AttendanceSession(subjectCode, sessionDate, periodNumber, currentUser.username()));
        log.info("Session {} opened for {} on {} period {} by {}",
                session.getId(), subjectCode, sessionDate, periodNumber, currentUser.username());
        return session;
    }

    /**
     * Close a session and write an absence for every active student who did
     * not mark themselves present.
     */
    @Transactional
    public AttendanceSession close(Long sessionId) {
        AttendanceSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new RecordNotFoundException(sessionId));

        if (!session.isOpen()) {
            throw new SessionNotOpenException("Session " + sessionId + " is already closed.");
        }

        Set<String> marked = records
                .findBySubjectCodeAndSessionDateAndPeriodNumber(
                        session.getSubjectCode(), session.getSessionDate(), session.getPeriodNumber())
                .stream()
                .map(r -> r.getStudent().getRollNumber())
                .collect(Collectors.toSet());

        List<Student> active = students.findByActiveTrueOrderByRollNumberAsc();
        int absences = 0;
        for (Student student : active) {
            if (marked.contains(student.getRollNumber())) {
                continue;
            }
            records.save(new AttendanceRecord(
                    student, session.getSubjectCode(), session.getSessionDate(),
                    session.getPeriodNumber(), AttendanceStatus.ABSENT,
                    "Did not mark attendance before the session closed",
                    currentUser.username()));
            absences++;
        }

        session.setStatus(SessionStatus.CLOSED);
        session.setClosedBy(currentUser.username());
        session.setClosedAt(Instant.now());
        session.setMarkedCount(marked.size());
        session.setAbsentCount(absences);

        log.info("Session {} closed by {}: {} marked, {} absent",
                sessionId, currentUser.username(), marked.size(), absences);
        return sessions.save(session);
    }

    // ---- students ---------------------------------------------------------

    /**
     * Mark the signed-in student present for an open session.
     *
     * <p>The student is taken from the security context, never from the
     * request, so a student cannot mark attendance for somebody else by
     * editing a form field.
     */
    @Transactional
    public AttendanceRecord markPresent(Long sessionId) {
        AttendanceSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new RecordNotFoundException(sessionId));

        if (!session.isOpen()) {
            throw new SessionNotOpenException(
                    "Attendance for this session has closed. Speak to your faculty.");
        }

        String roll = currentUser.restrictedToRollNumber()
                .orElseThrow(() -> new NotPermittedException(
                        "Only a student account can mark its own attendance."));

        Student student = students.findByRollNumber(roll)
                .orElseThrow(() -> new NotPermittedException("No student record for " + roll + "."));

        if (records.existsByStudent_RollNumberAndSubjectCodeAndSessionDateAndPeriodNumber(
                roll, session.getSubjectCode(), session.getSessionDate(), session.getPeriodNumber())) {
            throw new DuplicateRecordException(
                    roll, session.getSubjectCode(), session.getSessionDate(),
                    session.getPeriodNumber());
        }

        AttendanceRecord record = records.save(new AttendanceRecord(
                student, session.getSubjectCode(), session.getSessionDate(),
                session.getPeriodNumber(), AttendanceStatus.PRESENT,
                "Marked by the student during the open session", roll));

        log.info("Student {} marked present for session {}", roll, sessionId);
        return record;
    }

    /**
     * Record a student as absent even though they marked themselves present.
     *
     * <p>This is the control that actually works. No check the server can
     * make proves a student is in the room: a rotating code can be passed on
     * by text, a network check covers a whole campus, and browser
     * geolocation is trivially faked. The person who can see the class is
     * the teacher, so faculty keep the final say and the override is
     * recorded against their name rather than applied silently.
     */
    @Transactional
    public AttendanceRecord strike(Long sessionId, String rollNumber, String reason) {
        AttendanceSession session = require(sessionId);
        if (!session.isOpen()) {
            throw new SessionNotOpenException(
                    "Session " + sessionId + " is closed; correct the record instead.");
        }

        AttendanceRecord record = records
                .findBySubjectCodeAndSessionDateAndPeriodNumber(
                        session.getSubjectCode(), session.getSessionDate(), session.getPeriodNumber())
                .stream()
                .filter(r -> r.getStudent().getRollNumber().equals(rollNumber))
                .findFirst()
                .orElseThrow(() -> new NotPermittedException(
                        rollNumber + " has not marked attendance for this session."));

        record.setAttendanceStatus(AttendanceStatus.ABSENT);
        record.setRemarks(reason == null || reason.isBlank()
                ? "Struck by faculty: not present in class"
                : "Struck by faculty: " + reason.trim());
        record.setUpdatedAt(Instant.now());
        // The page promises the override is logged against the faculty
        // member's name, and the remark alone did not keep that promise:
        // "Struck by faculty" names a role, not a person. A strike is a
        // judgement no server-side check can verify, so it is exactly the
        // change that has to be traceable to who made it.
        record.setUpdatedBy(currentUser.username());

        log.info("Student {} struck from session {} by {}",
                rollNumber, sessionId, currentUser.username());
        return records.save(record);
    }

    /** Who has marked themselves present so far, for the live roster. */
    @Transactional(readOnly = true)
    public List<AttendanceRecord> markedSoFar(Long sessionId) {
        AttendanceSession session = require(sessionId);
        return records.findBySubjectCodeAndSessionDateAndPeriodNumber(
                session.getSubjectCode(), session.getSessionDate(), session.getPeriodNumber());
    }

    // ---- queries ----------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AttendanceSession> openSessions() {
        return sessions.findByStatusOrderByOpenedAtDesc(SessionStatus.OPEN);
    }

    @Transactional(readOnly = true)
    public List<AttendanceSession> recent() {
        return sessions.findTop20ByOrderByOpenedAtDesc();
    }

    /** Open sessions the signed-in student has not yet marked. */
    @Transactional(readOnly = true)
    public List<AttendanceSession> openSessionsAwaiting(String rollNumber) {
        return openSessions().stream()
                .filter(s -> !records.existsByStudent_RollNumberAndSubjectCodeAndSessionDateAndPeriodNumber(
                        rollNumber, s.getSubjectCode(), s.getSessionDate(), s.getPeriodNumber()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AttendanceSession require(Long sessionId) {
        return sessions.findById(sessionId)
                .orElseThrow(() -> new RecordNotFoundException(sessionId));
    }

    /**
     * The session, provided it is still open.
     *
     * <p>Used before a passkey prompt is raised, so that a student whose
     * window has closed is told so instead of being asked for a fingerprint
     * and refused afterwards. {@link #markPresent} checks this again: this
     * one is for the message, that one is the enforcement.
     */
    public AttendanceSession requireOpen(Long sessionId) {
        AttendanceSession session = require(sessionId);
        if (!session.isOpen()) {
            throw new SessionNotOpenException(
                    "Attendance for this session has closed. Speak to your faculty.");
        }
        return session;
    }
}
