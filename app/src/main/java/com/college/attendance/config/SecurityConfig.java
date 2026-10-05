package com.college.attendance.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Authentication and URL-level authorisation (FR-01, FR-02, NFR-05).
 *
 * <p>These rules are the outer gate only. Every service that changes data
 * re-checks the acting user's role and ownership, because a URL rule
 * cannot express "the author of <em>this</em> record, or an admin".
 * Defence in depth is the point: neither layer is trusted alone.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Public: the health probe used by the pipeline and the
                        // Ansible playbook must not need a session (FR-27).
                        .requestMatchers("/actuator/health", "/actuator/health/**",
                                "/actuator/info", "/css/**", "/login", "/error").permitAll()

                        // Recording and correcting attendance.
                        .requestMatchers("/attendance/new").hasAnyRole("FACULTY", "ADMIN")
                        .requestMatchers("/attendance/*/edit").hasAnyRole("FACULTY", "ADMIN")
                        .requestMatchers("/attendance/*/submit").hasAnyRole("FACULTY", "ADMIN")

                        // Review decisions.
                        .requestMatchers("/attendance/*/approve", "/attendance/*/reject")
                            .hasAnyRole("HOD", "ADMIN")
                        .requestMatchers("/review").hasAnyRole("HOD", "ADMIN")

                        // Roll management.
                        .requestMatchers("/students/**").hasRole("ADMIN")

                        .anyRequest().authenticated())

                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/dashboard", true)
                        .failureUrl("/login?error")
                        .permitAll())

                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?loggedOut")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll())

                .exceptionHandling(ex -> ex.accessDeniedPage("/error/403"));

        return http.build();
    }

    /**
     * BCrypt, so that no plaintext password is ever stored (NFR-03).
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
