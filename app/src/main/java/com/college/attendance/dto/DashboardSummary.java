package com.college.attendance.dto;

import java.util.List;

/**
 * Everything the dashboard renders, computed once.
 *
 * <p>There are no workflow counts any more. A record counts from the moment
 * a student marks themselves present or faculty closes the session, so
 * there is no such thing as a record that exists but does not yet count,
 * and nothing to tally per status.
 *
 * @param totalRecords         records visible to the viewer
 * @param sessionsCounted      records that count towards the percentage (BR-03)
 * @param sessionsAttended     of those, the ones marked present or late
 * @param attendancePercent    overall percentage
 * @param eligibilityThreshold the configured pass mark (BR-05)
 * @param shortageThreshold    the level below which a case is raised (BR-08)
 * @param subjects             per-subject breakdown
 * @param atRisk               students below the eligibility threshold
 * @param inShortage           students below the shortage threshold, a subset of atRisk
 * @param openCases            shortage cases awaiting somebody's attention
 */
public record DashboardSummary(
        long totalRecords,
        long sessionsCounted,
        long sessionsAttended,
        double attendancePercent,
        int eligibilityThreshold,
        int shortageThreshold,
        List<SubjectBreakdown> subjects,
        List<StudentStanding> atRisk,
        List<StudentStanding> inShortage,
        long openCases) {

    /** Whether the viewer's own overall standing clears the threshold. */
    public boolean isMeetingThreshold() {
        return attendancePercent >= eligibilityThreshold;
    }

    /** Whether the viewer is far enough below to be escalated (BR-08). */
    public boolean isInShortage() {
        return attendancePercent < shortageThreshold;
    }

    /** Per-subject figures (FR-23). */
    public record SubjectBreakdown(
            String subjectCode,
            long sessionsCounted,
            long sessionsAttended,
            double percent) {

        public boolean isBelowThreshold(int threshold) {
            return percent < threshold;
        }
    }

    /** One student's standing, used for the at-risk and shortage lists. */
    public record StudentStanding(
            String rollNumber,
            String fullName,
            long sessionsCounted,
            long sessionsAttended,
            double percent) {
    }

    /** An empty dashboard renders zeroes, never an error (FR-24). */
    public static DashboardSummary empty(int eligibilityThreshold, int shortageThreshold) {
        return new DashboardSummary(0, 0, 0, 0.0,
                eligibilityThreshold, shortageThreshold,
                List.of(), List.of(), List.of(), 0);
    }
}
