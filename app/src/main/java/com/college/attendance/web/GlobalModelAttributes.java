package com.college.attendance.web;

import com.college.attendance.config.AttendanceProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Supplies the model attributes the shared layout needs on every page, so
 * that no individual controller has to remember to add them.
 *
 * <p>The environment label is what Stage 8 parameterises from the Jenkins
 * pipeline: it is rendered in the top bar and asserted by the deployment
 * verification test, which makes a misconfigured deploy visible at a glance
 * rather than silent.
 */
@ControllerAdvice
public class GlobalModelAttributes {

    private final AttendanceProperties properties;

    public GlobalModelAttributes(AttendanceProperties properties) {
        this.properties = properties;
    }

    @ModelAttribute("appEnvironment")
    public String appEnvironment() {
        return properties.environment();
    }

    @ModelAttribute("eligibilityThreshold")
    public int eligibilityThreshold() {
        return properties.eligibilityThreshold();
    }

    @ModelAttribute("contextPath")
    public String contextPath(HttpServletRequest request) {
        String path = request.getContextPath();
        return path == null || path.isEmpty() ? "/" : path;
    }

    /**
     * The signed-in user's role, without Spring Security's {@code ROLE_}
     * prefix, for display in the top bar.
     *
     * <p>Resolved here rather than with an expression in the template: a
     * principal can legitimately carry no authorities, and an index-based
     * expression in the layout would then fail to render <em>every</em>
     * page rather than just omitting one label.
     */
    @ModelAttribute("currentRole")
    public String currentRole(Authentication authentication) {
        if (authentication == null) {
            return "";
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .map(a -> a.startsWith("ROLE_") ? a.substring("ROLE_".length()) : a)
                .orElse("");
    }
}
