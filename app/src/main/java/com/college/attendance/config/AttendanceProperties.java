package com.college.attendance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised application settings (SRS NFR-08). Every field is
 * overridable by environment variable without rebuilding the artefact,
 * which is what Stage 8 parameterises and Stage 13 templates.
 *
 * @param environment          label shown in the UI banner, e.g. local / staging / production
 * @param eligibilityThreshold minimum attendance percentage for examination eligibility (BR-05)
 * @param shortageThreshold    attendance below which a student is escalated to the head of
 *                             department as a shortage case (BR-08). Lower than the
 *                             eligibility threshold on purpose: being short of eligibility
 *                             is common and visible on the dashboard, whereas a case is a
 *                             conversation and should be reserved for the extreme few
 * @param seedData             seed deterministic fixtures when the datastore is empty (FR-29)
 */
@ConfigurationProperties(prefix = "attendance")
public record AttendanceProperties(
        String environment,
        int eligibilityThreshold,
        int shortageThreshold,
        boolean seedData) {

    public AttendanceProperties {
        if (environment == null || environment.isBlank()) {
            environment = "local";
        }
        if (eligibilityThreshold <= 0 || eligibilityThreshold > 100) {
            eligibilityThreshold = 75;
        }
        if (shortageThreshold <= 0 || shortageThreshold > 100) {
            shortageThreshold = 35;
        }
        // A shortage case is meant to be rarer than falling short of
        // eligibility. If the two were ever configured the other way round,
        // every ineligible student would raise a case and the queue would be
        // exactly the thing this design set out to remove.
        if (shortageThreshold > eligibilityThreshold) {
            shortageThreshold = eligibilityThreshold;
        }
    }
}
