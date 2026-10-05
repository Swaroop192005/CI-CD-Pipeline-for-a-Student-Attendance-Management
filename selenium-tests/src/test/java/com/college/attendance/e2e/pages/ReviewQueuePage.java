package com.college.attendance.e2e.pages;

import org.openqa.selenium.WebDriver;

import java.util.List;
import java.util.stream.Collectors;

public class ReviewQueuePage extends BasePage {

    public ReviewQueuePage(WebDriver driver, String baseUrl) {
        super(driver, baseUrl);
    }

    public ReviewQueuePage open() {
        driver.get(baseUrl + "/review");
        waitForTestId("page-title");
        return this;
    }

    public int queueSize() {
        return Integer.parseInt(textOf("queue-count").replaceAll("[^0-9]", ""));
    }

    public int rowCount() {
        return findAll("queue-row").size();
    }

    public List<String> rollNumbers() {
        return findAll("queue-roll").stream().map(e -> e.getText().trim()).collect(Collectors.toList());
    }

    public boolean isEmpty() {
        return isPresent("queue-empty");
    }

    public boolean containsRecord(long id) {
        return !driver.findElements(
                org.openqa.selenium.By.cssSelector("[data-record-id='" + id + "']")).isEmpty();
    }

    public AttendanceDetailPage reviewFirst() {
        findAll("queue-review").get(0).click();
        waitForTestId("detail-workflow-status");
        return new AttendanceDetailPage(driver, baseUrl);
    }
}
