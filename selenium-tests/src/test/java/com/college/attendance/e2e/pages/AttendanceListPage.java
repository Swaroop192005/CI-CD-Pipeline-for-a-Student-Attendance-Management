package com.college.attendance.e2e.pages;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

import java.util.List;
import java.util.stream.Collectors;

public class AttendanceListPage extends BasePage {

    public AttendanceListPage(WebDriver driver, String baseUrl) {
        super(driver, baseUrl);
    }

    public AttendanceListPage open() {
        driver.get(baseUrl + "/attendance");
        waitForTestId("page-title");
        return this;
    }

    // ---- Results ----------------------------------------------------------

    public int resultCount() {
        return Integer.parseInt(textOf("result-count").replaceAll("[^0-9]", ""));
    }

    public int rowsOnPage() {
        return findAll("record-row").size();
    }

    public List<String> rollNumbersOnPage() {
        return findAll("cell-roll").stream().map(e -> e.getText().trim()).collect(Collectors.toList());
    }

    public List<String> subjectsOnPage() {
        return findAll("cell-subject").stream().map(e -> e.getText().trim()).collect(Collectors.toList());
    }

    public List<String> workflowStatusesOnPage() {
        return findAll("cell-workflow-status").stream()
                .map(e -> e.getText().trim()).collect(Collectors.toList());
    }

    public List<String> attendanceStatusesOnPage() {
        return findAll("cell-attendance-status").stream()
                .map(e -> e.getText().trim()).collect(Collectors.toList());
    }

    public boolean showsEmptyState() {
        return isPresent("empty-state");
    }

    public String emptyStateText() {
        return textOrEmpty("empty-state");
    }

    public boolean canRecordAttendance() {
        return isPresent("new-record-button");
    }

    // ---- Filters ----------------------------------------------------------

    public AttendanceListPage filterByRollNumber(String rollNumber) {
        WebElement field = find("filter-roll");
        field.clear();
        field.sendKeys(rollNumber);
        return this;
    }

    public AttendanceListPage filterBySubject(String subjectCode) {
        new Select(find("filter-subject")).selectByValue(subjectCode);
        return this;
    }

    public AttendanceListPage filterByAttendanceStatus(String status) {
        new Select(find("filter-attendance-status")).selectByValue(status);
        return this;
    }

    public AttendanceListPage filterByWorkflowStatus(String status) {
        new Select(find("filter-workflow-status")).selectByValue(status);
        return this;
    }

    public AttendanceListPage applyFilters() {
        findClickable("apply-filters").click();
        waitForTestId("result-count");
        return this;
    }

    public AttendanceListPage clearFilters() {
        findClickable("clear-filters").click();
        waitForTestId("result-count");
        return this;
    }

    public boolean filtersAreActive() {
        return isPresent("filters-active");
    }

    // ---- Pagination -------------------------------------------------------

    public boolean hasPagination() {
        return isPresent("pagination");
    }

    public String pageInfo() {
        return textOrEmpty("page-info").replaceAll("\\s+", " ");
    }

    public AttendanceListPage nextPage() {
        findClickable("page-next").click();
        waitForTestId("result-count");
        return this;
    }

    // ---- Navigation -------------------------------------------------------

    public AttendanceDetailPage openFirstRecord() {
        findAll("view-record").get(0).click();
        waitForUrlContaining("/attendance/");
        waitForTestId("detail-workflow-status");
        return new AttendanceDetailPage(driver, baseUrl);
    }

    /** Opens the first record whose workflow status matches, or fails. */
    public AttendanceDetailPage openFirstRecordWithStatus(String workflowStatus) {
        List<WebElement> statuses = findAll("cell-workflow-status");
        List<WebElement> links = findAll("view-record");
        for (int i = 0; i < statuses.size() && i < links.size(); i++) {
            if (statuses.get(i).getText().trim().equalsIgnoreCase(workflowStatus)) {
                links.get(i).click();
                wait.until(ExpectedConditions.presenceOfElementLocated(testId("detail-workflow-status")));
                return new AttendanceDetailPage(driver, baseUrl);
            }
        }
        throw new AssertionError("No record with workflow status '" + workflowStatus
                + "' on this page. Statuses present: " + workflowStatusesOnPage());
    }

    public AttendanceFormPage startNewRecord() {
        findClickable("new-record-button").click();
        waitForTestId("attendance-form");
        return new AttendanceFormPage(driver, baseUrl);
    }
}
