package com.college.attendance.e2e;

import com.college.attendance.e2e.pages.AttendanceDetailPage;
import com.college.attendance.e2e.pages.AttendanceFormPage;
import com.college.attendance.e2e.pages.AttendanceListPage;
import com.college.attendance.e2e.pages.DashboardPage;
import com.college.attendance.e2e.support.BaseJourney;
import com.college.attendance.e2e.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Journey J4 — a student sees their own approved attendance, same day.
 *
 * <p>Covers US-09 (FR-25) and success criterion SC2: the delay between a
 * mark being approved and the student being able to see it. The measured
 * baseline was 9 to 14 days; this journey approves a record and then signs
 * in as the student to find it, in the same session.
 */
class J4StudentSelfServiceIT extends BaseJourney {

    @Test
    @DisplayName("J4: an approved record reaches the student in the same session (SC2)")
    void approvedRecordIsVisibleToTheStudentSameDay() {
        // Faculty records and submits a session for the student's own roll.
        signIn(TestData.FACULTY_USER, TestData.FACULTY_PASS);
        AttendanceDetailPage detail = new AttendanceFormPage(driver(), baseUrl).open()
                .selectStudent(TestData.STUDENT_ROLL)
                .enterSubject(TestData.SUBJECT_B)
                .enterSessionDate(LocalDate.now().minusDays(4))
                .enterPeriod(5)
                .selectAttendanceStatus("PRESENT")
                .enterRemarks("Journey J4")
                .saveExpectingSuccess();
        long id = detail.recordId();
        detail.submitForReview();

        // Before approval the student must not see it (BR-04).
        switchUser(TestData.STUDENT_USER, TestData.STUDENT_PASS);
        AttendanceListPage studentList = new AttendanceListPage(driver(), baseUrl).open();
        assertThat(studentList.workflowStatusesOnPage())
                .as("a student must only ever see approved records")
                .allMatch(s -> s.equals("Approved"));

        // HOD approves.
        switchUser(TestData.HOD_USER, TestData.HOD_PASS);
        new AttendanceDetailPage(driver(), baseUrl).open(id).approve(null);

        // The student can now see it, in the same session.
        switchUser(TestData.STUDENT_USER, TestData.STUDENT_PASS);
        AttendanceDetailPage asStudent = new AttendanceDetailPage(driver(), baseUrl).open(id);
        assertThat(asStudent.workflowStatus()).isEqualTo("Approved");
        assertThat(asStudent.student()).contains(TestData.STUDENT_ROLL);

        System.out.println("[J4] approved record was visible to the student immediately "
                + "(baseline for this step was 9-14 days)");
    }

    @Test
    @DisplayName("J4b: a student's list shows only their own records, whatever they filter by")
    void studentListIsScopedToTheirOwnRoll() {
        signIn(TestData.STUDENT_USER, TestData.STUDENT_PASS);

        AttendanceListPage list = new AttendanceListPage(driver(), baseUrl).open();
        assertThat(list.resultCount()).isPositive();
        assertThat(list.rollNumbersOnPage())
                .as("FR-25: a student sees only their own roll number")
                .containsOnly(TestData.STUDENT_ROLL);

        // Ask explicitly for somebody else's records and for drafts.
        list.filterByRollNumber(TestData.OTHER_ROLL)
            .filterByWorkflowStatus("DRAFT")
            .applyFilters();

        assertThat(list.resultCount())
                .as("a student's own filters must never widen their scope")
                .isZero();
        assertThat(list.showsEmptyState()).isTrue();
    }

    @Test
    @DisplayName("J4c: the student's dashboard shows their own percentage and eligibility")
    void studentDashboardShowsOwnStanding() {
        signIn(TestData.STUDENT_USER, TestData.STUDENT_PASS);

        DashboardPage dashboard = new DashboardPage(driver(), baseUrl).open();

        assertThat(dashboard.showsStudentScopeNote())
                .as("the student must be told the figures are their own")
                .isTrue();
        assertThat(dashboard.attendancePercent()).isBetween(0.0, 100.0);
        assertThat(dashboard.subjectCodes())
                .as("a per-subject breakdown is what makes the figure actionable")
                .isNotEmpty();
        assertThat(dashboard.eligibilityVerdict())
                .containsAnyOf("Eligible", "Below threshold");
        assertThat(dashboard.atRiskRollNumbers())
                .as("a student has no business seeing which classmates are failing")
                .isEmpty();
    }
}
