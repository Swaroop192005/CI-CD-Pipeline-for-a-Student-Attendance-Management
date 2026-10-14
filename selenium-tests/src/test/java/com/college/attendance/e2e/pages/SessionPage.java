package com.college.attendance.e2e.pages;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Taking attendance by session: the faculty list, the live roster, and the
 * student's own marking page.
 *
 * <p>One page object for all three because they are one exchange. Splitting
 * them would make it easy for the roster's idea of a session to drift from
 * the list's.
 */
public class SessionPage extends BasePage {

    public SessionPage(WebDriver driver, String baseUrl) {
        super(driver, baseUrl);
    }

    // ---- faculty ----------------------------------------------------------

    public SessionPage openList() {
        driver.get(baseUrl + "/sessions");
        waitForTestId("page-title");
        return this;
    }

    public SessionPage takeAttendance(String subject, LocalDate date, int period) {
        new Select(find("session-subject")).selectByValue(subject);
        // Set through the DOM, not sendKeys. A native date input reads typed
        // characters in the browser's own locale order, and sending an ISO
        // string produced the date 61014-02-02 and a 400 from the server.
        // AttendanceFormPage already does it this way.
        setDateValue(find("session-date"), date);
        find("session-period").clear();
        find("session-period").sendKeys(String.valueOf(period));
        findClickable("open-session").click();
        waitForTestId("page-title");
        return this;
    }

    private void setDateValue(WebElement field, LocalDate date) {
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].value = arguments[1];"
                        + "arguments[0].dispatchEvent(new Event('input', { bubbles: true }));"
                        + "arguments[0].dispatchEvent(new Event('change', { bubbles: true }));",
                field, date.format(DateTimeFormatter.ISO_LOCAL_DATE));
    }

    public boolean hasRunningSessions() {
        return isPresent("open-sessions-table");
    }

    public boolean saysNoSessionIsOpen() {
        return isPresent("no-open-sessions");
    }

    /** Closing from the list, which is where faculty come back to. */
    public SessionPage stopFromList() {
        findClickable("close-from-list").click();
        waitForTestId("page-title");
        return this;
    }

    public SessionPage openRoster() {
        findAll("view-roster").get(0).click();
        waitForUrlContaining("/sessions/");
        waitForTestId("page-title");
        return this;
    }

    public List<String> rosterRollNumbers() {
        return findAll("roster-row-roll").stream().map(e -> e.getText().trim()).toList();
    }

    public String markedCount() {
        return textOf("marked-count");
    }

    public boolean canStrike() {
        return isPresent("strike");
    }

    public boolean canCloseFromRoster() {
        return isPresent("close-session");
    }

    /** Marks a student absent who marked themselves but was not in the room. */
    public SessionPage strikeFirst(String reason) {
        WebElement box = find("strike-reason");
        box.clear();
        box.sendKeys(reason);
        findClickable("strike").click();
        waitForTestId("page-title");
        return this;
    }

    public SessionPage closeFromRoster() {
        findClickable("close-session").click();
        waitForTestId("page-title");
        return this;
    }

    // ---- student ----------------------------------------------------------

    public SessionPage openMarkPage() {
        driver.get(baseUrl + "/sessions/mark");
        waitForTestId("page-title");
        return this;
    }

    public boolean hasSomethingToMark() {
        return isPresent("mark-present");
    }

    public boolean saysNothingToMark() {
        return isPresent("nothing-to-mark");
    }

    public SessionPage markPresent() {
        findClickable("mark-present").click();
        waitForTestId("page-title");
        return this;
    }

    public String ownRoll() {
        return textOf("own-roll");
    }

    public String successMessage() {
        return textOrEmpty("alert-success");
    }

    public String errorMessage() {
        return textOrEmpty("alert-error");
    }
}
