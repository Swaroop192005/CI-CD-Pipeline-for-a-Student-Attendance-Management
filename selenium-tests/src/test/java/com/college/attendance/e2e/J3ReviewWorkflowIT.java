package com.college.attendance.e2e;

import com.college.attendance.e2e.pages.AttendanceDetailPage;
import com.college.attendance.e2e.pages.AttendanceFormPage;
import com.college.attendance.e2e.pages.ReviewQueuePage;
import com.college.attendance.e2e.support.BaseJourney;
import com.college.attendance.e2e.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Journey J3 — the review workflow, end to end and across two people.
 *
 * <p>Covers US-05, US-06 and US-07 (FR-16 … FR-22). This is the journey
 * that the paper register had no equivalent of at all: the whole point of
 * the system is that a mark cannot become official without a second pair
 * of eyes, and that the decision is attributed.
 *
 * <p>Each refusal is asserted together with the record's status, because a
 * rule that refuses but still writes would satisfy a naive assertion while
 * leaving the audit trail wrong.
 */
class J3ReviewWorkflowIT extends BaseJourney {

    private long recordAsFacultyAndSubmit(int period) {
        signIn(TestData.FACULTY_USER, TestData.FACULTY_PASS);

        AttendanceDetailPage detail = new AttendanceFormPage(driver(), baseUrl).open()
                .selectStudent(TestData.OTHER_ROLL)
                .enterSubject(TestData.SUBJECT_A)
                .enterSessionDate(LocalDate.now().minusDays(3))
                .enterPeriod(period)
                .selectAttendanceStatus("PRESENT")
                .saveExpectingSuccess();

        assertThat(detail.workflowStatus()).isEqualTo("Draft");
        long id = detail.recordId();

        detail.submitForReview();
        assertThat(detail.workflowStatus()).isEqualTo("Submitted");
        return id;
    }

    @Test
    @DisplayName("J3: faculty submits, HOD approves, and the decision is attributed")
    void submitThenApprove() {
        long id = recordAsFacultyAndSubmit(5);

        // A submitted record is locked for its author (FR-11).
        AttendanceDetailPage asFaculty = new AttendanceDetailPage(driver(), baseUrl).open(id);
        assertThat(asFaculty.canEdit())
                .as("a submitted record must not offer correction to its author")
                .isFalse();
        assertThat(asFaculty.canApprove())
                .as("a faculty member must not be offered the approve action")
                .isFalse();

        switchUser(TestData.HOD_USER, TestData.HOD_PASS);

        // It is in the HOD's queue (FR-22).
        ReviewQueuePage queue = new ReviewQueuePage(driver(), baseUrl).open();
        assertThat(queue.queueSize()).isPositive();
        assertThat(queue.containsRecord(id))
                .as("the submitted record must appear in the review queue")
                .isTrue();

        AttendanceDetailPage asHod = new AttendanceDetailPage(driver(), baseUrl).open(id);
        assertThat(asHod.canApprove()).isTrue();
        assertThat(asHod.canReject()).isTrue();

        asHod.approve("Cross-checked against the register");

        assertThat(asHod.workflowStatus()).isEqualTo("Approved");
        assertThat(asHod.reviewedBy())
                .as("FR-21: the reviewer must be recorded")
                .isEqualTo(TestData.HOD_USER);
        assertThat(asHod.reviewComment()).isEqualTo("Cross-checked against the register");

        // An approved record is final: no further transition is offered.
        assertThat(asHod.canApprove()).isFalse();
        assertThat(asHod.canReject()).isFalse();
        assertThat(asHod.canEdit()).isFalse();
    }

    @Test
    @DisplayName("J3b: a rejection without a reason is refused and changes nothing (FR-18)")
    void rejectionWithoutReasonRefused() {
        long id = recordAsFacultyAndSubmit(6);
        switchUser(TestData.HOD_USER, TestData.HOD_PASS);

        AttendanceDetailPage detail = new AttendanceDetailPage(driver(), baseUrl).open(id);
        detail.reject("");

        assertThat(detail.workflowStatus())
                .as("a refused rejection must leave the record exactly as it was")
                .isEqualTo("Submitted");
        assertThat(detail.errorMessage()).containsIgnoringCase("must say why");
        assertThat(detail.reviewedBy()).isEqualTo("Not yet reviewed");
    }

    @Test
    @DisplayName("J3c: rejected with a reason, corrected by the author, and re-submitted (FR-19)")
    void rejectCorrectResubmit() {
        long id = recordAsFacultyAndSubmit(4);
        switchUser(TestData.HOD_USER, TestData.HOD_PASS);

        AttendanceDetailPage asHod = new AttendanceDetailPage(driver(), baseUrl).open(id);
        asHod.reject("Period number does not match the timetable");

        assertThat(asHod.workflowStatus()).isEqualTo("Rejected");
        assertThat(asHod.reviewedBy()).isEqualTo(TestData.HOD_USER);
        assertThat(asHod.reviewComment()).contains("timetable");

        switchUser(TestData.FACULTY_USER, TestData.FACULTY_PASS);

        AttendanceDetailPage asFaculty = new AttendanceDetailPage(driver(), baseUrl).open(id);
        assertThat(asFaculty.canEdit())
                .as("US-06 AC-4: a rejected record becomes editable by its author again")
                .isTrue();
        assertThat(asFaculty.canSubmit())
                .as("FR-19: and re-submittable")
                .isTrue();

        asFaculty.startCorrection()
                .selectAttendanceStatus("LATE")
                .enterRemarks("Corrected after review: student arrived late")
                .saveExpectingSuccess();

        AttendanceDetailPage corrected = new AttendanceDetailPage(driver(), baseUrl).open(id);
        assertThat(corrected.attendanceStatus()).isEqualTo("Late");
        assertThat(corrected.markedBy())
                .as("FR-12: a correction must not transfer authorship")
                .isEqualTo(TestData.FACULTY_USER);
        assertThat(corrected.updatedAt())
                .as("FR-12: a correction must be timestamped")
                .isNotEqualTo("Never");

        corrected.submitForReview();
        assertThat(corrected.workflowStatus()).isEqualTo("Submitted");
    }
}
