package com.college.attendance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One teaching session that faculty has opened for students to mark
 * themselves present in.
 *
 * <p>This is the window that makes self-service marking safe. A student can
 * only mark attendance for a session that is open, so the act of marking is
 * bounded in time by the person actually teaching the class. When the
 * session closes, every enrolled student who did not mark is written as
 * absent, which is what makes a closed session a complete record rather
 * than a list of volunteers.
 *
 * <p>The uniqueness constraint mirrors BR-01 on the record itself: one
 * session per subject, date and period, so the same class cannot be opened
 * twice and collect two sets of marks.
 */
@Entity
@Table(name = "attendance_session",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_session_subject_date_period",
                columnNames = {"subject_code", "session_date", "period_number"}),
        indexes = {
                @Index(name = "idx_session_status", columnList = "status"),
                @Index(name = "idx_session_date", columnList = "session_date")
        })
public class AttendanceSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subject_code", nullable = false, length = 16)
    private String subjectCode;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "period_number", nullable = false)
    private int periodNumber;

    @Column(name = "status", nullable = false, length = 10)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private SessionStatus status = SessionStatus.OPEN;

    @Column(name = "opened_by", nullable = false, length = 64)
    private String openedBy;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_by", length = 64)
    private String closedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    /** Students who marked themselves present before the session closed. */
    @Column(name = "marked_count")
    private Integer markedCount;

    /** Students written as absent when the session closed. */
    @Column(name = "absent_count")
    private Integer absentCount;

    protected AttendanceSession() {
        // required by JPA
    }

    public AttendanceSession(String subjectCode, LocalDate sessionDate, int periodNumber,
                             String openedBy) {
        this.subjectCode = subjectCode;
        this.sessionDate = sessionDate;
        this.periodNumber = periodNumber;
        this.openedBy = openedBy;
        this.openedAt = Instant.now();
        this.status = SessionStatus.OPEN;
    }

    public boolean isOpen() {
        return status == SessionStatus.OPEN;
    }

    public Long getId() {
        return id;
    }

    public String getSubjectCode() {
        return subjectCode;
    }

    public LocalDate getSessionDate() {
        return sessionDate;
    }

    public int getPeriodNumber() {
        return periodNumber;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public String getOpenedBy() {
        return openedBy;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public String getClosedBy() {
        return closedBy;
    }

    public void setClosedBy(String closedBy) {
        this.closedBy = closedBy;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public Integer getMarkedCount() {
        return markedCount;
    }

    public void setMarkedCount(Integer markedCount) {
        this.markedCount = markedCount;
    }

    public Integer getAbsentCount() {
        return absentCount;
    }

    public void setAbsentCount(Integer absentCount) {
        this.absentCount = absentCount;
    }
}
