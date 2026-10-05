package com.college.attendance.service;

import com.college.attendance.config.CurrentUser;
import com.college.attendance.domain.AttendanceRecord;
import com.college.attendance.domain.Role;
import com.college.attendance.domain.WorkflowStatus;
import com.college.attendance.repository.AttendanceRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * The only component permitted to change a record's workflow status
 * (business rule BR-07).
 *
 * <p>Centralising it is what makes the audit story hold: there is exactly
 * one code path that can move a record between states, and it cannot be
 * taken without recording who took it and when. Every transition is
 * checked against three things before anything is written — the
 * transition is in the permitted set, the acting role may perform it, and
 * any precondition (a rejection reason) is satisfied. A refusal therefore
 * never leaves a partial change behind (FR-20).
 */
@Service
public class WorkflowService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);

    /** Roles allowed to move a record out of an editable state. */
    private static final Set<Role> MAY_SUBMIT = Set.of(Role.FACULTY, Role.ADMIN);

    /** Roles allowed to decide a submitted record. */
    private static final Set<Role> MAY_REVIEW = Set.of(Role.HOD, Role.ADMIN);

    private final AttendanceRecordRepository records;
    private final CurrentUser currentUser;

    public WorkflowService(AttendanceRecordRepository records, CurrentUser currentUser) {
        this.records = records;
        this.currentUser = currentUser;
    }

    // ---- Transitions ------------------------------------------------------

    /**
     * {@code DRAFT → SUBMITTED}, or {@code REJECTED → SUBMITTED} for a
     * re-submission after correction (FR-16, FR-19).
     */
    @Transactional
    public AttendanceRecord submit(Long id) {
        AttendanceRecord record = require(id);
        assertTransitionAllowed(record, WorkflowStatus.SUBMITTED);
        assertRole(MAY_SUBMIT, "submit a record for review");
        assertOwnership(record, "submit");

        return applyTransition(record, WorkflowStatus.SUBMITTED, null, false);
    }

    /** {@code SUBMITTED → APPROVED} (FR-17). The comment is optional. */
    @Transactional
    public AttendanceRecord approve(Long id, String comment) {
        AttendanceRecord record = require(id);
        assertTransitionAllowed(record, WorkflowStatus.APPROVED);
        assertRole(MAY_REVIEW, "approve a record");

        return applyTransition(record, WorkflowStatus.APPROVED, trimToNull(comment), true);
    }

    /**
     * {@code SUBMITTED → REJECTED} (FR-17, FR-18).
     *
     * <p>The reason is mandatory: a rejection without one tells the
     * faculty member nothing about what to correct, and leaves the audit
     * trail unable to explain why an official mark was refused.
     */
    @Transactional
    public AttendanceRecord reject(Long id, String reason) {
        AttendanceRecord record = require(id);
        assertTransitionAllowed(record, WorkflowStatus.REJECTED);
        assertRole(MAY_REVIEW, "reject a record");

        String trimmed = trimToNull(reason);
        if (trimmed == null) {
            throw new IllegalArgumentException(
                    "A rejection must say why, so that the faculty member knows what to correct");
        }

        return applyTransition(record, WorkflowStatus.REJECTED, trimmed, true);
    }

    // ---- Queries ----------------------------------------------------------

    /** Records awaiting a decision, for the review queue (FR-22). */
    @Transactional(readOnly = true)
    public List<AttendanceRecord> reviewQueue() {
        return records.findByWorkflowStatusOrderBySessionDateDesc(WorkflowStatus.SUBMITTED);
    }

    /** Whether the acting user may submit this record right now. */
    @Transactional(readOnly = true)
    public boolean maySubmit(AttendanceRecord record) {
        if (!record.getWorkflowStatus().canTransitionTo(WorkflowStatus.SUBMITTED)) {
            return false;
        }
        Role role = currentUser.role();
        if (role == null || !MAY_SUBMIT.contains(role)) {
            return false;
        }
        return role == Role.ADMIN || record.wasMarkedBy(currentUser.username());
    }

    /** Whether the acting user may decide this record right now. */
    @Transactional(readOnly = true)
    public boolean mayReview(AttendanceRecord record) {
        Role role = currentUser.role();
        return record.getWorkflowStatus() == WorkflowStatus.SUBMITTED
                && role != null && MAY_REVIEW.contains(role);
    }

    // ---- Internals --------------------------------------------------------

    private AttendanceRecord require(Long id) {
        return records.findById(id).orElseThrow(() -> new RecordNotFoundException(id));
    }

    private void assertTransitionAllowed(AttendanceRecord record, WorkflowStatus target) {
        WorkflowStatus from = record.getWorkflowStatus();
        if (!from.canTransitionTo(target)) {
            throw new IllegalTransitionException(record.getId(), from, target);
        }
    }

    private void assertRole(Set<Role> permitted, String action) {
        Role role = currentUser.role();
        if (role == null || !permitted.contains(role)) {
            throw new NotPermittedException("The "
                    + (role == null ? "current" : role.getLabel())
                    + " role may not " + action + ".");
        }
    }

    /**
     * A faculty member may only submit their own record; an administrator
     * may submit any. Expressed here rather than as a URL rule because no
     * URL pattern can say "the author of this particular record".
     */
    private void assertOwnership(AttendanceRecord record, String action) {
        if (currentUser.isAdmin()) {
            return;
        }
        if (!record.wasMarkedBy(currentUser.username())) {
            throw new NotPermittedException("Record " + record.getId() + " was entered by "
                    + record.getMarkedBy() + " and only they or an administrator may "
                    + action + " it.");
        }
    }

    private AttendanceRecord applyTransition(AttendanceRecord record, WorkflowStatus target,
                                             String comment, boolean isReviewDecision) {
        WorkflowStatus from = record.getWorkflowStatus();
        String actor = currentUser.username();

        record.setWorkflowStatus(target);
        if (isReviewDecision) {
            record.setReviewedBy(actor);
            record.setReviewedAt(Instant.now());
            record.setReviewComment(comment);
        }

        AttendanceRecord saved = records.save(record);
        log.info("Record {} moved {} -> {} by {}{}", saved.getId(), from, target, actor,
                comment == null ? "" : " (" + comment + ")");
        return saved;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
