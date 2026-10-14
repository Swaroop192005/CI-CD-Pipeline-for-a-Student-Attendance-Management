package com.college.attendance.dto;

import java.time.LocalDate;

/**
 * Attendance totalled over one period of time -- a week or a calendar month.
 *
 * @param label      how the period is named to a reader, e.g. "Week 42" or "October 2026"
 * @param from       first day of the period that has a record
 * @param to         last day of the period that has a record
 * @param attended   sessions counted as attended (BR-03: {@code EXCUSED} is not one)
 * @param counted    sessions counted towards the total (BR-03: {@code EXCUSED} is excluded
 *                   from this too, so an excused absence neither helps nor harms)
 * @param excused    sessions excused, reported separately so the figure is explainable
 */
public record AttendanceBucket(String label, LocalDate from, LocalDate to,
                                long attended, long counted, long excused) {

    /**
     * Attendance as a whole-number percentage, or {@code -1} when nothing
     * counted.
     *
     * <p>Minus one rather than zero: a week in which every session was
     * excused has no percentage at all, and showing it as 0% would read as
     * "attended nothing", which is the opposite of what the records say.
     * Callers render the difference.
     */
    public int percent() {
        return counted == 0 ? -1 : (int) Math.round(attended * 100.0 / counted);
    }

    public boolean hasPercent() {
        return counted > 0;
    }
}
