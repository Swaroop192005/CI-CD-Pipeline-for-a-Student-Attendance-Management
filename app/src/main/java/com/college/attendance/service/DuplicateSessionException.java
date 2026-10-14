package com.college.attendance.service;

import java.time.LocalDate;

/**
 * A session already exists for this (subject, date, period).
 *
 * <p>Distinct from {@link DuplicateRecordException}, which is about one
 * student's attendance. Reusing that one here produced "A record already
 * exists for - in CS501 ...": the dash stood where a roll number would go,
 * because a clashing session has no student to name. Faculty reading that
 * could not tell whether the problem was theirs, the student's, or a bug.
 */
public class DuplicateSessionException extends AttendanceException {

    public DuplicateSessionException(String subjectCode, LocalDate sessionDate,
                                      int periodNumber) {
        super("Attendance for " + subjectCode + " period " + periodNumber + " on "
              + sessionDate + " has already been taken. Open the existing session "
              + "from the list below, or pick another period.");
    }
}
