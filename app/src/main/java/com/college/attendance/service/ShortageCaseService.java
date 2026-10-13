package com.college.attendance.service;

import com.college.attendance.config.AttendanceProperties;
import com.college.attendance.config.CurrentUser;
import com.college.attendance.domain.AttendanceRecord;
import com.college.attendance.domain.AttendanceStatus;
import com.college.attendance.domain.CaseStatus;
import com.college.attendance.domain.ShortageCase;
import com.college.attendance.domain.Student;
import com.college.attendance.repository.AttendanceRecordRepository;
import com.college.attendance.repository.ShortageCaseRepository;
import com.college.attendance.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The conversation between a head of department and a student whose
 * attendance has fallen too far.
 *
 * <p>This is what the department does instead of approving every record.
 * Approving sessions one at a time produced a queue that grew with class
 * size and told nobody anything: a head of department clicking approve four
 * hundred times is not reviewing anything. Raising a case against the few
 * students actually in trouble is work with a point to it.
 *
 * <p>Business rules enforced here:
 *
 * <ul>
 *   <li><b>BR-12</b> — a case may only be raised against a student who is
 *       actually below the shortage threshold. Without this the mechanism
 *       drifts back into being a general-purpose queue.</li>
 *   <li><b>BR-13</b> — one open case per student. A second question while
 *       the first is unanswered leaves the student unable to say which
 *       reply belongs to which.</li>
 *   <li><b>BR-14</b> — a case cannot be decided before the student has
 *       answered. The point of the case is to hear the explanation; a head
 *       of department who could reject on sight would simply be issuing a
 *       verdict.</li>
 *   <li><b>BR-15</b> — only the student a case is about may answer it.</li>
 *   <li><b>BR-16</b> — accepting marks the chosen absences {@code EXCUSED},
 *       which removes them from both sides of the percentage under BR-03.
 *       Accepting an explanation and leaving the figure untouched would be
 *       sympathy without consequence.</li>
 * </ul>
 */
@Service
public class ShortageCaseService {

    private static final Logger log = LoggerFactory.getLogger(ShortageCaseService.class);
    private static final List<CaseStatus> OPEN =
            List.of(CaseStatus.AWAITING_RESPONSE, CaseStatus.ANSWERED);

    private final ShortageCaseRepository cases;
    private final AttendanceRecordRepository records;
    private final StudentRepository students;
    private final CurrentUser currentUser;
    private final AttendanceProperties properties;

    public ShortageCaseService(ShortageCaseRepository cases, AttendanceRecordRepository records,
                               StudentRepository students, CurrentUser currentUser,
                               AttendanceProperties properties) {
        this.cases = cases;
        this.records = records;
        this.students = students;
        this.currentUser = currentUser;
        this.properties = properties;
    }

    // ---- head of department -----------------------------------------------

    @Transactional
    public ShortageCase raise(String rollNumber, String question) {
        if (question == null || question.isBlank()) {
            throw new NotPermittedException("Ask the student something; a case needs a question.");
        }

        Student student = students.findByRollNumber(rollNumber)
                .orElseThrow(() -> new NotPermittedException("No student " + rollNumber + "."));

        cases.findFirstByStudent_RollNumberAndStatusInOrderByOpenedAtDesc(rollNumber, OPEN)
                .ifPresent(existing -> {
                    throw new DuplicateCaseException(rollNumber, existing.getId());
                });

        double percent = percentFor(rollNumber);
        int threshold = properties.shortageThreshold();
        if (percent >= threshold) {
            throw new NotPermittedException(
                    rollNumber + " is at " + percent + "%, which is not below the "
                    + threshold + "% shortage threshold.");
        }

        ShortageCase raised = cases.save(new ShortageCase(
                student, percent, threshold, question.trim(), currentUser.username()));
        log.info("Case {} raised against {} at {}% by {}",
                raised.getId(), rollNumber, percent, currentUser.username());
        return raised;
    }

    /**
     * Accept the student's explanation and excuse their absences in the
     * given window.
     */
    @Transactional
    public ShortageCase accept(Long caseId, String note, LocalDate from, LocalDate to) {
        ShortageCase shortage = require(caseId);
        assertDecidable(shortage);

        if (from == null || to == null || to.isBefore(from)) {
            throw new NotPermittedException(
                    "Give the dates the explanation covers, earliest first.");
        }

        List<AttendanceRecord> excused = records
                .findByStudent_RollNumberOrderBySessionDateAsc(shortage.getStudent().getRollNumber())
                .stream()
                .filter(r -> r.getAttendanceStatus() == AttendanceStatus.ABSENT)
                .filter(r -> !r.getSessionDate().isBefore(from) && !r.getSessionDate().isAfter(to))
                .toList();

        for (AttendanceRecord record : excused) {
            record.setAttendanceStatus(AttendanceStatus.EXCUSED);
            record.setExcusedBy(currentUser.username());
            record.setExcusedAt(Instant.now());
            record.setExcuseNote(note == null || note.isBlank()
                    ? "Explanation accepted (case " + caseId + ")"
                    : note.trim());
            record.setUpdatedAt(Instant.now());
            records.save(record);
        }

        shortage.setStatus(CaseStatus.ACCEPTED);
        shortage.setDecisionNote(note);
        shortage.setDecidedBy(currentUser.username());
        shortage.setDecidedAt(Instant.now());
        shortage.setExcusedCount(excused.size());
        shortage.setExcusedFrom(from);
        shortage.setExcusedTo(to);

        log.info("Case {} accepted by {}: {} sessions excused between {} and {}",
                caseId, currentUser.username(), excused.size(), from, to);
        return cases.save(shortage);
    }

    @Transactional
    public ShortageCase reject(Long caseId, String note) {
        ShortageCase shortage = require(caseId);
        assertDecidable(shortage);

        shortage.setStatus(CaseStatus.REJECTED);
        shortage.setDecisionNote(note);
        shortage.setDecidedBy(currentUser.username());
        shortage.setDecidedAt(Instant.now());

        log.info("Case {} rejected by {}", caseId, currentUser.username());
        return cases.save(shortage);
    }

    // ---- student ----------------------------------------------------------

    @Transactional
    public ShortageCase respond(Long caseId, String response) {
        if (response == null || response.isBlank()) {
            throw new NotPermittedException("Write an explanation before sending it.");
        }

        ShortageCase shortage = require(caseId);

        String roll = currentUser.restrictedToRollNumber()
                .orElseThrow(() -> new NotPermittedException(
                        "Only the student a case is about may answer it."));
        if (!roll.equals(shortage.getStudent().getRollNumber())) {
            throw new NotPermittedException("Case " + caseId + " is not yours to answer.");
        }
        if (shortage.getStatus() != CaseStatus.AWAITING_RESPONSE) {
            throw new NotPermittedException(
                    "This case has already been answered or decided.");
        }

        shortage.setResponse(response.trim());
        shortage.setRespondedAt(Instant.now());
        shortage.setStatus(CaseStatus.ANSWERED);

        log.info("Case {} answered by {}", caseId, roll);
        return cases.save(shortage);
    }

    // ---- queries ----------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ShortageCase> open() {
        return cases.findByStatusInOrderByOpenedAtDesc(OPEN);
    }

    @Transactional(readOnly = true)
    public List<ShortageCase> all() {
        return cases.findByStatusInOrderByOpenedAtDesc(List.of(CaseStatus.values()));
    }

    @Transactional(readOnly = true)
    public List<ShortageCase> forStudent(String rollNumber) {
        return cases.findByStudent_RollNumberOrderByOpenedAtDesc(rollNumber);
    }

    @Transactional(readOnly = true)
    public ShortageCase require(Long caseId) {
        return cases.findById(caseId)
                .orElseThrow(() -> new RecordNotFoundException(caseId));
    }

    /**
     * Overall attendance for one student, by the same rules the dashboard
     * uses: excused sessions leave both sides (BR-03).
     */
    @Transactional(readOnly = true)
    public double percentFor(String rollNumber) {
        List<AttendanceRecord> own = records.findByStudent_RollNumberOrderBySessionDateAsc(rollNumber);
        long counted = own.stream()
                .filter(r -> r.getAttendanceStatus().countsTowardsTotal()).count();
        long attended = own.stream()
                .filter(r -> r.getAttendanceStatus().countsTowardsTotal())
                .filter(r -> r.getAttendanceStatus().countsAsAttended()).count();
        return counted == 0 ? 0.0 : Math.round(attended * 1000.0 / counted) / 10.0;
    }

    private static void assertDecidable(ShortageCase shortage) {
        if (shortage.getStatus() == CaseStatus.AWAITING_RESPONSE) {
            throw new NotPermittedException(
                    "The student has not answered yet. The point of the case is the explanation.");
        }
        if (shortage.getStatus().isDecided()) {
            throw new NotPermittedException("Case " + shortage.getId() + " has already been decided.");
        }
    }
}
