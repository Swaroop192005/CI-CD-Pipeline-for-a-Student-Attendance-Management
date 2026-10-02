package com.college.attendance.e2e.pages;

import org.openqa.selenium.WebDriver;

import java.util.List;
import java.util.stream.Collectors;

public class DashboardPage extends BasePage {

    public DashboardPage(WebDriver driver, String baseUrl) {
        super(driver, baseUrl);
    }

    public DashboardPage open() {
        driver.get(baseUrl + "/dashboard");
        waitForTestId("page-title");
        return this;
    }

    public double attendancePercent() {
        return parsePercent(textOf("stat-percentage"));
    }

    public long draftCount()     { return parseCount("stat-draft"); }
    public long submittedCount() { return parseCount("stat-submitted"); }
    public long approvedCount()  { return parseCount("stat-approved"); }
    public long rejectedCount()  { return parseCount("stat-rejected"); }

    public List<String> subjectCodes() {
        return findAll("subject-code").stream()
                .map(e -> e.getText().trim())
                .collect(Collectors.toList());
    }

    public List<String> atRiskRollNumbers() {
        return findAll("at-risk-roll").stream()
                .map(e -> e.getText().trim())
                .collect(Collectors.toList());
    }

    public boolean showsAtRiskSection() {
        return isPresent("at-risk-table") || isPresent("at-risk-empty");
    }

    public boolean showsStudentScopeNote() {
        return isPresent("student-scope-note");
    }

    public String eligibilityVerdict() {
        return textOrEmpty("eligibility-verdict");
    }

    private long parseCount(String id) {
        return Long.parseLong(textOf(id).replaceAll("[^0-9]", ""));
    }

    private static double parsePercent(String raw) {
        return Double.parseDouble(raw.replace("%", "").trim());
    }
}
