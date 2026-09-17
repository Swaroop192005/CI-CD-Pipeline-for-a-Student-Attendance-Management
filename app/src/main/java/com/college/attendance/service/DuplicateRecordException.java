package com.college.attendance.service;

import java.time.LocalDate;

/**
 * A record already exists for this (student, subject, date, period).
 * Enforces BR-01 / FR-05. Maps to HTTP 409.
 */
public class DuplicateRecordException extends AttendanceException {

    public DuplicateRecordException(String rollNumber, String subjectCode,
                                    LocalDate sessionDate, int periodNumber) {
        super("A record already exists for " + rollNumber + " in " + subjectCode
                + " on " + sessionDate + " period " + periodNumber);
    }
}
