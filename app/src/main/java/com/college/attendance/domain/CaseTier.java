package com.college.attendance.domain;

/**
 * Who owns a case, and therefore which attendance band it belongs to.
 *
 * <p>Two tiers rather than one, because a single queue put every struggling
 * student in front of the head of department and that is what the shortage
 * threshold was introduced to stop. Splitting the band gives each role a
 * distinct job: faculty deal with students who are merely short of
 * eligibility, the head of department deals with the few who are in real
 * trouble.
 *
 * <p>The bands do not overlap, so a student is never the subject of two
 * simultaneous queries from two people who cannot see each other's.
 */
public enum CaseTier {

    /**
     * Raised by faculty, for a student below the eligibility threshold but
     * still above the shortage threshold. Faculty either close it themselves
     * or escalate it.
     */
    FACULTY("Faculty query"),

    /**
     * Owned by the head of department: either raised there because the
     * student is below the shortage threshold, or escalated from faculty.
     * Only this tier can excuse sessions.
     */
    HOD("Head of department");

    private final String label;

    CaseTier(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
