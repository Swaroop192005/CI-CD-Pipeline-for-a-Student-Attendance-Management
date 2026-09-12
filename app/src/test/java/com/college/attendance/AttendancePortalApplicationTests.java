package com.college.attendance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test for the skeleton: proves the Spring context starts with the
 * real configuration, which is the earliest point at which a broken
 * dependency or malformed configuration file is caught by CI.
 */
@SpringBootTest
@ActiveProfiles("test")
class AttendancePortalApplicationTests {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("application context loads with the production configuration")
    void contextLoads() {
        assertThat(context).isNotNull();
        assertThat(context.getBeanNamesForType(
                com.college.attendance.config.AttendanceProperties.class)).isNotEmpty();
    }
}
