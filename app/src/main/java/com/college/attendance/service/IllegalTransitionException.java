package com.college.attendance.service;

import com.college.attendance.domain.WorkflowStatus;

/**
 * The requested status change is not in the permitted set (FR-20).
 * Thrown before any write, so the record's status is unchanged.
 */
public class IllegalTransitionException extends AttendanceException {

    public IllegalTransitionException(Long id, WorkflowStatus from, WorkflowStatus to) {
        super("Record " + id + " cannot move from " + from.getLabel()
                + " to " + to.getLabel()
                + ". Permitted from " + from.getLabel() + ": "
                + (from.allowedTransitions().isEmpty()
                        ? "nothing - it is final"
                        : from.allowedTransitions().stream()
                                .map(WorkflowStatus::getLabel).sorted().toList()));
    }
}
