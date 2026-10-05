package com.college.attendance.e2e.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

/**
 * Shared page behaviour.
 *
 * <p>Every element is located by {@code data-testid}, never by CSS class
 * or display text. Classes exist for styling and change when the stylesheet
 * does; text changes when the wording is improved. A test that breaks
 * because a heading was reworded is a test that will eventually be deleted
 * for crying wolf.
 */
public abstract class BasePage {

    protected static final Duration TIMEOUT = Duration.ofSeconds(20);

    protected final WebDriver driver;
    protected final WebDriverWait wait;
    protected final String baseUrl;

    protected BasePage(WebDriver driver, String baseUrl) {
        this.driver = driver;
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.wait = new WebDriverWait(driver, TIMEOUT);
    }

    protected static By testId(String id) {
        return By.cssSelector("[data-testid='" + id + "']");
    }

    protected WebElement find(String id) {
        return wait.until(ExpectedConditions.presenceOfElementLocated(testId(id)));
    }

    protected WebElement findClickable(String id) {
        return wait.until(ExpectedConditions.elementToBeClickable(testId(id)));
    }

    protected List<WebElement> findAll(String id) {
        return driver.findElements(testId(id));
    }

    protected boolean isPresent(String id) {
        return !driver.findElements(testId(id)).isEmpty();
    }

    protected String textOf(String id) {
        return find(id).getText().trim();
    }

    /** Text of an element that may legitimately be absent. */
    protected String textOrEmpty(String id) {
        try {
            return driver.findElement(testId(id)).getText().trim();
        } catch (NoSuchElementException e) {
            return "";
        }
    }

    protected void waitForTestId(String id) {
        wait.until(ExpectedConditions.presenceOfElementLocated(testId(id)));
    }

    protected void waitForUrlContaining(String fragment) {
        wait.until(ExpectedConditions.urlContains(fragment));
    }

    public String currentUrl() {
        return driver.getCurrentUrl();
    }

    /** The environment label in the top bar, for deployment verification. */
    public String environmentBadge() {
        return textOrEmpty("env-badge");
    }

    /** The signed-in user's role, without the ROLE_ prefix. */
    public String currentRole() {
        return textOrEmpty("current-role");
    }
}
