package com.college.attendance.service;

/** A student already has a case in flight (BR-13). */
public class DuplicateCaseException extends AttendanceException {

    public DuplicateCaseException(String rollNumber, Long existingId) {
        super(rollNumber + " already has an open case (#" + existingId
              + "). Decide that one before raising another.");
    }
}
