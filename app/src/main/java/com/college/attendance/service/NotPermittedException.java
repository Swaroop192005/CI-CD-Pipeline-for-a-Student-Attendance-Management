package com.college.attendance.service;

/**
 * The acting user's role or ownership does not permit this action
 * (FR-20). Maps to HTTP 403. Thrown before any write, so a refusal never
 * leaves a partial change behind.
 */
public class NotPermittedException extends AttendanceException {

    public NotPermittedException(String message) {
        super(message);
    }
}
