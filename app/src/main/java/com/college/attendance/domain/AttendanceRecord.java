package com.college.attendance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One student's attendance for one subject, on one date, in one period.
 *
 * <p>Two things are deliberate here:
 *
 * <ul>
 *   <li>The uniqueness constraint over (student, subject, date, period)
 *       implements BR-01 in the database rather than only in a service
 *       check, so a race between two concurrent submissions cannot create
 *       the duplicate that the register used to suffer from.</li>
 *   <li>The audit fields are not nullable once set and are only written by
 *       the service layer. Workflow status in particular is changed only
 *       through {@code WorkflowService} (BR-07) — the setter exists for
 *       JPA and for that service, not for controllers.</li>
 * </ul>
 */
@Entity
@Table(
        name = "attendance_record",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_attendance_student_subject_date_period",
                columnNames = {"student_id", "subject_code", "session_date", "period_number"}),
        indexes = {
                @Index(name = "idx_attendance_session_date", columnList = "session_date"),
                @Index(name = "idx_attendance_workflow_status", columnList = "workflow_status"),
                @Index(name = "idx_attendance_subject_code", columnList = "subject_code")
        })
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "subject_code", nullable = false, length = 16)
    private String subjectCode;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "period_number", nullable = false)
    private int periodNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_status", nullable = false, length = 16)
    private AttendanceStatus attendanceStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_status", nullable = false, length = 16)
    private WorkflowStatus workflowStatus = WorkflowStatus.DRAFT;

    @Column(length = 500)
    private String remarks;

    // ---- Audit trail (FR-07, FR-12, FR-21) --------------------------------

    @Column(name = "marked_by", nullable = false, length = 64)
    private String markedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "reviewed_by", length = 64)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_comment", length = 500)
    private String reviewComment;

    protected AttendanceRecord() {
        // required by JPA
    }

    public AttendanceRecord(Student student, String subjectCode, LocalDate sessionDate,
                            int periodNumber, AttendanceStatus attendanceStatus,
                            String remarks, String markedBy) {
        this.student = student;
        this.subjectCode = subjectCode;
        this.sessionDate = sessionDate;
        this.periodNumber = periodNumber;
        this.attendanceStatus = attendanceStatus;
        this.remarks = remarks;
        this.markedBy = markedBy;
        this.workflowStatus = WorkflowStatus.DRAFT;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Student getStudent() {
        return student;
    }

    public void setStudent(Student student) {
        this.student = student;
    }

    public String getSubjectCode() {
        return subjectCode;
    }

    public void setSubjectCode(String subjectCode) {
        this.subjectCode = subjectCode;
    }

    public LocalDate getSessionDate() {
        return sessionDate;
    }

    public void setSessionDate(LocalDate sessionDate) {
        this.sessionDate = sessionDate;
    }

    public int getPeriodNumber() {
        return periodNumber;
    }

    public void setPeriodNumber(int periodNumber) {
        this.periodNumber = periodNumber;
    }

    public AttendanceStatus getAttendanceStatus() {
        return attendanceStatus;
    }

    public void setAttendanceStatus(AttendanceStatus attendanceStatus) {
        this.attendanceStatus = attendanceStatus;
    }

    public WorkflowStatus getWorkflowStatus() {
        return workflowStatus;
    }

    /** Only {@code WorkflowService} may call this (BR-07). */
    public void setWorkflowStatus(WorkflowStatus workflowStatus) {
        this.workflowStatus = workflowStatus;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public String getMarkedBy() {
        return markedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getReviewComment() {
        return reviewComment;
    }

    public void setReviewComment(String reviewComment) {
        this.reviewComment = reviewComment;
    }

    // ---- Domain queries ---------------------------------------------------

    /** Whether this record may still be changed by its author (FR-10/FR-11). */
    public boolean isEditable() {
        return workflowStatus.isEditable();
    }

    /** Whether {@code username} is the faculty member who entered this record. */
    public boolean wasMarkedBy(String username) {
        return markedBy != null && markedBy.equals(username);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AttendanceRecord record)) {
            return false;
        }
        return id != null && Objects.equals(id, record.id);
    }

    @Override
    public int hashCode() {
        return AttendanceRecord.class.hashCode();
    }

    @Override
    public String toString() {
        return "AttendanceRecord{id=" + id
                + ", student=" + (student == null ? null : student.getRollNumber())
                + ", subject=" + subjectCode
                + ", date=" + sessionDate
                + ", period=" + periodNumber
                + ", attendance=" + attendanceStatus
                + ", workflow=" + workflowStatus + "}";
    }
}
