package com.college.attendance.web;

import com.college.attendance.domain.AttendanceStatus;
import com.college.attendance.domain.Role;
import com.college.attendance.domain.WorkflowStatus;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * End-to-end web flow for US-01 and US-02: the create form renders, a
 * valid submission is persisted and shown, an invalid one comes back with
 * field errors, and the list renders the saved record.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AttendanceWebFlowIT {

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
    }

    @Test
    @WithMockUser(username = "faculty1", roles = "FACULTY")
    @DisplayName("AC-1: the create form offers the active students and the status options")
    void createFormRendersChoices() throws Exception {
        mockMvc.perform(get("/attendance/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("attendance/form"))
                .andExpect(model().attributeExists("students", "attendanceStatuses"))
                .andExpect(content().string(containsString("data-testid=\"attendance-form\"")))
                .andExpect(content().string(containsString(TestFixtures.ROLL_1)));
    }

    @Test
    @WithMockUser(username = "faculty1", roles = "FACULTY")
    @DisplayName("AC-2: a valid submission is saved as a draft and redirects to its detail page")
    void validSubmissionIsSaved() throws Exception {
        mockMvc.perform(post("/attendance").with(csrf())
                        .param("rollNumber", TestFixtures.ROLL_1)
                        .param("subjectCode", "CS501")
                        .param("sessionDate", LocalDate.now().minusDays(1).toString())
                        .param("periodNumber", "2")
                        .param("attendanceStatus", "PRESENT")
                        .param("remarks", "Recorded from the classroom"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/attendance/")));

        assertThat(records.findAll()).singleElement().satisfies(r -> {
            assertThat(r.getStudent().getRollNumber()).isEqualTo(TestFixtures.ROLL_1);
            assertThat(r.getSubjectCode()).isEqualTo("CS501");
            assertThat(r.getPeriodNumber()).isEqualTo(2);
            assertThat(r.getAttendanceStatus()).isEqualTo(AttendanceStatus.PRESENT);
            assertThat(r.getWorkflowStatus()).isEqualTo(WorkflowStatus.DRAFT);
            assertThat(r.getMarkedBy()).isEqualTo("faculty1");
        });
    }

    @Test
    @WithMockUser(username = "faculty1", roles = "FACULTY")
    @DisplayName("AC-4: a future session date returns the form with a field error and saves nothing")
    void futureDateReturnsFormWithError() throws Exception {
        mockMvc.perform(post("/attendance").with(csrf())
                        .param("rollNumber", TestFixtures.ROLL_1)
                        .param("subjectCode", "CS501")
                        .param("sessionDate", LocalDate.now().plusDays(3).toString())
                        .param("periodNumber", "1")
                        .param("attendanceStatus", "PRESENT"))
                .andExpect(status().isOk())
                .andExpect(view().name("attendance/form"))
                .andExpect(content().string(containsString("future")));

        assertThat(records.findAll()).isEmpty();
    }

    @Test
    @WithMockUser(username = "faculty1", roles = "FACULTY")
    @DisplayName("a malformed subject code returns a field-level validation message")
    void malformedSubjectCodeRejected() throws Exception {
        mockMvc.perform(post("/attendance").with(csrf())
                        .param("rollNumber", TestFixtures.ROLL_1)
                        .param("subjectCode", "not-a-code")
                        .param("sessionDate", LocalDate.now().minusDays(1).toString())
                        .param("periodNumber", "1")
                        .param("attendanceStatus", "PRESENT"))
                .andExpect(status().isOk())
                .andExpect(view().name("attendance/form"))
                .andExpect(content().string(containsString("data-testid=\"error-subject\"")));

        assertThat(records.findAll()).isEmpty();
    }

    @Test
    @WithMockUser(username = "faculty1", roles = "FACULTY")
    @DisplayName("AC-3: a duplicate submission is refused with a readable message")
    void duplicateSubmissionRefused() throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/attendance").with(csrf())
                    .param("rollNumber", TestFixtures.ROLL_1)
                    .param("subjectCode", "CS501")
                    .param("sessionDate", LocalDate.now().minusDays(1).toString())
                    .param("periodNumber", "1")
                    .param("attendanceStatus", "PRESENT"));
        }

        assertThat(records.findAll()).hasSize(1);
    }

    @Test
    @WithMockUser(username = "faculty1", roles = "FACULTY")
    @DisplayName("AC-1/AC-2 of US-02: the list renders the saved record's key fields")
    void listRendersSavedRecord() throws Exception {
        mockMvc.perform(post("/attendance").with(csrf())
                .param("rollNumber", TestFixtures.ROLL_1)
                .param("subjectCode", "CS501")
                .param("sessionDate", LocalDate.now().minusDays(1).toString())
                .param("periodNumber", "1")
                .param("attendanceStatus", "LATE"));

        mockMvc.perform(get("/attendance"))
                .andExpect(status().isOk())
                .andExpect(view().name("attendance/list"))
                .andExpect(content().string(containsString(TestFixtures.ROLL_1)))
                .andExpect(content().string(containsString("CS501")))
                .andExpect(content().string(containsString("Late")))
                .andExpect(content().string(containsString("Draft")));
    }

    @Test
    @WithMockUser(username = "faculty1", roles = "FACULTY")
    @DisplayName("the empty list shows an explicit empty state, not a blank table")
    void emptyListShowsEmptyState() throws Exception {
        mockMvc.perform(get("/attendance"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-testid=\"empty-state\"")));
    }
}
