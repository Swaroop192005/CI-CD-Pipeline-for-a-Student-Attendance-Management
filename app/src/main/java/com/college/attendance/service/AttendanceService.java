package com.college.attendance.service;

import com.college.attendance.config.CurrentUser;
import com.college.attendance.domain.AttendanceRecord;
import com.college.attendance.domain.Student;
import com.college.attendance.domain.WorkflowStatus;
import com.college.attendance.dto.AttendanceForm;
import com.college.attendance.repository.AttendanceRecordRepository;
import com.college.attendance.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Creating, reading and correcting attendance records.
 *
 * <p>Status transitions are deliberately <em>not</em> here: they belong to
 * {@code WorkflowService}, which is the only component allowed to change
 * {@code workflowStatus} (BR-07). This class may create a record in
 * {@code DRAFT} and may refuse to edit one that has left an editable
 * state, but it never advances the workflow itself.
 */
@Service
public class AttendanceService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceService.class);

    private final AttendanceRecordRepository records;
    private final StudentRepository students;
    private final CurrentUser currentUser;

    public AttendanceService(AttendanceRecordRepository records, StudentRepository students,
                             CurrentUser currentUser) {
        this.records = records;
        this.students = students;
        this.currentUser = currentUser;
    }

    // ---- Read -------------------------------------------------------------

    @Transactional(readOnly = true)
    public AttendanceRecord require(Long id) {
        return records.findById(id).orElseThrow(() -> new RecordNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<AttendanceRecord> list(Pageable pageable) {
        return records.findAllByOrderBySessionDateDescPeriodNumberAsc(pageable);
    }

    @Transactional(readOnly = true)
    public List<Student> activeStudents() {
        return students.findByActiveTrueOrderByRollNumberAsc();
    }

    @Transactional(readOnly = true)
    public List<String> knownSubjectCodes() {
        return records.findDistinctSubjectCodes();
    }

    // ---- Create -----------------------------------------------------------

    /**
     * Creates a record in {@code DRAFT}, attributed to the acting user.
     *
     * @throws RecordNotFoundException  the roll number is not on the roll
     * @throws DuplicateRecordException a record already exists for this
     *                                  student, subject, date and period (BR-01)
     * @throws IllegalArgumentException the session date is in the future (FR-06)
     */
    @Transactional
    public AttendanceRecord create(AttendanceForm form) {
        Student student = requireStudent(form.getRollNumber());
        rejectFutureDate(form.getSessionDate());

        if (records.existsByStudent_RollNumberAndSubjectCodeAndSessionDateAndPeriodNumber(
                student.getRollNumber(), form.getSubjectCode(),
                form.getSessionDate(), form.getPeriodNumber())) {
            throw new DuplicateRecordException(student.getRollNumber(), form.getSubjectCode(),
                    form.getSessionDate(), form.getPeriodNumber());
        }

        String actor = currentUser.username();
        AttendanceRecord record = new AttendanceRecord(
                student, form.getSubjectCode(), form.getSessionDate(), form.getPeriodNumber(),
                form.getAttendanceStatus(), trimToNull(form.getRemarks()), actor);

        AttendanceRecord saved = records.save(record);
        log.info("Created attendance record {} for {} in {} on {} period {} by {}",
                saved.getId(), student.getRollNumber(), saved.getSubjectCode(),
                saved.getSessionDate(), saved.getPeriodNumber(), actor);
        return saved;
    }

    // ---- Update -----------------------------------------------------------

    /**
     * Applies a correction to a record that is still editable.
     *
     * <p>The original {@code markedBy} is preserved: a correction does not
     * transfer authorship, and the audit trail would be worthless if it
     * did (FR-12).
     *
     * @throws RecordLockedException  the record is submitted or approved (FR-11)
     * @throws NotPermittedException  the actor is neither the author nor an admin
     */
    @Transactional
    public AttendanceRecord update(Long id, AttendanceForm form) {
        AttendanceRecord record = require(id);
        assertMayEdit(record);
        rejectFutureDate(form.getSessionDate());

        Student student = requireStudent(form.getRollNumber());
        boolean keyChanged = !student.getRollNumber().equals(record.getStudent().getRollNumber())
                || !form.getSubjectCode().equals(record.getSubjectCode())
                || !form.getSessionDate().equals(record.getSessionDate())
                || form.getPeriodNumber() != record.getPeriodNumber();

        if (keyChanged && records.existsByStudent_RollNumberAndSubjectCodeAndSessionDateAndPeriodNumber(
                student.getRollNumber(), form.getSubjectCode(),
                form.getSessionDate(), form.getPeriodNumber())) {
            throw new DuplicateRecordException(student.getRollNumber(), form.getSubjectCode(),
                    form.getSessionDate(), form.getPeriodNumber());
        }

        record.setStudent(student);
        record.setSubjectCode(form.getSubjectCode());
        record.setSessionDate(form.getSessionDate());
        record.setPeriodNumber(form.getPeriodNumber());
        record.setAttendanceStatus(form.getAttendanceStatus());
        record.setRemarks(trimToNull(form.getRemarks()));
        record.setUpdatedAt(Instant.now());

        AttendanceRecord saved = records.save(record);
        log.info("Updated attendance record {} by {} (author remains {})",
                saved.getId(), currentUser.username(), saved.getMarkedBy());
        return saved;
    }

    // ---- Authorisation ----------------------------------------------------

    /**
     * Whether the acting user may edit this record: it must still be in an
     * editable state, and they must be its author or an administrator.
     */
    @Transactional(readOnly = true)
    public boolean mayEdit(AttendanceRecord record) {
        if (!record.isEditable()) {
            return false;
        }
        return currentUser.isAdmin() || record.wasMarkedBy(currentUser.username());
    }

    private void assertMayEdit(AttendanceRecord record) {
        if (!record.isEditable()) {
            throw new RecordLockedException(record.getId(), record.getWorkflowStatus());
        }
        if (!currentUser.isAdmin() && !record.wasMarkedBy(currentUser.username())) {
            throw new NotPermittedException(
                    "Record " + record.getId() + " was entered by " + record.getMarkedBy()
                            + " and can only be corrected by them or an administrator");
        }
    }

    // ---- Helpers ----------------------------------------------------------

    private Student requireStudent(String rollNumber) {
        return students.findByRollNumber(rollNumber)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No student on the roll with number " + rollNumber));
    }

    private void rejectFutureDate(LocalDate sessionDate) {
        if (sessionDate != null && sessionDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "Session date cannot be in the future: " + sessionDate);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Count of records in a given workflow state, for the dashboard. */
    @Transactional(readOnly = true)
    public long countByStatus(WorkflowStatus status) {
        return records.countByWorkflowStatus(status);
    }
}
