package com.college.attendance.e2e.pages;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class AttendanceFormPage extends BasePage {

    public AttendanceFormPage(WebDriver driver, String baseUrl) {
        super(driver, baseUrl);
    }

    public AttendanceFormPage open() {
        driver.get(baseUrl + "/attendance/new");
        waitForTestId("attendance-form");
        return this;
    }

    public AttendanceFormPage selectStudent(String rollNumber) {
        new Select(find("field-student")).selectByValue(rollNumber);
        return this;
    }

    public AttendanceFormPage enterSubject(String subjectCode) {
        WebElement field = find("field-subject");
        field.clear();
        field.sendKeys(subjectCode);
        return this;
    }

    /**
     * Sets the date field.
     *
     * <p>A native date input does not accept a plain ISO string reliably
     * across locales, so the value is set through the DOM and an input
     * event dispatched, which is what the browser itself would do.
     */
    public AttendanceFormPage enterSessionDate(LocalDate date) {
        WebElement field = find("field-date");
        String iso = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                "arguments[0].value = arguments[1];"
                        + "arguments[0].dispatchEvent(new Event('input', { bubbles: true }));"
                        + "arguments[0].dispatchEvent(new Event('change', { bubbles: true }));",
                field, iso);
        return this;
    }

    public AttendanceFormPage enterPeriod(int period) {
        WebElement field = find("field-period");
        field.clear();
        field.sendKeys(String.valueOf(period));
        return this;
    }

    public AttendanceFormPage selectAttendanceStatus(String status) {
        new Select(find("field-attendance-status")).selectByValue(status);
        return this;
    }

    public AttendanceFormPage enterRemarks(String remarks) {
        WebElement field = find("field-remarks");
        field.clear();
        field.sendKeys(remarks);
        return this;
    }

    /** Saves, expecting to land on the new record's detail page. */
    public AttendanceDetailPage saveExpectingSuccess() {
        findClickable("save-record").click();
        waitForTestId("detail-workflow-status");
        return new AttendanceDetailPage(driver, baseUrl);
    }

    /** Saves, expecting the form back with an error. */
    public AttendanceFormPage saveExpectingRejection() {
        findClickable("save-record").click();
        waitForTestId("attendance-form");
        return this;
    }

    public boolean showsFieldError(String field) {
        return isPresent("error-" + field);
    }

    public String fieldErrorText(String field) {
        return textOrEmpty("error-" + field);
    }

    public boolean showsErrorAlert() {
        return isPresent("alert-error");
    }

    public String errorAlertText() {
        return textOrEmpty("alert-error");
    }
}
