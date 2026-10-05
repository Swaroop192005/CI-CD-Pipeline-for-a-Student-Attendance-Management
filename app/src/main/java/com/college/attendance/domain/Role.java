package com.college.attendance.domain;

/**
 * Application roles. Stored without Spring Security's {@code ROLE_}
 * prefix; the prefix is added when authorities are built.
 */
public enum Role {

    /** Records attendance and submits it for review. */
    FACULTY("Faculty"),

    /** Approves or rejects submitted attendance. */
    HOD("Head of Department"),

    /** Full access, including the student roll. */
    ADMIN("Administrator"),

    /** Sees only their own approved attendance. */
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
