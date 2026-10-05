package com.college.attendance.e2e;

import com.college.attendance.e2e.pages.AttendanceListPage;
import com.college.attendance.e2e.pages.DashboardPage;
import com.college.attendance.e2e.pages.LoginPage;
import com.college.attendance.e2e.support.BaseJourney;
import com.college.attendance.e2e.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Journey J5 — authentication and authorisation.
 *
 * <p>Covers US-10 (FR-01, FR-02, FR-26) and success criterion SC5. The
 * assertions deliberately go beyond "the button is hidden": hiding a
 * control is a convenience, and a test that only checks the UI would pass
 * against a system whose server accepted the request anyway. Each
 * forbidden action is therefore also requested directly by URL.
 */
class J5AuthorisationIT extends BaseJourney {

    /** True when the page the browser landed on is the refusal page. */
    private boolean showsRefusal() {
        return !driver().findElements(By.cssSelector("[data-testid='problem-status']")).isEmpty()
                && driver().findElement(By.cssSelector("[data-testid='problem-status']"))
                           .getText().contains("403");
    }

    @Test
    @DisplayName("J5: an unauthenticated request for a protected page lands on the login page (FR-02)")
    void anonymousIsSentToLogin() {
        driver().get(baseUrl + "/attendance");

        assertThat(driver().getCurrentUrl()).contains("/login");
        assertThat(driver().findElements(By.cssSelector("[data-testid='login-form']"))).isNotEmpty();
    }

    @Test
    @DisplayName("J5b: wrong credentials are refused and establish no session")
    void wrongCredentialsRefused() {
        LoginPage login = new LoginPage(driver(), baseUrl).open()
                .signInExpectingFailure(TestData.FACULTY_USER, "definitely-not-the-password");

        assertThat(login.showsError()).isTrue();
        assertThat(login.errorText()).containsIgnoringCase("wrong username or password");

        // No session was established: a protected page still refuses.
        driver().get(baseUrl + "/attendance");
        assertThat(driver().getCurrentUrl()).contains("/login");
    }

    @Test
    @DisplayName("J5c: a student is refused the record, review and roll pages, by URL as well as by UI")
    void studentIsRefusedStaffPages() {
        signIn(TestData.STUDENT_USER, TestData.STUDENT_PASS);

        // The UI does not offer them.
        AttendanceListPage list = new AttendanceListPage(driver(), baseUrl).open();
        assertThat(list.canRecordAttendance())
                .as("a student must not be offered the record-attendance action")
                .isFalse();
        assertThat(driver().findElements(By.cssSelector("[data-testid='nav-review']"))).isEmpty();
        assertThat(driver().findElements(By.cssSelector("[data-testid='nav-students']"))).isEmpty();

        // And the server refuses them when asked directly.
        for (String path : new String[] {"/attendance/new", "/review", "/students"}) {
            driver().get(baseUrl + path);
            assertThat(showsRefusal())
                    .as("requesting %s directly as a student must be refused by the server, "
                            + "not merely hidden in the UI", path)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("J5d: a faculty member is refused the HOD and administrator pages")
    void facultyIsRefusedHodAndAdminPages() {
        signIn(TestData.FACULTY_USER, TestData.FACULTY_PASS);

        for (String path : new String[] {"/review", "/students"}) {
            driver().get(baseUrl + path);
            assertThat(showsRefusal())
                    .as("requesting %s as a faculty member must be refused", path)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("J5e: each role lands on a dashboard scoped to what it may see")
    void eachRoleSeesItsOwnDashboard() {
        signIn(TestData.HOD_USER, TestData.HOD_PASS);
        DashboardPage hodView = new DashboardPage(driver(), baseUrl).open();
        assertThat(hodView.currentRole()).isEqualTo("HOD");
        assertThat(hodView.showsAtRiskSection())
                .as("a HOD must see the at-risk list - it is the point of the dashboard")
                .isTrue();
        assertThat(hodView.showsStudentScopeNote()).isFalse();

        switchUser(TestData.STUDENT_USER, TestData.STUDENT_PASS);
        DashboardPage studentView = new DashboardPage(driver(), baseUrl).open();
        assertThat(studentView.currentRole()).isEqualTo("STUDENT");
        assertThat(studentView.showsStudentScopeNote()).isTrue();
        assertThat(studentView.atRiskRollNumbers()).isEmpty();
    }

    @Test
    @DisplayName("J5f: signing out ends the session and says so")
    void signOutEndsTheSession() {
        signIn(TestData.ADMIN_USER, TestData.ADMIN_PASS);
        assertThat(new DashboardPage(driver(), baseUrl).open().currentRole()).isEqualTo("ADMIN");

        signOut();

        LoginPage login = new LoginPage(driver(), baseUrl);
        assertThat(driver().getCurrentUrl()).contains("/login");
        assertThat(login.showsSignedOutNotice()).isTrue();

        driver().get(baseUrl + "/students");
        assertThat(driver().getCurrentUrl()).contains("/login");
    }
}
