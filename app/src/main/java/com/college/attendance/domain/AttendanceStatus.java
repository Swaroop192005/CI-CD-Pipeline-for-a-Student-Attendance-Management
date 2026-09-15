package com.college.attendance.domain;

/**
 * How a student was marked for one class session.
 *
 * <p>The weighting below implements business rule BR-03: {@code EXCUSED}
 * (approved leave, for example a medical certificate) is excluded from
 * both the numerator and the denominator rather than counted as an
 * absence, which is what makes the published percentage defensible.
 */
public enum AttendanceStatus {

    PRESENT("Present", true, true),
    ABSENT("Absent", false, true),
    LATE("Late", true, true),
    EXCUSED("Excused", false, false);

    private final String label;
    private final boolean countsAsAttended;
    private final boolean countsTowardsTotal;

    AttendanceStatus(String label, boolean countsAsAttended, boolean countsTowardsTotal) {
        this.label = label;
        this.countsAsAttended = countsAsAttended;
        this.countsTowardsTotal = countsTowardsTotal;
    }

    public String getLabel() {
        return label;
    }

    /** Whether this status contributes to the numerator of the percentage. */
    public boolean countsAsAttended() {
        return countsAsAttended;
    }

    /** Whether this status contributes to the denominator of the percentage. */
    public boolean countsTowardsTotal() {
        return countsTowardsTotal;
    }
}
