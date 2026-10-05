package com.college.attendance.support;

import com.college.attendance.domain.AppUser;
import com.college.attendance.domain.AttendanceStatus;
import com.college.attendance.domain.Role;
import com.college.attendance.domain.Student;
import com.college.attendance.dto.AttendanceForm;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;

/**
 * Fixture builders shared by the tests, so that each test states only the
 * one thing it is actually about.
 */
public final class TestFixtures {

    public static final String ROLL_1 = "1CS21CS001";
    public static final String ROLL_2 = "1CS21CS002";
    public static final String SUBJECT = "CS501";

    private TestFixtures() {
    }

    public static Student student(String rollNumber, String name) {
        return new Student(rollNumber, name, rollNumber.toLowerCase() + "@college.edu",
                "Computer Science", 5);
    }

    public static AppUser user(String username, Role role, String encodedPassword) {
        return new AppUser(username, encodedPassword, username + " display", role);
    }

    public static AppUser studentUser(String username, String encodedPassword, String rollNumber) {
        return new AppUser(username, encodedPassword, username + " display", Role.STUDENT, rollNumber);
    }

    public static AttendanceForm form(String rollNumber, String subject, LocalDate date, int period,
                                      AttendanceStatus status) {
        AttendanceForm form = new AttendanceForm();
        form.setRollNumber(rollNumber);
        form.setSubjectCode(subject);
        form.setSessionDate(date);
        form.setPeriodNumber(period);
        form.setAttendanceStatus(status);
        return form;
    }

    public static AttendanceForm validForm() {
        return form(ROLL_1, SUBJECT, LocalDate.now().minusDays(1), 1, AttendanceStatus.PRESENT);
    }

    /**
     * Switches the acting user inside a test.
     *
     * <p>Needed where a single test has to act as two different people -
     * for instance, one faculty member creating a record and another
     * trying to correct it. {@code @WithMockUser} sets one identity for
     * the whole test method and cannot express that.
     */
    public static void actAs(String username, Role role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "test-credentials",
                        List.of(new SimpleGrantedAuthority(role.authority()))));
    }
}
