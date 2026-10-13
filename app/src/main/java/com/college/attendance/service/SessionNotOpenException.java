package com.college.attendance.service;

/** A student tried to mark attendance for a session that is not open. */
public class SessionNotOpenException extends AttendanceException {

    public SessionNotOpenException(String message) {
        super(message);
    }
}
