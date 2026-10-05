package com.college.attendance.e2e.pages;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;

public class AttendanceDetailPage extends BasePage {

    public AttendanceDetailPage(WebDriver driver, String baseUrl) {
        super(driver, baseUrl);
    }

    public AttendanceDetailPage open(long id) {
        driver.get(baseUrl + "/attendance/" + id);
        waitForTestId("detail-workflow-status");
        return this;
    }

    public String workflowStatus()    { return textOf("detail-workflow-status"); }
    public String attendanceStatus()  { return textOf("detail-attendance-status"); }
    public String student()           { return textOf("detail-student"); }
    public String subject()           { return textOf("detail-subject"); }
    public String markedBy()          { return textOf("detail-marked-by"); }
    public String createdAt()         { return textOf("detail-created-at"); }
    public String updatedAt()         { return textOf("detail-updated-at"); }
    public String reviewedBy()        { return textOf("detail-reviewed-by"); }
    public String reviewComment()     { return textOrEmpty("detail-review-comment"); }
    public String remarks()           { return textOrEmpty("detail-remarks"); }

    public String successMessage()    { return textOrEmpty("alert-success"); }
    public String errorMessage()      { return textOrEmpty("alert-error"); }

    public boolean canEdit()    { return isPresent("edit-record"); }
    public boolean canSubmit()  { return isPresent("submit-record"); }
    public boolean canApprove() { return isPresent("approve-record"); }
    public boolean canReject()  { return isPresent("reject-record"); }

    /** The record identifier from the URL, for assertions across pages. */
    public long recordId() {
        String url = driver.getCurrentUrl();
        String tail = url.replaceAll("[?#].*$", "");
        return Long.parseLong(tail.substring(tail.lastIndexOf('/') + 1));
    }

    public AttendanceDetailPage submitForReview() {
        findClickable("submit-record").click();
        wait.until(ExpectedConditions.presenceOfElementLocated(testId("detail-workflow-status")));
        return this;
    }

    public AttendanceDetailPage approve(String comment) {
        if (comment != null && !comment.isBlank()) {
            find("approve-comment").clear();
            find("approve-comment").sendKeys(comment);
        }
        findClickable("approve-record").click();
        wait.until(ExpectedConditions.presenceOfElementLocated(testId("detail-workflow-status")));
        return this;
    }

    public AttendanceDetailPage reject(String reason) {
        if (reason != null) {
            find("reject-reason").clear();
            if (!reason.isEmpty()) {
                find("reject-reason").sendKeys(reason);
            }
        }
        findClickable("reject-record").click();
        wait.until(ExpectedConditions.presenceOfElementLocated(testId("detail-workflow-status")));
        return this;
    }

    public AttendanceFormPage startCorrection() {
        findClickable("edit-record").click();
        waitForTestId("attendance-form");
        return new AttendanceFormPage(driver, baseUrl);
    }
}
