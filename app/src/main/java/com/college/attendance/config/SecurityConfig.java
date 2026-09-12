package com.college.attendance.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Baseline filter chain for the skeleton: the health endpoint is public and
 * everything else requires an authenticated session.
 *
 * <p>Role-based authorisation rules and the seeded user store arrive with
 * the authentication feature (US-10); this class is the placeholder that
 * keeps the skeleton bootable and the health probe reachable.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info", "/css/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form.permitAll());
        return http.build();
    }
}
