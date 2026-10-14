package com.college.attendance.e2e;

import com.college.attendance.e2e.pages.AttendanceDetailPage;
import com.college.attendance.e2e.pages.AttendanceFormPage;
import com.college.attendance.e2e.pages.AttendanceListPage;
import com.college.attendance.e2e.support.BaseJourney;
import com.college.attendance.e2e.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Journey J4 — a student sees their own attendance the moment it is recorded.
 *
 * <p>Covers US-09 (FR-25) and success criterion SC2: the delay between a
 * mark being made and the student being able to see it. The measured
 * baseline was 9 to 14 days.
 *
 * <p>This journey used to submit a record for review, approve it as the
 * head of department, and only then check the student could see it, because
 * BR-04 withheld anything unapproved. Per-record approval is gone, so the
 * delay is now structurally zero: there is no state a record can sit in
 * where the person it describes cannot read it. The journey asserts that
 * directly, which is a stronger claim than the old one — it is the absence
 * of a waiting step rather than a short one.
 */
class J4StudentSelfServiceIT extends BaseJourney {

    @Test
    @DisplayName("J4: a recorded mark reaches the student immediately, with no approval step (SC2)")
    void recordIsVisibleToTheStudentImmediately() {
        signIn(TestData.FACULTY_USER, TestData.FACULTY_PASS);
        AttendanceDetailPage detail = new AttendanceFormPage(driver(), baseUrl).open()
                .selectStudent(TestData.STUDENT_ROLL)
                .enterSubject(TestData.SUBJECT_B)
                .enterSessionDate(LocalDate.now().minusDays(4))
                .enterPeriod(TestData.PERIOD_J4_SELF_SERVICE)
                .selectAttendanceStatus("PRESENT")
                .enterRemarks("Journey J4")
                .saveExpectingSuccess();
        long id = detail.recordId();

        // No submit, no approval. The student signs in and it is simply there.
        switchUser(TestData.STUDENT_USER, TestData.STUDENT_PASS);
        AttendanceDetailPage asStudent = new AttendanceDetailPage(driver(), baseUrl).open(id);

        assertThat(asStudent.attendanceStatus()).isEqualTo("Present");
        assertThat(asStudent.student()).contains(TestData.STUDENT_ROLL);
        assertThat(asStudent.excusedBy())
                .as("an ordinary record is not excused, and says so plainly")
                .isEqualTo("Not excused");

        System.out.println("[J4] the record was visible to the student immediately, with no "
                + "approval step at all (baseline for this step was 9-14 days)");
    }

    @Test
    @DisplayName("J4b: a student sees only their own records and cannot correct them")
    void studentScopeIsOwnRecordsReadOnly() {
        signIn(TestData.STUDENT_USER, TestData.STUDENT_PASS);
        AttendanceListPage list = new AttendanceListPage(driver(), baseUrl).open();

        assertThat(list.rollNumbersOnPage())
                .as("a student's list must hold nobody else's roll number")
                .allMatch(roll -> roll.equals(TestData.STUDENT_ROLL));
        assertThat(list.canEditFromRow())
                .as("a student who could correct their own attendance would make "
                    + "recording it pointless")
                .isFalse();
    }
}
