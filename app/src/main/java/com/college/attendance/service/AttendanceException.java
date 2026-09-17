package com.college.attendance.service;

/**
 * Base type for the domain's refusals, so that the web and API layers can
 * translate them into the right status code without catching everything.
 */
public abstract class AttendanceException extends RuntimeException {

    protected AttendanceException(String message) {
        super(message);
    }
}
