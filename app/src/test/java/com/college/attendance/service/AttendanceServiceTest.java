package com.college.attendance.service;

import com.college.attendance.domain.AttendanceRecord;
import com.college.attendance.domain.AttendanceStatus;
import com.college.attendance.domain.Role;
import com.college.attendance.domain.WorkflowStatus;
import com.college.attendance.dto.AttendanceForm;
import com.college.attendance.repository.AppUserRepository;
import com.college.attendance.repository.AttendanceRecordRepository;
import com.college.attendance.repository.StudentRepository;
import com.college.attendance.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static com.college.attendance.support.TestFixtures.ROLL_1;
import static com.college.attendance.support.TestFixtures.SUBJECT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the acceptance criteria of US-01 (record attendance), US-03
 * (correct a record) and US-07 (attribution on every change).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AttendanceServiceTest {

    @Autowired
    private AttendanceService attendanceService;
    @Autowired
    private AttendanceRecordRepository records;
    @Autowired
    private StudentRepository students;
    @Autowired
    private AppUserRepository users;

    @BeforeEach
    void seedRoll() {
        // deleteAllInBatch issues the DELETE immediately. The queued form
        // (deleteAll) would be flushed after the inserts below, because
        // Hibernate orders inserts ahead of deletes within one transaction,
        // and the re-inserted roll numbers would then collide.
        records.deleteAllInBatch();
        students.deleteAllInBatch();
        users.deleteAllInBatch();
        students.save(TestFixtures.student(ROLL_1, "Aditya Rao"));
        students.save(TestFixtures.student(TestFixtures.ROLL_2, "Bhavana Shetty"));
        users.save(TestFixtures.user("faculty1", Role.FACULTY, "{noop}x"));
        users.save(TestFixtures.user("faculty2", Role.FACULTY, "{noop}x"));
        users.save(TestFixtures.user("admin1", Role.ADMIN, "{noop}x"));
    }

    @Nested
    @DisplayName("Creating a record")
    class Creating {

        @Test
        @WithMockUser(username = "faculty1", roles = "FACULTY")
        @DisplayName("AC-2: a new record starts in DRAFT and is retrievable")
        void createStartsInDraft() {
            AttendanceRecord saved = attendanceService.create(TestFixtures.validForm());

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getWorkflowStatus()).isEqualTo(WorkflowStatus.DRAFT);
            assertThat(attendanceService.require(saved.getId()).getSubjectCode()).isEqualTo(SUBJECT);
        }

        @Test
        @WithMockUser(username = "faculty1", roles = "FACULTY")
        @DisplayName("AC-5: creation stores the acting user and the creation time")
        void recordsActorAndTimestamp() {
            AttendanceRecord saved = attendanceService.create(TestFixtures.validForm());

            assertThat(saved.getMarkedBy()).isEqualTo("faculty1");
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getUpdatedAt()).isNull();
        }

        @Test
        @WithMockUser(username = "faculty1", roles = "FACULTY")
        @DisplayName("AC-3: a duplicate student/subject/date/period is refused (BR-01)")
        void rejectsDuplicate() {
            attendanceService.create(TestFixtures.validForm());

            assertThatThrownBy(() -> attendanceService.create(TestFixtures.validForm()))
                    .isInstanceOf(DuplicateRecordException.class)
                    .hasMessageContaining(ROLL_1)
                    .hasMessageContaining(SUBJECT);
        }

        @Test
        @WithMockUser(username = "faculty1", roles = "FACULTY")
        @DisplayName("the same student in a different period is not a duplicate")
        void differentPeriodIsNotDuplicate() {
            attendanceService.create(TestFixtures.validForm());

            AttendanceForm otherPeriod = TestFixtures.validForm();
            otherPeriod.setPeriodNumber(2);

            assertThat(attendanceService.create(otherPeriod).getId()).isNotNull();
        }

        @Test
        @WithMockUser(username = "faculty1", roles = "FACULTY")
        @DisplayName("AC-4: a session date in the future is refused (FR-06)")
        void futureDateRejected() {
            AttendanceForm form = TestFixtures.validForm();
            form.setSessionDate(LocalDate.now().plusDays(1));

            assertThatThrownBy(() -> attendanceService.create(form))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("future");
        }

        @Test
        @WithMockUser(username = "faculty1", roles = "FACULTY")
        @DisplayName("a roll number that is not on the roll is refused")
        void unknownStudentRejected() {
            AttendanceForm form = TestFixtures.validForm();
            form.setRollNumber("9XX99XX999");

            assertThatThrownBy(() -> attendanceService.create(form))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No student on the roll");
        }
    }

    @Nested
    @DisplayName("Correcting a record")
    class Correcting {

        @Test
        @WithMockUser(username = "faculty1", roles = "FACULTY")
        @DisplayName("AC-1: a DRAFT record is editable by its author")
        void draftIsEditable() {
            AttendanceRecord saved = attendanceService.create(TestFixtures.validForm());

            AttendanceForm correction = TestFixtures.validForm();
            correction.setAttendanceStatus(AttendanceStatus.ABSENT);
            correction.setRemarks("Corrected: student was not in class");

            AttendanceRecord updated = attendanceService.update(saved.getId(), correction);

            assertThat(updated.getAttendanceStatus()).isEqualTo(AttendanceStatus.ABSENT);
            assertThat(updated.getRemarks()).isEqualTo("Corrected: student was not in class");
        }

        @Test
        @WithMockUser(username = "faculty1", roles = "FACULTY")
        @DisplayName("AC-3: a correction stamps updatedAt but keeps the original author")
        void updatePreservesAuthor() {
            AttendanceRecord saved = attendanceService.create(TestFixtures.validForm());

            AttendanceForm correction = TestFixtures.validForm();
            correction.setAttendanceStatus(AttendanceStatus.LATE);
            AttendanceRecord updated = attendanceService.update(saved.getId(), correction);

            assertThat(updated.getUpdatedAt()).isNotNull();
            assertThat(updated.getMarkedBy()).isEqualTo("faculty1");
        }

        @Test
        @WithMockUser(username = "faculty1", roles = "FACULTY")
        @DisplayName("AC-2: a SUBMITTED record is locked against correction (FR-11)")
        void submittedIsLocked() {
            AttendanceRecord saved = attendanceService.create(TestFixtures.validForm());
            saved.setWorkflowStatus(WorkflowStatus.SUBMITTED);
            records.save(saved);

            assertThatThrownBy(() -> attendanceService.update(saved.getId(), TestFixtures.validForm()))
                    .isInstanceOf(RecordLockedException.class)
                    .hasMessageContaining("Submitted");
        }

        @Test
        @WithMockUser(username = "faculty1", roles = "FACULTY")
        @DisplayName("an APPROVED record is locked against correction")
        void approvedIsLocked() {
            AttendanceRecord saved = attendanceService.create(TestFixtures.validForm());
            saved.setWorkflowStatus(WorkflowStatus.APPROVED);
            records.save(saved);

            assertThatThrownBy(() -> attendanceService.update(saved.getId(), TestFixtures.validForm()))
                    .isInstanceOf(RecordLockedException.class);
        }

        @Test
        @DisplayName("AC-4: one faculty member cannot correct another's record")
        void cannotEditOthersRecord() {
            Long id = asFaculty1CreateRecord();
            TestFixtures.actAs("faculty2", Role.FACULTY);

            assertThatThrownBy(() -> attendanceService.update(id, TestFixtures.validForm()))
                    .isInstanceOf(NotPermittedException.class)
                    .hasMessageContaining("faculty1");
        }

        @Test
        @DisplayName("an administrator may correct any faculty member's record")
        void adminMayEditAnyRecord() {
            Long id = asFaculty1CreateRecord();
            TestFixtures.actAs("admin1", Role.ADMIN);

            AttendanceForm correction = TestFixtures.validForm();
            correction.setAttendanceStatus(AttendanceStatus.EXCUSED);

            assertThat(attendanceService.update(id, correction).getAttendanceStatus())
                    .isEqualTo(AttendanceStatus.EXCUSED);
        }

        private Long asFaculty1CreateRecord() {
            TestFixtures.actAs("faculty1", Role.FACULTY);
            return attendanceService.create(TestFixtures.validForm()).getId();
        }
    }

    @Test
    @WithMockUser(username = "faculty1", roles = "FACULTY")
    @DisplayName("requesting an unknown record identifier is a not-found refusal")
    void unknownIdIsNotFound() {
        assertThatThrownBy(() -> attendanceService.require(999_999L))
                .isInstanceOf(RecordNotFoundException.class)
                .hasMessageContaining("999999");
    }
}
