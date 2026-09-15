package com.college.attendance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * An account that can sign in.
 *
 * <p>A student account carries the roll number it speaks for, which is how
 * the self-service view is scoped to one student's own records (FR-25)
 * without the controller having to trust anything the browser sends.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    /** BCrypt hash. A plaintext password is never stored (NFR-03). */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    /** Set only for STUDENT accounts; scopes their view to their own roll. */
    @Column(name = "linked_roll_number", length = 32)
    private String linkedRollNumber;

    @Column(nullable = false)
    private boolean enabled = true;

    protected AppUser() {
        // required by JPA
    }

    public AppUser(String username, String passwordHash, String displayName, Role role) {
        this(username, passwordHash, displayName, role, null);
    }

    public AppUser(String username, String passwordHash, String displayName, Role role,
                   String linkedRollNumber) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
        this.linkedRollNumber = linkedRollNumber;
        this.enabled = true;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getLinkedRollNumber() {
        return linkedRollNumber;
    }

    public void setLinkedRollNumber(String linkedRollNumber) {
        this.linkedRollNumber = linkedRollNumber;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AppUser user)) {
            return false;
        }
        return id != null && Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return AppUser.class.hashCode();
    }

    @Override
    public String toString() {
        return "AppUser{" + username + ", " + role + "}";
    }
}
