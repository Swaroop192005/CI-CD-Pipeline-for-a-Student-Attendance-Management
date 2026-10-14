package com.college.attendance.e2e.pages;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.List;

/** Shortage cases, from either side of the conversation. */
public class CasePage extends BasePage {

    public CasePage(WebDriver driver, String baseUrl) {
        super(driver, baseUrl);
    }

    public CasePage openList() {
        driver.get(baseUrl + "/cases");
        waitForTestId("page-title");
        return this;
    }

    public CasePage open(long id) {
        driver.get(baseUrl + "/cases/" + id);
        waitForTestId("case-status");
        return this;
    }

    public boolean hasCandidates() {
        return isPresent("candidates-table");
    }

    /** Roll numbers this user is allowed to raise a case against. */
    public List<String> candidateRollNumbers() {
        return findAll("candidates-table").isEmpty()
                ? List.of()
                : driver.findElements(testId("candidates-table")).get(0)
                        .findElements(org.openqa.selenium.By.cssSelector("tbody tr td:first-child"))
                        .stream().map(e -> e.getText().trim()).toList();
    }

    /** Raises a case against the first candidate and lands on it. */
    public CasePage raiseAgainstFirst(String question) {
        WebElement box = findAll("case-question").get(0);
        box.clear();
        box.sendKeys(question);
        findAll("raise-case").get(0).click();
        waitForTestId("case-status");
        return this;
    }

    public String status()        { return textOf("case-status"); }
    public String tier()          { return textOf("case-tier"); }
    public String question()      { return textOf("case-question-text"); }
    public String response()      { return textOrEmpty("case-response"); }
    public String escalationNote() { return textOrEmpty("escalation-note"); }
    public String successMessage() { return textOrEmpty("alert-success"); }
    public String errorMessage()   { return textOrEmpty("alert-error"); }

    public boolean canRespond()  { return isPresent("response-text"); }
    public boolean canAccept()   { return isPresent("accept-case"); }
    public boolean canReject()   { return isPresent("reject-case"); }
    public boolean canEscalate() { return isPresent("escalate-case"); }
    public boolean showsEscalationTrail() { return isPresent("escalation-card"); }

    private void setDateValue(WebElement field, String isoDate) {
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].value = arguments[1];"
                        + "arguments[0].dispatchEvent(new Event('input', { bubbles: true }));"
                        + "arguments[0].dispatchEvent(new Event('change', { bubbles: true }));",
                field, isoDate);
    }

    public long caseId() {
        String tail = driver.getCurrentUrl().replaceAll("[?#].*$", "");
        return Long.parseLong(tail.substring(tail.lastIndexOf('/') + 1));
    }

    public CasePage respond(String explanation) {
        find("response-text").clear();
        find("response-text").sendKeys(explanation);
        findClickable("send-response").click();
        waitForTestId("case-status");
        return this;
    }

    public CasePage escalate(String reason) {
        find("escalation-reason").clear();
        find("escalation-reason").sendKeys(reason);
        findClickable("escalate-case").click();
        waitForTestId("case-status");
        return this;
    }

    /** Accepts, supplying an excuse window only when the page asks for one. */
    public CasePage accept(String note, String from, String to) {
        if (isPresent("excuse-from")) {
            // Through the DOM, for the same reason SessionPage does: typed
            // characters in a native date input are read in the browser's
            // locale order, which turned an ISO string into 60101-02-02.
            setDateValue(find("excuse-from"), from);
            setDateValue(find("excuse-to"), to);
        }
        if (note != null && isPresent("decision-note")) {
            findAll("decision-note").get(0).sendKeys(note);
        }
        findClickable("accept-case").click();
        waitForTestId("case-status");
        return this;
    }
}
