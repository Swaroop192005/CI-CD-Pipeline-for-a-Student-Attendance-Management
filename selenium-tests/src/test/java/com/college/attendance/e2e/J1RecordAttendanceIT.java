package com.college.attendance.e2e;

import com.college.attendance.e2e.pages.AttendanceDetailPage;
import com.college.attendance.e2e.pages.AttendanceFormPage;
import com.college.attendance.e2e.pages.AttendanceListPage;
import com.college.attendance.e2e.support.BaseJourney;
import com.college.attendance.e2e.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Journey J1 — a faculty member signs in and records attendance.
 *
 * <p>Covers US-01 (FR-03 … FR-07) and success criterion SC1: recording a
 * class session must take under two minutes. The timing assertion is part
 * of the journey, because "it works" and "it is usable in the two minutes
 * between classes" are different claims and only one of them replaces the
 * paper register.
 */
class J1RecordAttendanceIT extends BaseJourney {

    /**
     * A period no seeded record uses, so the journey can run repeatedly
     * against the same instance without colliding with its own earlier
     * runs or with the fixtures.
     */
    private static int freePeriod() {
        return 7;
    }

    @Test
    @DisplayName("J1: faculty records attendance for a class session in under two minutes")
    void facultyRecordsAttendance() {
        long startedAt = System.nanoTime();

        signIn(TestData.FACULTY_USER, TestData.FACULTY_PASS);

        AttendanceListPage list = new AttendanceListPage(driver(), baseUrl).open();
        assertThat(list.canRecordAttendance())
                .as("a faculty member must be offered the record-attendance action")
                .isTrue();

        LocalDate sessionDate = LocalDate.now().minusDays(1);
        AttendanceDetailPage detail = list.startNewRecord()
                .selectStudent(TestData.STUDENT_ROLL)
                .enterSubject(TestData.SUBJECT_A)
                .enterSessionDate(sessionDate)
                .enterPeriod(freePeriod())
                .selectAttendanceStatus("PRESENT")
                .enterRemarks("Recorded from the classroom during journey J1")
                .saveExpectingSuccess();

        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        // FR-04: a new record is a draft, not an official mark.
        assertThat(detail.workflowStatus()).isEqualTo("Draft");
        assertThat(detail.attendanceStatus()).isEqualTo("Present");
        assertThat(detail.student()).contains(TestData.STUDENT_ROLL);
        assertThat(detail.subject()).isEqualTo(TestData.SUBJECT_A);

        // FR-07: attribution is what makes the record defensible.
        assertThat(detail.markedBy()).isEqualTo(TestData.FACULTY_USER);
        assertThat(detail.createdAt()).isNotBlank();
        assertThat(detail.updatedAt()).isEqualTo("Never");
        assertThat(detail.reviewedBy()).isEqualTo("Not yet reviewed");

        assertThat(detail.successMessage()).contains("draft");

        // SC1 - the measurable objective, not a vague "it was quick".
        assertThat(elapsed)
                .as("recording one session took %d s; success criterion SC1 is under 2 min",
                        elapsed.toSeconds())
                .isLessThan(Duration.ofMinutes(2));

        System.out.printf("[J1] recorded one session in %d s (SC1 budget: 120 s)%n",
                elapsed.toSeconds());
    }

    @Test
    @DisplayName("J1b: a duplicate session is refused with a message that says why (BR-01)")
    void duplicateSessionRefused() {
        signIn(TestData.FACULTY_USER, TestData.FACULTY_PASS);

        LocalDate sessionDate = LocalDate.now().minusDays(2);
        int period = 8;

        // First entry succeeds.
        new AttendanceFormPage(driver(), baseUrl).open()
                .selectStudent(TestData.OTHER_ROLL)
                .enterSubject(TestData.SUBJECT_B)
                .enterSessionDate(sessionDate)
                .enterPeriod(period)
                .selectAttendanceStatus("ABSENT")
                .saveExpectingSuccess();

        // The same tuple a second time must be refused.
        AttendanceFormPage second = new AttendanceFormPage(driver(), baseUrl).open()
                .selectStudent(TestData.OTHER_ROLL)
                .enterSubject(TestData.SUBJECT_B)
                .enterSessionDate(sessionDate)
                .enterPeriod(period)
                .selectAttendanceStatus("PRESENT")
                .saveExpectingRejection();

        assertThat(second.showsErrorAlert()).isTrue();
        assertThat(second.errorAlertText())
                .as("the message must name the clash, not just say 'error'")
                .contains(TestData.OTHER_ROLL)
                .contains(TestData.SUBJECT_B);
    }

    @Test
    @DisplayName("J1c: a session date in the future is refused (FR-06)")
    void futureSessionDateRefused() {
        signIn(TestData.FACULTY_USER, TestData.FACULTY_PASS);

        AttendanceFormPage form = new AttendanceFormPage(driver(), baseUrl).open()
                .selectStudent(TestData.STUDENT_ROLL)
                .enterSubject(TestData.SUBJECT_A)
                .enterSessionDate(LocalDate.now().plusDays(3))
                .enterPeriod(6)
                .selectAttendanceStatus("PRESENT")
                .saveExpectingRejection();

        assertThat(form.showsErrorAlert()).isTrue();
        assertThat(form.errorAlertText()).containsIgnoringCase("future");
    }
}
