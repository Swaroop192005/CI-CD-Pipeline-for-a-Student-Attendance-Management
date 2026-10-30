package com.college.attendance.domain;

/**
 * Application roles. Stored without Spring Security's {@code ROLE_}
 * prefix; the prefix is added when authorities are built.
 */
public enum Role {

    /**
     * Opens and closes attendance sessions, corrects records, and raises
     * queries about students below the eligibility threshold but above the
     * shortage one.
     */
    FACULTY("Faculty"),

    /**
     * Owns students below the shortage threshold and decides cases faculty
     * escalate. The only role that can excuse sessions, because excusing
     * changes the figure the eligibility decision rests on.
     */
    HOD("Head of Department"),

    /** Full access, including the student roll. */
    ADMIN("Administrator"),

    /** Marks their own attendance, and answers cases raised about them. */
    STUDENT("Student");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public String authority() {
        return "ROLE_" + name();
    }
}
