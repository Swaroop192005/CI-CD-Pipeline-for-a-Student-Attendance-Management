package com.college.attendance.config;

import com.college.attendance.domain.AppUser;
import com.college.attendance.domain.Role;
import com.college.attendance.repository.AppUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Answers "who is acting, and what may they see" from the security
 * context.
 *
 * <p>Services ask this rather than reading a request parameter, so a
 * student cannot widen their own scope by editing a URL: the roll number
 * their view is filtered by comes from their account, never from the
 * browser (FR-25).
 */
@Component
public class CurrentUser {

    private final AppUserRepository users;

    public CurrentUser(AppUserRepository users) {
        this.users = users;
    }

    /** The signed-in username, or {@code "system"} outside a request. */
    public String username() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "system";
        }
        return authentication.getName();
    }

    @Transactional(readOnly = true)
    public Optional<AppUser> account() {
        return users.findByUsername(username());
    }

    @Transactional(readOnly = true)
    public Role role() {
        return account().map(AppUser::getRole).orElse(null);
    }

    public boolean hasRole(Role role) {
        return role() == role;
    }

    public boolean isAdmin() {
        return hasRole(Role.ADMIN);
    }

    public boolean isStudent() {
        return hasRole(Role.STUDENT);
    }

    /**
     * For a student account, the roll number their view is restricted to.
     * Empty for every other role, which means "not restricted".
     *
     * <p>An empty result must only ever mean "this role is not scoped". A
     * student account with no linked roll number is a misconfiguration, not
     * an unscoped account, so it throws rather than returning empty: if the
     * two cases shared a return value, the one account that most needs
     * restricting would be the one that got unrestricted access.
     *
     * @throws MisconfiguredAccountException the account has the student role
     *                                       but no roll number to scope by
     */
    @Transactional(readOnly = true)
    public Optional<String> restrictedToRollNumber() {
        Optional<AppUser> student = account().filter(u -> u.getRole() == Role.STUDENT);
        if (student.isEmpty()) {
            return Optional.empty();
        }
        String rollNumber = student.get().getLinkedRollNumber();
        if (rollNumber == null || rollNumber.isBlank()) {
            throw new MisconfiguredAccountException(student.get().getUsername());
        }
        return Optional.of(rollNumber);
    }
}
