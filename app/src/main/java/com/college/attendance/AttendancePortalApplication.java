package com.college.attendance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the Student Attendance Management Portal.
 *
 * <p>The application is packaged as a WAR so that one artefact can be
 * deployed to an external Tomcat 10.1 or run standalone with
 * {@code java -jar attendance.war}.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AttendancePortalApplication {

    public static void main(String[] args) {
        SpringApplication.run(AttendancePortalApplication.class, args);
    }
}
