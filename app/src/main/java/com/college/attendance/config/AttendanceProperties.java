package com.college.attendance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised application settings (SRS NFR-08). Every field is
 * overridable by environment variable without rebuilding the artefact,
 * which is what Stage 8 parameterises and Stage 13 templates.
 *
 * @param environment          label shown in the UI banner, e.g. local / staging / production
 * @param eligibilityThreshold minimum attendance percentage for examination eligibility (BR-05)
 * @param seedData             seed deterministic fixtures when the datastore is empty (FR-29)
 */
@ConfigurationProperties(prefix = "attendance")
public record AttendanceProperties(
        String environment,
        int eligibilityThreshold,
        boolean seedData) {

    public AttendanceProperties {
        if (environment == null || environment.isBlank()) {
            environment = "local";
        }
        if (eligibilityThreshold <= 0 || eligibilityThreshold > 100) {
            eligibilityThreshold = 75;
        }
    }
}
