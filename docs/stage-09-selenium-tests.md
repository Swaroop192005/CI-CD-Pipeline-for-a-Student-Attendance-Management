# Stage 9 — Selenium Test Design and Local Execution

**Deliverable:** test plan, Selenium scripts, local test report and
failure-screenshot mechanism.
**Issue:** [#5](https://github.com/Swaroop192005/CI-CD-Pipeline-for-a-Student-Attendance-Management/issues/5)

---

## 1. Test plan

### 1.1 How the journeys were chosen

Not "one test per page". Each journey is a path a real user takes to get
a real outcome, chosen by tracing the Stage 1 pain points to the thing
that would have to work for the pain to go away.

| Journey | What it proves | Pain point addressed | Requirements | Success criterion |
|---|---|---|---|---|
| **J1** Record attendance | A faculty member can record a class session, quickly, and it is attributed | P2 re-keying, P3 no audit trail | FR-03 … FR-07 | SC1 (< 2 min) |
| **J2** Search and filter | Anyone can answer a question about one student in seconds | P6 reading a register to find a record | FR-13 … FR-15 | SC8 |
| **J3** Review workflow | No mark becomes official without a reviewed, attributed decision | P4 no review step, P3 no audit trail | FR-16 … FR-22 | SC4 |
| **J4** Student self-service | A student sees their own approved attendance the same day | P1 9–14 day delay | FR-25, BR-04 | SC2 |
| **J5** Authorisation | Role rules are enforced by the server, not just the UI | P5 anyone with the register can change anything | FR-01, FR-02, FR-26 | SC5 |

### 1.2 Test data

Seeded fixtures, documented in one place
([`TestData.java`](../selenium-tests/src/test/java/com/college/attendance/e2e/support/TestData.java))
so that a change to the seed breaks compilation once rather than producing
six confusing assertion failures. All names and roll numbers are invented —
no real student data is used anywhere (constraint C9).

| Account | Password | Role |
|---|---|---|
| `faculty1` | `Faculty@123` | Faculty |
| `faculty2` | `Faculty@123` | Faculty (proves cross-author isolation) |
| `hod1` | `Hod@12345` | Head of Department |
| `admin1` | `Admin@123` | Administrator |
| `student1` | `Student@123` | Student, roll `1CS21CS001` |

Students `1CS21CS001` … `1CS21CS010`; subjects `CS501`, `CS502`, `CS503`.
Periods 1–3 are used by the fixtures; journeys that create records use
periods 4–8 so they never collide with the seed or with each other.

### 1.3 Scope boundaries

The suite does **not** re-test what the unit and integration suites
already cover — percentage arithmetic, transition legality, specification
composition. Those are faster and more precise at that level. The browser
suite exists to prove the parts that only a browser can: that the pages
render, that the controls are wired to the right actions, and that the
server refuses what the UI hides.

---

## 2. Design decisions

Browser suites earn their reputation for flakiness honestly. Four choices
were made specifically against the usual causes.

### 2.1 Locate by `data-testid`, never by class or text

Every element the suite touches carries a stable `data-testid`. CSS
classes exist for styling and change when the stylesheet does; display
text changes when the wording is improved. A test that breaks because a
heading was reworded is a test that will eventually be deleted for crying
wolf, taking its real coverage with it.

### 2.2 A fresh driver per test

Slower than sharing one browser, and worth it: a journey can never inherit
another journey's session, cookies or scroll position. That is the single
most common reason a test passes alone and fails in a suite.

### 2.3 No implicit wait

Mixing implicit and explicit waits makes timeouts unpredictable and
occasionally quadratic. Every wait here is explicit and states what it is
waiting for — a specific element, or a URL change.

### 2.4 A pinned browser and driver, and a fixed viewport

Chromium 141 with ChromeDriver 141, and a fixed 1366×900 window. The
layout is responsive, so an unspecified viewport would let the window size
decide whether an element is considered visible.

---

## 3. The suite

```
selenium-tests/
├── pom.xml
└── src/test/java/com/college/attendance/e2e/
    ├── J1RecordAttendanceIT.java      3 tests
    ├── J2SearchAndFilterIT.java       5 tests
    ├── J3ReviewWorkflowIT.java        3 tests
    ├── J4StudentSelfServiceIT.java    3 tests
    ├── J5AuthorisationIT.java         6 tests
    ├── pages/                         Page objects
    │   ├── BasePage.java              testId() locator, shared waits
    │   ├── LoginPage.java
    │   ├── DashboardPage.java
    │   ├── AttendanceListPage.java    filters, pagination, rows
    │   ├── AttendanceFormPage.java
    │   ├── AttendanceDetailPage.java  workflow actions, audit fields
    │   └── ReviewQueuePage.java
    └── support/
        ├── BaseJourney.java           driver lifecycle, sign in/out
        ├── TestData.java              the fixtures, in one place
        └── ScreenshotOnFailure.java   failure capture
```

**20 tests across the 5 journeys.**

### Assertions worth highlighting

**J1 asserts the measured objective, not just the behaviour.** "It works"
and "it is usable in the two minutes between classes" are different
claims, and only the second one replaces the paper register:

```java
assertThat(elapsed)
    .as("recording one session took %d s; success criterion SC1 is under 2 min",
        elapsed.toSeconds())
    .isLessThan(Duration.ofMinutes(2));
```

**J3 asserts the record is unchanged after every refusal**, not merely
that an error appeared. A rule that refuses but still writes would satisfy
a naive assertion while leaving the audit trail wrong — which is the exact
failure the workflow exists to prevent:

```java
detail.reject("");                                    // no reason given
assertThat(detail.workflowStatus()).isEqualTo("Submitted");   // unchanged
assertThat(detail.reviewedBy()).isEqualTo("Not yet reviewed");
```

**J4 asks for what must not be returned.** A student filters explicitly
for another student's drafts, and must get nothing:

```java
list.filterByRollNumber(TestData.OTHER_ROLL)
    .filterByWorkflowStatus("DRAFT")
    .applyFilters();
assertThat(list.resultCount())
    .as("a student's own filters must never widen their scope")
    .isZero();
```

**J5 goes past the UI.** Hiding a button is a convenience; a test that
only checks the button would pass against a system whose server accepted
the request anyway. Every forbidden action is therefore also requested
directly by URL:

```java
for (String path : new String[] {"/attendance/new", "/review", "/students"}) {
    driver().get(baseUrl + path);
    assertThat(showsRefusal())
        .as("requesting %s directly as a student must be refused by the server, "
            + "not merely hidden in the UI", path)
        .isTrue();
}
```

---

## 4. Failure-screenshot mechanism

A browser test that fails in CI with nothing but a stack trace is usually
unactionable: the stack says which assertion failed, not what the page
looked like. On any failure the suite writes, side by side:

- a **PNG** of the viewport,
- the **page source**, because a rendered error page rarely shows the
  message that matters in a screenshot,
- the **URL, title and failure**, so the artefact is self-describing.

### A bug found while building it

The first version implemented JUnit's `TestWatcher`. It appeared correct
and produced **nothing**: `TestWatcher.testFailed` fires *after* the
`@AfterEach` callbacks, by which time `driver.quit()` has already run and
there is no browser left to photograph.

Rewritten as an `AfterTestExecutionCallback`, which runs *before*
`@AfterEach`, while the browser is still alive. Worth recording because
the broken version failed silently — the mechanism looked wired up, and
would have been discovered only at the moment it was actually needed.

### Demonstrated, not merely claimed

A test was deliberately made to fail once:

```
[selenium] failure captured: .../ScreenshotMechanismDemoIT.deliberateFailure-20261005-074812-479.png

Journey : ScreenshotMechanismDemoIT
Test    : deliberate failure, to demonstrate the capture mechanism
URL     : http://localhost:8080/attendance/dashboard
Title   : Dashboard · Attendance Portal
Failure : AssertionFailedError: expected: "PRINCIPAL" but was: "HOD"
```

Artefacts kept at
[`proofs/stage-09/failure-screenshot-demo.png`](../proofs/stage-09/failure-screenshot-demo.png)
and
[`proofs/stage-09/failure-report-demo.txt`](../proofs/stage-09/failure-report-demo.txt).
The demo test was removed afterwards.

---

## 5. Local execution

```bash
# 1. Start the application
java -jar app/target/attendance.war

# 2. Run the suite through Maven
mvn -pl selenium-tests verify \
    -DskipSeleniumTests=false \
    -Dapp.base.url=http://localhost:8080/attendance \
    -Dwebdriver.chrome.driver=/usr/local/bin/chromedriver \
    -Dselenium.browser.binary=/opt/pw-browsers/chromium-1194/chrome-linux/chrome \
    -Dselenium.headless=true
```

The suite is **skipped by default** (`skipSeleniumTests=true`), so a plain
`mvn verify` at the repository root does not require a running application
and a browser. The pipeline and a developer running it deliberately both
pass `-DskipSeleniumTests=false`.

Driven by **Failsafe**, so the gate is a build phase rather than a script
somebody remembers to run — which is what lets Stage 10 make it block the
deployment.

### Result

```
Browser : Chromium 141.0.7390.37
Driver  : ChromeDriver 141.0.7390.122
Selenium: 4.50.0
Mode    : headless, fixed 1366x900 viewport, fresh driver per test

J1RecordAttendanceIT     Tests run: 3, Failures: 0, Errors: 0   6.7 s
J2SearchAndFilterIT      Tests run: 5, Failures: 0, Errors: 0  10.7 s
J3ReviewWorkflowIT       Tests run: 3, Failures: 0, Errors: 0  12.5 s
J4StudentSelfServiceIT   Tests run: 3, Failures: 0, Errors: 0   8.2 s
J5AuthorisationIT        Tests run: 6, Failures: 0, Errors: 0   9.8 s

Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

[J1] recorded one session in 1 s (SC1 budget: 120 s)
[J4] approved record was visible to the student immediately (baseline 9-14 days)
```

### A second bug found by the suite

`J5f` failed on the first run. The cause was in the *test*, and
instructive: it signed out with `GET /logout`. Spring Security's logout is
a CSRF-protected POST, so the GET logged nobody out — and because the next
sign-in replaces the session anyway, every other journey that "signed out"
that way still appeared to work while never having tested logout at all.

The suite now signs out by clicking the button, which is both what a user
does and what actually ends the session.

---

## 6. Evidence index

| File | Shows |
|---|---|
| [`proofs/stage-09/local-run.log`](../proofs/stage-09/local-run.log) | Full Maven output of the green run |
| [`proofs/stage-09/failsafe-summary.txt`](../proofs/stage-09/failsafe-summary.txt) | Per-journey results, versions, measured criteria |
| [`proofs/stage-09/failure-capture-demo.log`](../proofs/stage-09/failure-capture-demo.log) | The deliberate failure run |
| [`proofs/stage-09/failure-screenshot-demo.png`](../proofs/stage-09/failure-screenshot-demo.png) | The captured viewport |
| [`proofs/stage-09/failure-report-demo.txt`](../proofs/stage-09/failure-report-demo.txt) | The self-describing failure report |
| [`proofs/stage-09/app-under-test.log`](../proofs/stage-09/app-under-test.log) | The application the suite ran against |

---

## 7. Stage 9 Definition of Done

| # | Criterion | Status |
|---|---|---|
| D2 | Each journey has a passing automated test | 20 tests, 5 journeys |
| D3 | Suite green locally | BUILD SUCCESS |
| D6 | Selenium gate | Built here; wired into the pipeline in Stage 10 |
| D8 | Stage documentation written | This document |
| D9 | Evidence captured | `proofs/stage-09/` |
| D12 | Backlog updated | US-14 Done |

**Outcome:** five critical user journeys covered by 20 Selenium WebDriver
tests with meaningful assertions, deterministic fixed test data, and a
failure-capture mechanism that was demonstrated to produce real artefacts
rather than merely being wired up.
