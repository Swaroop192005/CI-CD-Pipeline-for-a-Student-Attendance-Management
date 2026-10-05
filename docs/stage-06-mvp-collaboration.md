# Stage 6 — MVP Completion and Git Collaboration

**Deliverable:** functional MVP, resolved merge conflict, tagged version
and updated backlog.
**Issue:** [#2](https://github.com/Swaroop192005/CI-CD-Pipeline-for-a-Student-Attendance-Management/issues/2) · **Pull requests:** [#13](https://github.com/Swaroop192005/CI-CD-Pipeline-for-a-Student-Attendance-Management/pull/13), [#14](https://github.com/Swaroop192005/CI-CD-Pipeline-for-a-Student-Attendance-Management/pull/14) · **Tag:** `v1.0.0`

---

## 1. MVP completed

Every functional requirement FR-01 … FR-29 now has an implementation and
a passing test.

| Capability | Requirement | Where |
|---|---|---|
| Correct an editable record, preserving the original author | FR-10 … FR-12 | `AttendanceService.update` |
| Search across five optional filters, AND-combined, surviving pagination | FR-13 … FR-15 | `AttendanceSpecifications`, `AttendanceSearch` |
| Role-based status workflow with mandatory rejection reason | FR-16 … FR-22 | `WorkflowService` |
| Summary dashboard: counts, percentage, per-subject, at-risk | FR-23, FR-24 | `DashboardService` |
| Student self-service, scoped to own approved records | FR-25, FR-26 | `AttendanceService.search`, `CurrentUser` |
| Administrator's student roll | FR-28 | `StudentController` |

### Design decisions worth stating

**One code path for every status change.** `WorkflowService` is the only
component permitted to write `workflowStatus` (BR-07). This is what makes
the audit story hold: there is exactly one route between states, and it
cannot be taken without recording who took it and when. Every transition
is checked three ways — the transition is in the permitted set, the
acting role may perform it, and any precondition is satisfied — all
**before** anything is written, so a refusal never leaves a partial change
behind (FR-20). Each refusal test asserts the status is unchanged, not
merely that an exception was thrown.

**Search as composable specifications, not derived queries.** Five
independently optional filters would need one derived method per
combination. More importantly, composition is what makes scoping safe:
the student predicate is AND-ed on top of whatever the user asked for, so
a student cannot widen their view by editing a query parameter. The test
`studentScopeCannotBeWidened` asks explicitly for another student's
drafts and asserts nothing comes back.

**Scoping in the service, not the controller.** The list and the detail
view are restricted by the same rule. Two separate checks would
eventually drift apart, and the one that drifted would be the hole. This
also closes the unscoped-detail gap raised in the review of PR #12.

**Percentages from approved records only (BR-04), with EXCUSED removed
from both sides of the fraction (BR-03).** A draft is not yet a fact
about a student. And counting approved leave as an absence would punish a
student for a certificate the institution itself accepted. Both are easy
to get subtly wrong while still producing a plausible number, so both
have dedicated tests.

---

## 2. Defects found by exercising the running application

Three defects that the test suite did not catch, found by driving the
whole flow against a running instance. All three are fixed in this stage.

| # | Defect | Why it mattered | Fix |
|---|---|---|---|
| 1 | The correction form was served for an already-submitted record | The save would have been refused — but only after the faculty member had typed the correction | Refuse when the form is requested (409), not when it is saved |
| 2 | A refused POST answered **405 Method Not Allowed** instead of 403 | The filter chain forwards the original request to the access-denied page, so a POST reached a GET-only mapping. It reads as "wrong verb" when the truth is "wrong role", and an API client would have been misled | Map the page for every method and set 403 explicitly |
| 3 | `/actuator/health` reported `UP` **before seeding had finished** | Seeding ran as an `ApplicationRunner`, which fires after the servlet container binds its port. A sign-in in that window failed. Both the Jenkins pipeline and the Ansible playbook gate on that health check, so this would have surfaced in Stage 10 or 14 as an intermittent deployment failure | Seed during context refresh, via a `TransactionTemplate` (the `@Transactional` proxy is not yet in place during bean initialisation, so the annotation would have silently done nothing) |

Defect 3 is the one worth dwelling on. It was found because a login
failed only on a fresh start — the kind of symptom that is normally
written off as a flake. Had it been, Stage 14's idempotency and rollback
runs would have been intermittently red for reasons that had nothing to
do with Ansible.

### Evidence — the whole flow, exercised

From [`proofs/stage-06/workflow-demo.log`](../proofs/stage-06/workflow-demo.log):

```
-- faculty1 submits #1 for review (DRAFT -> SUBMITTED)   status now: Submitted
-- faculty1 tries to correct a submitted record          HTTP 409
-- faculty1 tries to approve their own record            HTTP 403, status still: Submitted
-- hod1 rejects with no reason                           status still: Submitted
-- hod1 rejects with a reason                            status now: Rejected
-- faculty1 re-submits after correction                  status now: Submitted
-- hod1 approves                                         status now: Approved
-- hod1 tries to reject an approved record               status still: Approved

student1 GET /attendance/new -> 403    student1 GET /review   -> 403
student1 GET /students       -> 403    faculty1 GET /students -> 403
hod1     GET /review         -> 200

records visible to student1: 21 · roll numbers shown: 1CS21CS001 · statuses: Approved

roll=1CS21CS001 AND subject=CS503 -> 10 records
attendanceStatus=ABSENT           -> 42 records
rollNumber=NOSUCHROLL             ->  0 records, empty state shown

dashboard: 85.9% overall · 30 draft · 30 awaiting review · 210 approved · 30 rejected · 1 at risk
```

### Screenshots

| View | File |
|---|---|
| HOD dashboard with at-risk list | [`01-dashboard-hod.png`](../proofs/stage-06/01-dashboard-hod.png) |
| Review queue | [`02-review-queue.png`](../proofs/stage-06/02-review-queue.png) |
| Search with filters applied | [`03-search-filters.png`](../proofs/stage-06/03-search-filters.png) |
| Record detail with audit trail | [`04-record-detail-audit.png`](../proofs/stage-06/04-record-detail-audit.png) |
| Student's own dashboard | [`05-student-dashboard.png`](../proofs/stage-06/05-student-dashboard.png) |
| Student's own records | [`06-student-records.png`](../proofs/stage-06/06-student-records.png) |
| Administrator's student roll | [`07-student-roll.png`](../proofs/stage-06/07-student-roll.png) |

---

## 3. Second feature branch

`feature/status-workflow-dashboard`, branched from `develop`, seven
commits:

| Commit | Message |
|---|---|
| `e9fc26d` | `feat(workflow): enforce role-based status transitions` |
| `f83985f` | `feat(search): add filtered search and student record scoping` |
| `112e6a3` | `feat(dashboard): add summary with per-subject and at-risk figures` |
| `de9ee17` | `feat(web): add dashboard, review queue, filters and workflow actions` |
| `9f5fad0` | `fix(config): seed before the port opens, and seed a realistic state mix` |
| `12aadea` | `test(workflow): cover transitions, search scoping and dashboard rules` |
| `96c6235` | `docs(readme): mark the MVP feature set as delivered` |

---

## 4. Merge conflict: creation and resolution

The conflict was **created deliberately**, by letting two branches edit
the same block of `README.md` concurrently rather than sequencing the
work to avoid it.

### How it arose

```
develop                            <- PR #13 restructured the feature table,
                                      adding a Requirement column
feature/status-workflow-dashboard  <- rewrote the same rows with a prose
                                      status saying what was actually built
```

PR #13 was merged into `develop` first. PR #14 then reported
`mergeable_state: dirty`.

### What git reported

```
$ git merge origin/develop
Auto-merging README.md
CONFLICT (content): Merge conflict in README.md
Automatic merge failed; fix conflicts and then commit the result.

$ git status --short
UU README.md

$ git diff --name-only --diff-filter=U
README.md
```

```
<<<<<<< HEAD
| # | Capability | Roles | Status |
| F1 | Record attendance ... | Faculty, Admin | Delivered — one record per student, subject, ... |
=======
| # | Capability | Roles | Requirement | Status |
| F1 | Record attendance ... | Faculty, Admin | FR-03 … FR-07 | Done |
>>>>>>> origin/develop
```

### Resolution

**Neither side was discarded.** The two edits were not in opposition:
one added *traceability* (which requirement specifies this feature), the
other added *substance* (what the feature actually does). Taking either
alone would have thrown away information a reader needs, and
`--theirs` / `--ours` would have silently deleted half of it.

Resolved to the **union** — the `Requirement` column from `develop`
carrying the prose status from the feature branch:

```markdown
| # | Capability | Roles | Requirement | Status |
|---|---|---|---|---|
| F1 | Record attendance for a (student, subject, date, period) | Faculty, Admin | FR-03 … FR-07 | Delivered — one record per student, subject, session date and period, enforced by a database constraint |
```

### Verification after resolution

A resolved conflict that is not re-verified is a guess that happened to
compile:

```
$ grep -c -E '^(<<<<<<<|=======|>>>>>>>)' README.md
0

$ mvn -B clean verify
Tests run: 73, Failures: 0, Errors: 0, Skipped: 0   (Surefire)
Tests run:  7, Failures: 0, Errors: 0, Skipped: 0   (Failsafe)
BUILD SUCCESS
```

Merge commit `fca7dc5`. Full evidence, including the raw `git merge`
output and the conflict exactly as git produced it, in
[`proofs/stage-06/merge-conflict.log`](../proofs/stage-06/merge-conflict.log).

---

## 5. Tagged release

`develop` was merged into `main` with `--no-ff` and tagged:

```
$ git tag -n99
v1.0.0   v1.0.0 - Student Attendance Management Portal MVP
         The frozen MVP from Stage 1, complete and release-ready.
         ...
         Quality
           - 80 tests: 73 unit and slice, 7 integration. All green.
           - Three defects found by exercising the running application and
             fixed before release.

$ git describe --tags
v1.0.0
```

An **annotated** tag, not lightweight: it carries the release notes, the
author and the date as a real object in the repository, which a
lightweight tag cannot.

### Known limitation — the tag is not on origin

Pushing the tag is refused by this session's GitHub credentials:

```
$ git push origin refs/tags/v1.0.0
error: RPC failed; HTTP 403 curl 22 The requested URL returned error: 403
fatal: the remote end hung up unexpectedly
```

Branch pushes from the same credentials succeed — `main`, `develop` and
three feature branches are all on origin — so the restriction is
specific to `refs/tags/*` and is an environment limitation, not a
repository or history problem. The tag object exists in the repository
history and is reproduced verbatim in
[`proofs/stage-06/release-tag.log`](../proofs/stage-06/release-tag.log).

From a clone with ordinary credentials it publishes with one command:

```bash
git push origin v1.0.0
```

This is recorded in the Stage 15 troubleshooting guide and limitations
list rather than quietly omitted.

---

## 6. Verification

```
mvn -B clean verify

Tests run: 73, Failures: 0, Errors: 0, Skipped: 0   (Surefire, unit and slice)
Tests run:  7, Failures: 0, Errors: 0, Skipped: 0   (Failsafe, integration)
BUILD SUCCESS
```

Eighty tests, up from thirty-nine at the end of Stage 5.

| Test class | Tests | Covers |
|---|---|---|
| `WorkflowServiceTest` | 16 | US-05, US-06, US-07; FR-16 … FR-22 |
| `AttendanceSearchTest` | 14 | US-04, US-09; FR-13 … FR-15, FR-25 |
| `DashboardServiceTest` | 11 | US-08, US-20; FR-23, FR-24, BR-03, BR-04 |
| `AttendanceServiceTest` | 13 | US-01, US-03, US-07 |
| `AttendanceSecurityTest` | 8 | US-10; FR-01, FR-02, FR-26, FR-27 |
| `CurrentUserTest` | 5 | Scoping, including the fail-open regression |
| `AttendanceRecordRepositoryTest` | 5 | FR-08, BR-01 |
| `AttendanceWebFlowIT` | 7 | End-to-end web flow |
| `AttendancePortalApplicationTests` | 1 | Context loads |

---

## 7. Updated backlog

| ID | Story | Points | Status |
|---|---|---|---|
| US-01 | Record attendance | 8 | Done (Stage 5) |
| US-02 | Paginated list | 5 | Done (Stage 5) |
| US-03 | Correct an editable record | 5 | **Done** |
| US-04 | Search and filter | 5 | **Done** |
| US-05 | Submit for review | 3 | **Done** |
| US-06 | Approve or reject | 8 | **Done** |
| US-07 | Attribution on every change | 3 | **Done** |
| US-08 | Summary dashboard | 8 | **Done** |
| US-09 | Student self-service | 5 | **Done** |
| US-10 | Authentication and authorisation | 5 | Done (Stage 5) |
| US-11 | Repository with policy | 3 | Done (Stage 4) |
| US-20 | At-risk list | 3 | **Done** |

**61 of 128 committed points delivered.** Sprints 1 and 2's product
scope is complete; the remaining 67 points are the engineering stories
US-12 … US-19, delivered in Stages 7 to 14.

---

## 8. Stage 6 Definition of Done

| # | Criterion | Status |
|---|---|---|
| D1 | Reviewed on a pull request | PR #14, with the conflict resolution documented in a comment |
| D2 | Every acceptance criterion has a passing test | §6 |
| D3 | `mvn clean verify` green | 80 tests, BUILD SUCCESS |
| D5 | Jenkins pipeline green | **Not applicable** — Jenkins arrives in Stage 7 |
| D6 | Selenium gate passed | **Not applicable** — suite arrives in Stage 9 |
| D8 | Stage documentation written | This document |
| D9 | Evidence captured | `proofs/stage-06/` |
| D11 | Merged | PR #13 and PR #14 both merged into `develop`; `develop` merged into `main` |
| D12 | Backlog updated | §7 |

**Outcome:** the MVP is functionally complete and release-ready, a second
feature branch was delivered through a pull request, a genuine merge
conflict was created, inspected, resolved by combining both sides and
re-verified, and the release point is tagged `v1.0.0`.
