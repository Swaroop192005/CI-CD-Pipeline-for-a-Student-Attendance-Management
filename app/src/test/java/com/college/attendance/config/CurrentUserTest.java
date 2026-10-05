package com.college.attendance.config;

import com.college.attendance.domain.AppUser;
import com.college.attendance.domain.Role;
import com.college.attendance.repository.AppUserRepository;
import com.college.attendance.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Scoping rules for the acting user.
 *
 * <p>The case that matters most here is the misconfigured student
 * account. An earlier version returned an empty Optional for it, which
 * callers read as "this role is not scoped" - so the one account that
 * most needed restricting was the one that got unrestricted access.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CurrentUserTest {

    @Autowired
    private CurrentUser currentUser;
    @Autowired
    private AppUserRepository users;

    @BeforeEach
    void reset() {
        users.deleteAllInBatch();
    }

    @Test
    @DisplayName("a student account is scoped to its own roll number")
    void studentIsScopedToOwnRoll() {
        users.save(TestFixtures.studentUser("student1", "{noop}x", TestFixtures.ROLL_1));
        TestFixtures.actAs("student1", Role.STUDENT);

        assertThat(currentUser.restrictedToRollNumber()).contains(TestFixtures.ROLL_1);
    }

    @Test
    @DisplayName("a faculty account is not scoped")
    void facultyIsNotScoped() {
        users.save(TestFixtures.user("faculty1", Role.FACULTY, "{noop}x"));
        TestFixtures.actAs("faculty1", Role.FACULTY);

        assertThat(currentUser.restrictedToRollNumber()).isEmpty();
    }

    @Test
    @DisplayName("an admin account is not scoped")
    void adminIsNotScoped() {
        users.save(TestFixtures.user("admin1", Role.ADMIN, "{noop}x"));
        TestFixtures.actAs("admin1", Role.ADMIN);

        assertThat(currentUser.restrictedToRollNumber()).isEmpty();
        assertThat(currentUser.isAdmin()).isTrue();
    }

    @Test
    @DisplayName("a student account with no roll number fails loudly instead of failing open")
    void studentWithoutRollNumberIsRefused() {
        users.save(new AppUser("orphan", "{noop}x", "Orphaned student account",
                Role.STUDENT, null));
        TestFixtures.actAs("orphan", Role.STUDENT);

        assertThatThrownBy(() -> currentUser.restrictedToRollNumber())
                .isInstanceOf(MisconfiguredAccountException.class)
                .hasMessageContaining("orphan")
                .hasMessageContaining("no linked roll number");
    }

    @Test
    @DisplayName("a blank roll number is treated the same as a missing one")
    void blankRollNumberIsRefused() {
        users.save(new AppUser("blank", "{noop}x", "Blank roll account",
                Role.STUDENT, "   "));
        TestFixtures.actAs("blank", Role.STUDENT);

        assertThatThrownBy(() -> currentUser.restrictedToRollNumber())
                .isInstanceOf(MisconfiguredAccountException.class);
    }
}
