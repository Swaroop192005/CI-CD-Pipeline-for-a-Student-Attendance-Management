package com.college.attendance.dto;

import java.util.List;

/**
 * Everything the dashboard renders, computed once.
 *
 * @param totalRecords       records visible to the viewer
 * @param draftCount         awaiting submission
 * @param submittedCount     awaiting a decision
 * @param approvedCount      official
 * @param rejectedCount      sent back for correction
 * @param sessionsCounted    approved records that count towards the percentage (BR-03)
 * @param sessionsAttended   of those, the ones marked present or late
 * @param attendancePercent  overall percentage, from approved records only (BR-04)
 * @param eligibilityThreshold the configured pass mark (BR-05)
 * @param subjects           per-subject breakdown
 * @param atRisk             students below the threshold
 */
public record DashboardSummary(
        long totalRecords,
        long draftCount,
        long submittedCount,
        long approvedCount,
        long rejectedCount,
        long sessionsCounted,
        long sessionsAttended,
        double attendancePercent,
        int eligibilityThreshold,
        List<SubjectBreakdown> subjects,
        List<StudentStanding> atRisk) {

    /** Whether the viewer's own overall standing clears the threshold. */
    public boolean isMeetingThreshold() {
        return attendancePercent >= eligibilityThreshold;
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

    /** One student's standing, used for the at-risk list (US-20). */
    public record StudentStanding(
            String rollNumber,
            String fullName,
            long sessionsCounted,
            long sessionsAttended,
            double percent) {
    }

    /** An empty dashboard renders zeroes, never an error (FR-24). */
    public static DashboardSummary empty(int eligibilityThreshold) {
        return new DashboardSummary(0, 0, 0, 0, 0, 0, 0, 0.0,
                eligibilityThreshold, List.of(), List.of());
    }
}
