package com.college.attendance.web;

import com.college.attendance.domain.Role;
import com.college.attendance.repository.AppUserRepository;
import com.college.attendance.repository.AttendanceRecordRepository;
import com.college.attendance.repository.StudentRepository;
import com.college.attendance.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * URL-level authorisation (US-10, FR-01, FR-02, NFR-05).
 *
 * <p>These assert the outer gate. Ownership rules - "the author of this
 * record, which a URL pattern cannot express" - are asserted in
 * {@code AttendanceServiceTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AttendanceSecurityTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private StudentRepository students;
    @Autowired
    private AttendanceRecordRepository records;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seed() {
        records.deleteAllInBatch();
        users.deleteAllInBatch();
        students.deleteAllInBatch();
        students.save(TestFixtures.student(TestFixtures.ROLL_1, "Aditya Rao"));
        users.save(TestFixtures.user("faculty1", Role.FACULTY, passwordEncoder.encode("Faculty@123")));
        users.save(TestFixtures.user("hod1", Role.HOD, passwordEncoder.encode("Hod@12345")));
        users.save(TestFixtures.studentUser("student1", passwordEncoder.encode("Student@123"),
                TestFixtures.ROLL_1));
    }

    @Test
    @WithAnonymousUser
    @DisplayName("FR-02: an unauthenticated request for a protected page goes to the login page")
    void anonymousRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/attendance"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login")));
    }

    @Test
    @WithAnonymousUser
    @DisplayName("FR-27: the health endpoint is reachable without a session")
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("the login page itself is public")
    void loginPageIsPublic() throws Exception {
        mockMvc.perform(get("/login")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "faculty1", roles = "FACULTY")
    @DisplayName("faculty may open the record-attendance form")
    void facultyMayOpenCreateForm() throws Exception {
        mockMvc.perform(get("/attendance/new")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    @DisplayName("FR-26: a student may not open the record-attendance form")
    void studentMayNotOpenCreateForm() throws Exception {
        mockMvc.perform(get("/attendance/new")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "hod1", roles = "HOD")
    @DisplayName("a HOD may not open the record-attendance form either")
    void hodMayNotOpenCreateForm() throws Exception {
        mockMvc.perform(get("/attendance/new")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "faculty1", roles = "FACULTY")
    @DisplayName("faculty may not reach the administrator's student roll")
    void facultyMayNotReachStudentRoll() throws Exception {
        mockMvc.perform(get("/students")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    @DisplayName("a student may read the attendance list")
    void studentMayReadList() throws Exception {
        mockMvc.perform(get("/attendance")).andExpect(status().isOk());
    }
}
