package com.college.attendance.service;

import com.college.attendance.domain.AttendanceRecord;
import com.college.attendance.domain.Role;
import com.college.attendance.domain.WorkflowStatus;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The role-based status workflow (US-05, US-06, US-07; FR-16 to FR-22).
 *
 * <p>Every refusal test also asserts that the record's status is
 * unchanged. A rule that refuses but still writes would satisfy a
 * naive assertion while leaving the audit trail wrong, which is the
 * failure mode this workflow exists to prevent (FR-20).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WorkflowServiceTest {

    @Autowired
    private WorkflowService workflowService;
    @Autowired
    private AttendanceService attendanceService;
    @Autowired
    private AttendanceRecordRepository records;
    @Autowired
    private StudentRepository students;
    @Autowired
    private AppUserRepository users;

    @BeforeEach
    void seed() {
        records.deleteAllInBatch();
        students.deleteAllInBatch();
        users.deleteAllInBatch();
        students.save(TestFixtures.student(TestFixtures.ROLL_1, "Aditya Rao"));
        users.save(TestFixtures.user("faculty1", Role.FACULTY, "{noop}x"));
        users.save(TestFixtures.user("faculty2", Role.FACULTY, "{noop}x"));
        users.save(TestFixtures.user("hod1", Role.HOD, "{noop}x"));
        users.save(TestFixtures.user("admin1", Role.ADMIN, "{noop}x"));
        users.save(TestFixtures.studentUser("student1", "{noop}x", TestFixtures.ROLL_1));
    }

    private Long draftByFaculty1() {
        TestFixtures.actAs("faculty1", Role.FACULTY);
        return attendanceService.create(TestFixtures.validForm()).getId();
    }

    private Long submittedByFaculty1() {
        Long id = draftByFaculty1();
        workflowService.submit(id);
        return id;
    }

    private WorkflowStatus statusOf(Long id) {
        return records.findById(id).orElseThrow().getWorkflowStatus();
    }

    @Nested
    @DisplayName("Submitting a draft")
    class Submitting {

        @Test
        @DisplayName("FR-16: the author may submit their own draft")
        void facultyMaySubmitOwnDraft() {
            Long id = draftByFaculty1();

            AttendanceRecord submitted = workflowService.submit(id);

            assertThat(submitted.getWorkflowStatus()).isEqualTo(WorkflowStatus.SUBMITTED);
        }

        @Test
        @DisplayName("a submitted record is no longer editable by its author")
        void submittedIsLockedForEditing() {
            Long id = submittedByFaculty1();

            assertThatThrownBy(() -> attendanceService.update(id, TestFixtures.validForm()))
                    .isInstanceOf(RecordLockedException.class);
        }

        @Test
        @DisplayName("FR-20: submitting an already-submitted record is refused and changes nothing")
        void illegalTransitionsRefused() {
            Long id = submittedByFaculty1();

            assertThatThrownBy(() -> workflowService.submit(id))
                    .isInstanceOf(IllegalTransitionException.class)
                    .hasMessageContaining("Submitted");
            assertThat(statusOf(id)).isEqualTo(WorkflowStatus.SUBMITTED);
        }

        @Test
        @DisplayName("one faculty member may not submit another's draft")
        void cannotSubmitAnothersDraft() {
            Long id = draftByFaculty1();
            TestFixtures.actAs("faculty2", Role.FACULTY);

            assertThatThrownBy(() -> workflowService.submit(id))
                    .isInstanceOf(NotPermittedException.class)
                    .hasMessageContaining("faculty1");
            assertThat(statusOf(id)).isEqualTo(WorkflowStatus.DRAFT);
        }

        @Test
        @DisplayName("an administrator may submit any draft")
        void adminMaySubmitAnyDraft() {
            Long id = draftByFaculty1();
            TestFixtures.actAs("admin1", Role.ADMIN);

            assertThat(workflowService.submit(id).getWorkflowStatus())
                    .isEqualTo(WorkflowStatus.SUBMITTED);
        }

        @Test
        @DisplayName("FR-20: a student may not move a record at all")
        void studentMayNotTransition() {
            Long id = draftByFaculty1();
            TestFixtures.actAs("student1", Role.STUDENT);

            assertThatThrownBy(() -> workflowService.submit(id))
                    .isInstanceOf(NotPermittedException.class);
            assertThat(statusOf(id)).isEqualTo(WorkflowStatus.DRAFT);
        }
    }

    @Nested
    @DisplayName("Reviewing a submitted record")
    class Reviewing {

        @Test
        @DisplayName("FR-17/FR-21: approval records the reviewer, the time and the comment")
        void approvalRecordsReviewer() {
            Long id = submittedByFaculty1();
            TestFixtures.actAs("hod1", Role.HOD);

            AttendanceRecord approved = workflowService.approve(id, "Cross-checked with the register");

            assertThat(approved.getWorkflowStatus()).isEqualTo(WorkflowStatus.APPROVED);
            assertThat(approved.getReviewedBy()).isEqualTo("hod1");
            assertThat(approved.getReviewedAt()).isNotNull();
            assertThat(approved.getReviewComment()).isEqualTo("Cross-checked with the register");
        }

        @Test
        @DisplayName("an approval comment is optional")
        void approvalCommentOptional() {
            Long id = submittedByFaculty1();
            TestFixtures.actAs("hod1", Role.HOD);

            AttendanceRecord approved = workflowService.approve(id, "   ");

            assertThat(approved.getWorkflowStatus()).isEqualTo(WorkflowStatus.APPROVED);
            assertThat(approved.getReviewComment()).isNull();
        }

        @Test
        @DisplayName("FR-18: a rejection without a reason is refused and changes nothing")
        void rejectionRequiresReason() {
            Long id = submittedByFaculty1();
            TestFixtures.actAs("hod1", Role.HOD);

            assertThatThrownBy(() -> workflowService.reject(id, "  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must say why");
            assertThat(statusOf(id)).isEqualTo(WorkflowStatus.SUBMITTED);
        }

        @Test
        @DisplayName("FR-21: rejection stores the reviewer and the reason")
        void rejectionRecordsReason() {
            Long id = submittedByFaculty1();
            TestFixtures.actAs("hod1", Role.HOD);

            AttendanceRecord rejected = workflowService.reject(id, "Period number looks wrong");

            assertThat(rejected.getWorkflowStatus()).isEqualTo(WorkflowStatus.REJECTED);
            assertThat(rejected.getReviewedBy()).isEqualTo("hod1");
            assertThat(rejected.getReviewComment()).isEqualTo("Period number looks wrong");
        }

        @Test
        @DisplayName("FR-17: a faculty member may not approve")
        void facultyMayNotApprove() {
            Long id = submittedByFaculty1();

            assertThatThrownBy(() -> workflowService.approve(id, null))
                    .isInstanceOf(NotPermittedException.class);
            assertThat(statusOf(id)).isEqualTo(WorkflowStatus.SUBMITTED);
        }

        @Test
        @DisplayName("FR-20: a draft cannot be approved without being submitted first")
        void draftCannotBeApproved() {
            Long id = draftByFaculty1();
            TestFixtures.actAs("hod1", Role.HOD);

            assertThatThrownBy(() -> workflowService.approve(id, null))
                    .isInstanceOf(IllegalTransitionException.class);
            assertThat(statusOf(id)).isEqualTo(WorkflowStatus.DRAFT);
        }

        @Test
        @DisplayName("an approved record is final: no further transition is permitted")
        void approvedIsFinal() {
            Long id = submittedByFaculty1();
            TestFixtures.actAs("hod1", Role.HOD);
            workflowService.approve(id, null);

            assertThatThrownBy(() -> workflowService.reject(id, "changed my mind"))
                    .isInstanceOf(IllegalTransitionException.class)
                    .hasMessageContaining("final");
            assertThat(statusOf(id)).isEqualTo(WorkflowStatus.APPROVED);
        }
    }

    @Nested
    @DisplayName("After a rejection")
    class AfterRejection {

        private Long rejectedRecord() {
            Long id = submittedByFaculty1();
            TestFixtures.actAs("hod1", Role.HOD);
            workflowService.reject(id, "Wrong period");
            return id;
        }

        @Test
        @DisplayName("US-06 AC-4: a rejected record becomes editable by its author again")
        void rejectedBecomesEditable() {
            Long id = rejectedRecord();
            TestFixtures.actAs("faculty1", Role.FACULTY);

            var correction = TestFixtures.validForm();
            correction.setPeriodNumber(3);

            assertThat(attendanceService.update(id, correction).getPeriodNumber()).isEqualTo(3);
        }

        @Test
        @DisplayName("FR-19: a rejected record can be corrected and re-submitted")
        void rejectedCanBeResubmitted() {
            Long id = rejectedRecord();
            TestFixtures.actAs("faculty1", Role.FACULTY);

            assertThat(workflowService.submit(id).getWorkflowStatus())
                    .isEqualTo(WorkflowStatus.SUBMITTED);
        }
    }

    @Test
    @DisplayName("FR-22: the review queue lists only submitted records")
    void reviewQueueListsOnlySubmitted() {
        draftByFaculty1();
        var second = TestFixtures.validForm();
        second.setPeriodNumber(2);
        TestFixtures.actAs("faculty1", Role.FACULTY);
        Long submittedId = attendanceService.create(second).getId();
        workflowService.submit(submittedId);

        TestFixtures.actAs("hod1", Role.HOD);

        assertThat(workflowService.reviewQueue())
                .singleElement()
                .satisfies(r -> assertThat(r.getId()).isEqualTo(submittedId));
    }
}
