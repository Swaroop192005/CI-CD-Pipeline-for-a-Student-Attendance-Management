package com.college.attendance.e2e;

import com.college.attendance.e2e.pages.SessionPage;
import com.college.attendance.e2e.support.BaseJourney;
import com.college.attendance.e2e.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Journey J3 — taking attendance as a session.
 *
 * <p>Replaces the review-workflow journey. That one walked a record from
 * draft to approved, which measured how quickly a queue could be cleared;
 * the queue is gone, and the thing worth measuring now is whether a class
 * can be recorded without faculty typing a name at all.
 *
 * <p>The assertion that matters most is the one about closing. A session
 * that recorded only the students who marked themselves would be a list of
 * the conscientious, and every percentage built on it would flatter
 * whoever stayed silent.
 */
class J3SessionAttendanceIT extends BaseJourney {

    @Test
    @DisplayName("J3: faculty open a session, a student marks themselves, closing records the rest")
    void sessionLifecycle() {
        int period = TestData.PERIOD_J3_SESSION;
        LocalDate today = LocalDate.now();

        // Faculty open the session. Nobody has been typed in.
        signIn(TestData.FACULTY_USER, TestData.FACULTY_PASS);
        SessionPage sessions = new SessionPage(driver(), baseUrl).openList()
                .takeAttendance(TestData.SUBJECT_A, today, period);
        // Opening lands on the live roster, which is where somebody taking
        // attendance wants to be. The list is a separate page, so it has to
        // be revisited to assert the session appears there too.
        assertThat(sessions.canCloseFromRoster())
                .as("opening a session should land on its roster, ready to close")
                .isTrue();
        assertThat(new SessionPage(driver(), baseUrl).openList().hasRunningSessions())
                .as("and the session should be listed as running")
                .isTrue();

        // The student marks themselves, from their own account.
        switchUser(TestData.STUDENT_USER, TestData.STUDENT_PASS);
        SessionPage mark = new SessionPage(driver(), baseUrl).openMarkPage();
        assertThat(mark.ownRoll()).isEqualTo(TestData.STUDENT_ROLL);
        assertThat(mark.hasSomethingToMark()).isTrue();
        mark.markPresent();
        assertThat(mark.successMessage()).containsIgnoringCase("marked present");

        // BR-11: the same session cannot be marked twice.
        SessionPage again = new SessionPage(driver(), baseUrl).openMarkPage();
        assertThat(again.hasSomethingToMark())
                .as("a session already marked must not be offered again")
                .isFalse();

        // Faculty see the mark arrive on the live roster.
        switchUser(TestData.FACULTY_USER, TestData.FACULTY_PASS);
        SessionPage roster = new SessionPage(driver(), baseUrl).openList().openRoster();
        assertThat(roster.rosterRollNumbers()).contains(TestData.STUDENT_ROLL);
        assertThat(roster.markedCount()).contains("1");
        assertThat(roster.canCloseFromRoster())
                .as("the roster must offer a way to stop taking attendance")
                .isTrue();

        // BR-10: closing writes an absence for everyone who did not mark.
        roster.closeFromRoster();
        assertThat(roster.successMessage())
                .containsIgnoringCase("marked present")
                .containsIgnoringCase("recorded absent");

        // And the window is shut.
        switchUser(TestData.STUDENT_USER, TestData.STUDENT_PASS);
        assertThat(new SessionPage(driver(), baseUrl).openMarkPage().hasSomethingToMark())
                .as("a closed session cannot be marked")
                .isFalse();
    }

    @Test
    @DisplayName("J3b: a student who marked themselves but was not in the room is struck")
    void strikeOverridesASelfMark() {
        int period = TestData.PERIOD_J3_STRIKE;
        LocalDate today = LocalDate.now();

        signIn(TestData.FACULTY_USER, TestData.FACULTY_PASS);
        new SessionPage(driver(), baseUrl).openList()
                .takeAttendance(TestData.SUBJECT_B, today, period);

        switchUser(TestData.STUDENT_USER, TestData.STUDENT_PASS);
        new SessionPage(driver(), baseUrl).openMarkPage().markPresent();

        switchUser(TestData.FACULTY_USER, TestData.FACULTY_PASS);
        SessionPage roster = new SessionPage(driver(), baseUrl).openList().openRoster();
        assertThat(roster.canStrike())
                .as("self-marking is only safe if faculty can override it")
                .isTrue();

        roster.strikeFirst("Not in the room when the register was called");
        assertThat(roster.successMessage())
                .containsIgnoringCase("recorded absent")
                .as("the override is attributed, because no server check can prove presence")
                .containsIgnoringCase("your name");

        // The student cannot undo it by marking again.
        switchUser(TestData.STUDENT_USER, TestData.STUDENT_PASS);
        assertThat(new SessionPage(driver(), baseUrl).openMarkPage().hasSomethingToMark())
                .as("a struck student must not be able to re-mark themselves")
                .isFalse();
    }
}
