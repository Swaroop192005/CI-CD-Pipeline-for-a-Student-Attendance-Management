# Stage 10 — Continuous Testing in Jenkins

**Deliverable:** Jenkins test report, failed-pipeline evidence, defect
correction commit and successful rerun.
**Issue:** [#6](https://github.com/Swaroop192005/CI-CD-Pipeline-for-a-Student-Attendance-Management/issues/6) · **Pull request:** [#16](https://github.com/Swaroop192005/CI-CD-Pipeline-for-a-Student-Attendance-Management/pull/16)

---

## 1. The gate in the pipeline

```
Checkout → Build → Unit test → Package → Integration test
    → ┌──────────────────────────┐
      │  Quality gate: Selenium  │ ── fails ──▶ build red, deploy stages never run
      └──────────────────────────┘
    → Deploy to Tomcat → Verify deployment
```

Placed **after** packaging and **before** deployment, which is the only
position where it can do its job: the artefact exists to be tested, and
nothing has been deployed yet to be regretted.

### How the gate works

| Step | What happens | Why it is done this way |
|---|---|---|
| 1 | Start a throwaway instance **from the artefact just packaged** | Testing a different build from the one deployed would make the gate decorative |
| 2 | Start a Selenium container | The controller has no browser and no graphics libraries, and `apt` cannot reach the Debian mirrors to install any. The container also pins browser and driver together, removing the version-mismatch failure mode |
| 3 | Wait for both to be ready, printing the container log and failing with a message on timeout | A gate that hangs is worse than one that fails |
| 4 | Run the suite in a `post { always { … } }` block | So the report is published and the containers removed whether the journeys pass or fail |
| 5 | Publish the JUnit XML, archive any screenshots | By the time anyone reads the build, the screenshot is the only record of what the browser saw |
| 6 | `error()` if the suite failed | **This is the gate.** A failing journey stops the build before the deploy stages |

---

## 2. Test reports published by Jenkins

| Build | Tests published | Result |
|---|---|---|
| #9 | **100** passed, 0 failed | SUCCESS |
| #10 | 99 passed, **1 failed** | FAILURE |
| #11 | **100** passed, 0 failed | SUCCESS |

100 = 73 unit + 7 integration + 20 Selenium journeys, all in one report.

---

## 3. The deliberate defect

### What was introduced

Commit `refactor(web): simplify the pagination link` on
`bugfix/pagination-drops-filters`:

```diff
       <a class="btn btn-secondary btn-sm"
          th:if="${records.hasNext()}"
-         th:href="@{/attendance(page=${records.number + 1},
-                                rollNumber=${search.rollNumber}, subjectCode=${search.subjectCode},
-                                from=${search.from}, to=${search.to},
-                                attendanceStatus=${search.attendanceStatus},
-                                workflowStatus=${search.workflowStatus})}"
+         th:href="@{/attendance(page=${records.number + 1})}"
          data-testid="page-next">Next</a>
```

It looks like a tidy-up — the long link really was hard to read — and it
drops every active filter on page change. Page 2 of a filtered search then
silently shows unfiltered results, violating FR-14.

### Why this defect specifically

It is observable **only through a browser**. A dropped query parameter in
a Thymeleaf template is invisible to the unit and integration suites, and
both of them passed on the defective build. Choosing a defect that the
cheaper suites would have caught would have proved nothing about the
browser gate.

It is also the kind of bug that misleads: a user filtering for one student
and seeing somebody else's records on page 2 concludes the *data* is
wrong, not the navigation.

---

## 4. The failed pipeline — build #10

### Stage outcomes

| Stage | Status | Duration |
|---|---|---|
| Checkout | SUCCESS | 1.06 s |
| Build | SUCCESS | 4.24 s |
| Unit test | SUCCESS | 23.22 s |
| Package | SUCCESS | 7.80 s |
| Integration test | SUCCESS | 17.81 s |
| **Quality gate: Selenium** | **FAILED** | 86.05 s |
| Deploy to Tomcat | FAILED | **0.039 s** |
| Verify deployment | FAILED | **0.033 s** |

### The failure

```
[ERROR] com.college.attendance.e2e.J2SearchAndFilterIT.filtersSurvivePagination
        <<< FAILURE!
        [the subject filter must still apply on page 2]
        at J2SearchAndFilterIT.filtersSurvivePagination(J2SearchAndFilterIT.java:97)

[selenium] failure captured: .../J2SearchAndFilterIT.filtersSurvivePagination-20261005-080516-577.png

hudson.AbortException: Selenium quality gate failed. The deployment stages will not run.
```

### Proof the deployment did not run

Durations of 39 ms and 33 ms are suggestive; the console is conclusive:

| Check | Count in build #10's console |
|---|---|
| `Deploying 1.0.10 …` lines | **0** |
| `docker run … attendance-tomcat` invocations | **0** |
| `Parameter verified` lines | **0** |

The previously deployed staging instance was also untouched — still
running from before the red build, still reporting `staging`.

> **A note on the reporting.** Jenkins marks the Deploy and Verify stages
> **FAILED** rather than SKIPPED, because a declarative pipeline marks
> everything after an aborted stage as failed. The counts above are what
> actually demonstrates that no step inside them executed.

### Failure artefacts archived by the red build

```
attendance.war
J2SearchAndFilterIT.filtersSurvivePagination-20261005-080516-577.png
J2SearchAndFilterIT.filtersSurvivePagination-20261005-080516-577.txt
```

The `.txt` is self-describing:

```
Journey : J2SearchAndFilterIT
Test    : J2d: results paginate at ten rows and filters survive the page change (FR-14)
URL     : http://localhost:8095/attendance/attendance?page=1
Title   : Attendance records · Attendance Portal
Failure : AssertionError: [the subject filter must still apply on page 2]
```

The URL alone diagnoses it: `?page=1` with no filter parameters.

---

## 5. The correction — build #11

### Fix commit

`fix(search): keep active filters when changing page` on
`bugfix/restore-filters-on-pagination`. The restored link now carries a
comment saying why it is long, so the next tidy-up does not repeat it:

```html
<!-- Every active filter is echoed into the page links. Without this
     the second page silently shows unfiltered results, which looks
     like a data problem rather than a navigation one (FR-14). -->
```

### Rerun result

| Stage | Status | Duration |
|---|---|---|
| Checkout | SUCCESS | 1.03 s |
| Build | SUCCESS | 4.88 s |
| Unit test | SUCCESS | 23.31 s |
| Package | SUCCESS | 5.64 s |
| Integration test | SUCCESS | 12.16 s |
| **Quality gate: Selenium** | **SUCCESS** | 89.47 s |
| Deploy to Tomcat | SUCCESS | 6.04 s |
| Verify deployment | SUCCESS | 16.04 s |
| **TOTAL** | **SUCCESS** | **160.4 s** |

```
Selenium quality gate passed.
Parameter verified: requested 'staging', deployed 'staging'
Pipeline succeeded. 1.0.11-8734007 is deployed at http://localhost:8090/attendance
```

---

## 6. Summary of the three builds

| Build | Change | Gate | Deployment |
|---|---|---|---|
| **#9** | Baseline | PASSED | Deployed |
| **#10** | Pagination link "simplified" | **FAILED** | **Blocked** |
| **#11** | Filters restored | PASSED | Deployed |

That sequence is the whole claim of this stage: the gate is not decorative.
It caught a real regression that the cheaper suites missed, stopped the
deployment, and left behind a screenshot and a page dump that diagnosed it.

---

## 7. Evidence index

| File | Shows |
|---|---|
| [`proofs/stage-10/quality-gate-evidence.txt`](../proofs/stage-10/quality-gate-evidence.txt) | All three builds side by side with stage tables and the zero-count proof |
| [`proofs/stage-10/failed-pipeline.log`](../proofs/stage-10/failed-pipeline.log) | Full console of the red build |
| [`proofs/stage-10/successful-rerun.log`](../proofs/stage-10/successful-rerun.log) | Full console of the green rerun |
| [`proofs/stage-10/gate-failure-screenshot.png`](../proofs/stage-10/gate-failure-screenshot.png) | What the browser saw when the gate failed |
| [`proofs/stage-10/gate-failure-report.txt`](../proofs/stage-10/gate-failure-report.txt) | URL, title, failure and page source |
| [`01-failed-build.png`](../proofs/stage-10/01-failed-build.png) | Jenkins build #10, red |
| [`02-failed-test-report.png`](../proofs/stage-10/02-failed-test-report.png) | The failing test in the published report |
| [`03-successful-rerun.png`](../proofs/stage-10/03-successful-rerun.png) | Jenkins build #11, green |
| [`04-pipeline-history.png`](../proofs/stage-10/04-pipeline-history.png) | Build history showing the red-then-green sequence |

---

## 8. Stage 10 Definition of Done

| # | Criterion | Status |
|---|---|---|
| D2 | Acceptance criteria have passing tests | 100 tests published per build |
| D5 | Jenkins pipeline green | Build #11 |
| D6 | Selenium quality gate passed | Build #11; build #10 proves it also fails correctly |
| D8 | Stage documentation written | This document |
| D9 | Evidence captured | `proofs/stage-10/` |
| D12 | Backlog updated | US-15 Done |

**Outcome:** the Selenium suite runs inside the pipeline, its results are
published alongside the unit and integration tests, and a failing journey
provably stops the deployment — demonstrated by introducing a real
regression, watching the gate catch it with the deploy stages unexecuted,
correcting it, and rerunning green.
