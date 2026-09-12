package com.college.attendance;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Bootstraps the application when the WAR is deployed to an external
 * servlet container (Stage 8 deploys it to Tomcat 10.1).
 */
public class ServletInitializer extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(AttendancePortalApplication.class);
    }
}
