package com.college.attendance.e2e.pages;

import org.openqa.selenium.WebDriver;

public class AttendanceDetailPage extends BasePage {

    public AttendanceDetailPage(WebDriver driver, String baseUrl) {
        super(driver, baseUrl);
    }

    public AttendanceDetailPage open(long id) {
        driver.get(baseUrl + "/attendance/" + id);
        // Waits on the attendance status, which every record has. It used
        // to wait on a workflow status: when that element disappeared the
        // wait would simply time out, with no hint that the page itself was
        // fine and only the locator was stale.
        waitForTestId("detail-attendance-status");
        return this;
    }

    public String attendanceStatus()  { return textOf("detail-attendance-status"); }
    public String student()           { return textOf("detail-student"); }
    public String subject()           { return textOf("detail-subject"); }
    public String markedBy()          { return textOf("detail-marked-by"); }
    public String createdAt()         { return textOf("detail-created-at"); }
    public String updatedAt()         { return textOf("detail-updated-at"); }
    public String excusedBy()         { return textOf("detail-excused-by"); }
    public String excuseNote()        { return textOrEmpty("detail-excuse-note"); }
    public String updatedBy()         { return textOrEmpty("detail-updated-by"); }
    public String remarks()           { return textOrEmpty("detail-remarks"); }

    public String successMessage()    { return textOrEmpty("alert-success"); }
    public String errorMessage()      { return textOrEmpty("alert-error"); }

    public boolean canEdit()    { return isPresent("edit-record"); }

    /** The record identifier from the URL, for assertions across pages. */
    public long recordId() {
        String url = driver.getCurrentUrl();
        String tail = url.replaceAll("[?#].*$", "");
        return Long.parseLong(tail.substring(tail.lastIndexOf('/') + 1));
    }

    public AttendanceFormPage startCorrection() {
        findClickable("edit-record").click();
        waitForTestId("attendance-form");
        return new AttendanceFormPage(driver, baseUrl);
    }
}
