package com.college.attendance.service;

import com.college.attendance.domain.WorkflowStatus;

/**
 * The record has passed out of an editable state (FR-11). Maps to HTTP 409.
 */
public class RecordLockedException extends AttendanceException {

    public RecordLockedException(Long id, WorkflowStatus status) {
        super("Record " + id + " cannot be edited while it is " + status.getLabel()
                + ". Only draft and rejected records are editable.");
    }
}
