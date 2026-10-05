package com.college.attendance.service;

/** No attendance record with that identifier. Maps to HTTP 404. */
public class RecordNotFoundException extends AttendanceException {

    public RecordNotFoundException(Long id) {
        super("Attendance record not found: " + id);
    }
}
