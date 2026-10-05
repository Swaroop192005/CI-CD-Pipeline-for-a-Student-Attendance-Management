package com.college.attendance.e2e.support;

import com.college.attendance.e2e.pages.LoginPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;

/**
 * Driver lifecycle shared by every journey.
 *
 * <p>Three deliberate choices, each aimed at the usual causes of flaky
 * browser suites:
 *
 * <ul>
 *   <li><b>A fresh driver per test.</b> Slower than sharing one, but a
 *       journey can never inherit another journey's session, cookies or
 *       scroll position - the most common source of tests that pass alone
 *       and fail in a suite.</li>
 *   <li><b>No implicit wait.</b> Mixing implicit and explicit waits makes
 *       timeouts unpredictable; every wait in this suite is explicit and
 *       states what it is waiting for.</li>
 *   <li><b>A fixed 1366x900 window.</b> The layout is responsive, so an
 *       unspecified size would let the viewport decide whether an element
 *       is visible.</li>
 * </ul>
 *
 * <p>The suite drives a local browser by default and a remote one when
 * {@code selenium.remote.url} is set. The pipeline uses the remote mode:
 * the Jenkins controller has no browser and no graphics libraries, so the
 * journeys run against a Selenium container instead. The same test code
 * serves both, which matters - a CI-only code path is a code path nobody
 * debugs until it breaks in CI.
 */
@ExtendWith(ScreenshotOnFailure.class)
public abstract class BaseJourney {

    protected static final Duration TIMEOUT = Duration.ofSeconds(20);

    private WebDriver driver;
    protected String baseUrl;

    @BeforeEach
    void startBrowser() {
        baseUrl = System.getProperty("app.base.url", "http://localhost:8080/attendance")
                .replaceAll("/$", "");

        String driverPath = System.getProperty("webdriver.chrome.driver", "");
        if (!driverPath.isBlank()) {
            System.setProperty("webdriver.chrome.driver", driverPath);
        }

        ChromeOptions options = new ChromeOptions();
        String binary = System.getProperty("selenium.browser.binary", "");
        if (!binary.isBlank()) {
            options.setBinary(binary);
        }
        if (Boolean.parseBoolean(System.getProperty("selenium.headless", "true"))) {
            options.addArguments("--headless=new");
        }
        options.addArguments(
                "--no-sandbox",
                "--disable-gpu",
                "--disable-dev-shm-usage",
                "--window-size=1366,900",
                "--hide-scrollbars",
                // Keeps the suite deterministic across machines.
                "--lang=en-GB",
                "--disable-search-engine-choice-screen");

        String remoteUrl = System.getProperty("selenium.remote.url", "");
        if (remoteUrl.isBlank()) {
            driver = new ChromeDriver(options);
        } else {
            try {
                driver = new RemoteWebDriver(URI.create(remoteUrl).toURL(), options);
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException(
                        "selenium.remote.url is not a valid URL: " + remoteUrl, e);
            }
        }
        driver.manage().window().setSize(new Dimension(1366, 900));
    }

    @AfterEach
    void stopBrowser() {
        if (driver != null) {
            driver.quit();
            driver = null;
        }
    }

    public WebDriver driver() {
        return driver;
    }

    protected LoginPage openLoginPage() {
        driver.get(baseUrl + "/login");
        return new LoginPage(driver, baseUrl);
    }

    /** Signs in and lands on the dashboard. */
    protected void signIn(String username, String password) {
        openLoginPage().signInExpectingSuccess(username, password);
    }

    /**
     * Signs out the way a user does: by clicking the button.
     *
     * <p>Not by navigating to {@code /logout}. Spring Security's logout is
     * a POST protected by a CSRF token, so a GET simply does not log
     * anybody out - and because the next sign-in replaces the session
     * anyway, a journey that "signed out" with a GET would still appear to
     * work while never having tested logout at all.
     */
    protected void signOut() {
        driver.findElement(org.openqa.selenium.By.cssSelector("[data-testid='logout']")).click();
        new org.openqa.selenium.support.ui.WebDriverWait(driver, TIMEOUT)
                .until(org.openqa.selenium.support.ui.ExpectedConditions.urlContains("/login"));
    }

    /** Signs out and signs straight back in as somebody else. */
    protected void switchUser(String username, String password) {
        signOut();
        signIn(username, password);
    }
}
