package com.college.attendance.e2e.pages;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;

public class LoginPage extends BasePage {

    public LoginPage(WebDriver driver, String baseUrl) {
        super(driver, baseUrl);
    }

    public LoginPage open() {
        driver.get(baseUrl + "/login");
        waitForTestId("login-form");
        return this;
    }

    private void submit(String username, String password) {
        waitForTestId("login-form");
        find("login-username").clear();
        find("login-username").sendKeys(username);
        find("login-password").clear();
        find("login-password").sendKeys(password);
        findClickable("login-submit").click();
    }

    /** Signs in and waits until the dashboard has actually rendered. */
    public DashboardPage signInExpectingSuccess(String username, String password) {
        submit(username, password);
        wait.until(ExpectedConditions.not(ExpectedConditions.urlContains("/login")));
        return new DashboardPage(driver, baseUrl);
    }

    /** Signs in expecting refusal, and stays on the login page. */
    public LoginPage signInExpectingFailure(String username, String password) {
        submit(username, password);
        wait.until(ExpectedConditions.urlContains("/login?error"));
        waitForTestId("login-error");
        return this;
    }

    public boolean showsError() {
        return isPresent("login-error");
    }

    public String errorText() {
        return textOrEmpty("login-error");
    }

    public boolean showsSignedOutNotice() {
        return isPresent("login-logged-out");
    }
}
