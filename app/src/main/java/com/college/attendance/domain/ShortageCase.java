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

import java.time.Instant;
import java.time.LocalDate;

/**
 * A query raised against one student whose attendance has fallen below the
 * shortage threshold.
 *
 * <p>This is what replaced per-session approval. Marking attendance is now
 * a faculty act that counts immediately; the head of department's attention
 * is spent on the few students in difficulty rather than on every record.
 *
 * <p>The percentage is stored as it stood when the case was raised. The
 * student keeps attending classes while the case is open, so recomputing it
 * later would silently change what the head of department was looking at
 * when they asked the question.
 */
@Entity
@Table(name = "shortage_case",
        indexes = {
                @Index(name = "idx_case_status", columnList = "status"),
                @Index(name = "idx_case_student", columnList = "student_id")
        })
public class ShortageCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CaseStatus status = CaseStatus.AWAITING_RESPONSE;

    /** Overall attendance when the case was raised, not as it stands now. */
    @Column(name = "percent_at_open", nullable = false)
    private double percentAtOpen;

    @Column(name = "threshold_at_open", nullable = false)
    private int thresholdAtOpen;

    // ---- the conversation -------------------------------------------------

    @Column(name = "question", nullable = false, length = 1000)
    private String question;

    @Column(name = "opened_by", nullable = false, length = 64)
    private String openedBy;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "response", length = 2000)
    private String response;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "decision_note", length = 1000)
    private String decisionNote;

    @Column(name = "decided_by", length = 64)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    /** How many sessions were marked EXCUSED when the case was accepted. */
    @Column(name = "excused_count")
    private Integer excusedCount;

    @Column(name = "excused_from")
    private LocalDate excusedFrom;

    @Column(name = "excused_to")
    private LocalDate excusedTo;

    protected ShortageCase() {
        // required by JPA
    }

    public ShortageCase(Student student, double percentAtOpen, int thresholdAtOpen,
                        String question, String openedBy) {
        this.student = student;
        this.percentAtOpen = percentAtOpen;
        this.thresholdAtOpen = thresholdAtOpen;
        this.question = question;
        this.openedBy = openedBy;
        this.openedAt = Instant.now();
        this.status = CaseStatus.AWAITING_RESPONSE;
    }

    public Long getId() {
        return id;
    }

    public Student getStudent() {
        return student;
    }

    public CaseStatus getStatus() {
        return status;
    }

    public void setStatus(CaseStatus status) {
        this.status = status;
    }

    public double getPercentAtOpen() {
        return percentAtOpen;
    }

    public int getThresholdAtOpen() {
        return thresholdAtOpen;
    }

    public String getQuestion() {
        return question;
    }

    public String getOpenedBy() {
        return openedBy;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(Instant respondedAt) {
        this.respondedAt = respondedAt;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public void setDecisionNote(String decisionNote) {
        this.decisionNote = decisionNote;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(String decidedBy) {
        this.decidedBy = decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    public Integer getExcusedCount() {
        return excusedCount;
    }

    public void setExcusedCount(Integer excusedCount) {
        this.excusedCount = excusedCount;
    }

    public LocalDate getExcusedFrom() {
        return excusedFrom;
    }

    public void setExcusedFrom(LocalDate excusedFrom) {
        this.excusedFrom = excusedFrom;
    }

    public LocalDate getExcusedTo() {
        return excusedTo;
    }

    public void setExcusedTo(LocalDate excusedTo) {
        this.excusedTo = excusedTo;
    }
}
