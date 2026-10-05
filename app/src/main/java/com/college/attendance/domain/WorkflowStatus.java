package com.college.attendance.domain;

import java.util.Set;

/**
 * Review state of an attendance record.
 *
 * <p>The permitted transitions are declared here and enforced by
 * {@code WorkflowService}; nothing else may change a record's status
 * (business rule BR-07). Keeping the state machine on the enum means an
 * illegal transition is a question the domain can answer, rather than a
 * rule scattered across controllers.
 */
public enum WorkflowStatus {

    /** Entered but not yet sent for review. Editable by its author. */
    DRAFT("Draft"),

    /** Sent for review. Locked for the author, awaiting a HOD decision. */
    SUBMITTED("Submitted"),

    /** Official. Visible to the student and counted in percentages. */
    APPROVED("Approved"),

    /** Sent back with a reason. Editable by its author and re-submittable. */
    REJECTED("Rejected");

    private final String label;

    WorkflowStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Whether a record in this state may still be edited by its author. */
    public boolean isEditable() {
        return this == DRAFT || this == REJECTED;
    }

    /** Whether a record in this state is official and publishable. */
    public boolean isOfficial() {
        return this == APPROVED;
    }

    /** The states this one may legally move to. */
    public Set<WorkflowStatus> allowedTransitions() {
        return switch (this) {
            case DRAFT -> Set.of(SUBMITTED);
            case SUBMITTED -> Set.of(APPROVED, REJECTED);
            case REJECTED -> Set.of(SUBMITTED);
            case APPROVED -> Set.of();
        };
    }

    public boolean canTransitionTo(WorkflowStatus target) {
        return allowedTransitions().contains(target);
    }
}
