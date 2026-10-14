package com.college.attendance.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.management.Webauthn4JRelyingPartyOperations;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Set;

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

    private final String relyingPartyId;
    private final String relyingPartyName;
    private final String[] allowedOrigins;

    public SecurityConfig(
            @Value("${attendance.passkey.relying-party-id}") String relyingPartyId,
            @Value("${attendance.passkey.relying-party-name}") String relyingPartyName,
            @Value("${attendance.passkey.allowed-origins}") String[] allowedOrigins) {
        this.relyingPartyId = relyingPartyId;
        this.relyingPartyName = relyingPartyName;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Public: the health probe used by the pipeline and the
                        // Ansible playbook must not need a session (FR-27).
                        .requestMatchers("/actuator/health", "/actuator/health/**",
                                "/actuator/info", "/css/**", "/login", "/error").permitAll()

                        // The passkey ceremony happens before anyone is
                        // authenticated, so its script and its challenge
                        // endpoint have to be reachable from the login page.
                        .requestMatchers("/login/webauthn.js", "/webauthn/authenticate/options")
                            .permitAll()

                        // Recording and correcting attendance.
                        .requestMatchers("/attendance/new").hasAnyRole("FACULTY", "ADMIN")
                        .requestMatchers("/attendance/*/edit").hasAnyRole("FACULTY", "ADMIN")

                        // Taking a session. A student marks themselves, so
                        // /sessions/mark and /sessions/*/mark are theirs and
                        // everything else under /sessions belongs to faculty.
                        .requestMatchers("/sessions/mark").hasRole("STUDENT")
                        .requestMatchers("/sessions/*/mark").hasRole("STUDENT")
                        // Three path segments, so neither rule above covers
                        // it and it would otherwise fall through to
                        // anyRequest().authenticated() -- which would let
                        // faculty request a student's assertion challenge.
                        .requestMatchers("/sessions/*/mark/challenge").hasRole("STUDENT")
                        .requestMatchers("/sessions/open", "/sessions/*/close",
                                "/sessions/*/strike").hasAnyRole("FACULTY", "ADMIN")
                        .requestMatchers("/sessions", "/sessions/*")
                            .hasAnyRole("FACULTY", "HOD", "ADMIN")

                        // Shortage cases. Raising and deciding belong to the
                        // head of department; answering belongs to the student
                        // the case is about, which the service checks by roll
                        // number rather than by role alone.
                        .requestMatchers("/cases/raise", "/cases/*/accept",
                                "/cases/*/reject").hasAnyRole("HOD", "ADMIN")
                        .requestMatchers("/cases/*/respond").hasRole("STUDENT")

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

                // Passkeys. A credential is bound to an origin by the
                // browser, so one registered here cannot be replayed against
                // another site. localhost is the exception browsers make for
                // development, which is what lets the demonstration run
                // without a certificate.
                .webAuthn(webAuthn -> webAuthn
                        .rpId(relyingPartyId)
                        .rpName(relyingPartyName)
                        .allowedOrigins(allowedOrigins))

                .exceptionHandling(ex -> ex.accessDeniedPage("/error/403"));

        return http.build();
    }

    /**
     * Where a registered passkey lives.
     *
     * <p>Stored in the same H2 database as everything else, in the two
     * tables Spring Security defines. Only a public key is kept: the private
     * half never leaves the authenticator that made it, which is the
     * property that makes a passkey worth having over a password.
     */
    @Bean
    PublicKeyCredentialUserEntityRepository userEntityRepository(JdbcOperations jdbc) {
        return new JdbcPublicKeyCredentialUserEntityRepository(jdbc);
    }

    @Bean
    UserCredentialRepository userCredentialRepository(JdbcOperations jdbc) {
        return new JdbcUserCredentialRepository(jdbc);
    }

    /**
     * The relying party, declared as a bean rather than left to the
     * {@code webAuthn()} DSL to build privately.
     *
     * <p>The DSL looks for a bean of this type first and only constructs one
     * if none exists, so declaring it here replaces rather than duplicates
     * it. The reason to want it is that marking attendance needs to issue
     * and verify an assertion of its own, outside the login filter chain;
     * without a reachable bean that code would have to re-implement
     * verification, and a second implementation of a signature check is a
     * second chance to get it wrong.
     */
    @Bean
    WebAuthnRelyingPartyOperations webAuthnRelyingPartyOperations(
            PublicKeyCredentialUserEntityRepository userEntities,
            UserCredentialRepository userCredentials) {
        return new Webauthn4JRelyingPartyOperations(userEntities, userCredentials,
                PublicKeyCredentialRpEntity.builder()
                        .id(relyingPartyId)
                        .name(relyingPartyName)
                        .build(),
                Set.of(allowedOrigins));
    }

    /**
     * BCrypt, so that no plaintext password is ever stored (NFR-03).
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
