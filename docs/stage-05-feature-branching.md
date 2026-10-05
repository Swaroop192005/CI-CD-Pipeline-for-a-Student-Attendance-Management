# Stage 5 — Feature Development with Branching

**Deliverable:** working feature 1, feature branch, pull request, review
comments and merge evidence.
**Issue:** [#1](https://github.com/Swaroop192005/CI-CD-Pipeline-for-a-Student-Attendance-Management/issues/1) · **Pull request:** [#12](https://github.com/Swaroop192005/CI-CD-Pipeline-for-a-Student-Attendance-Management/pull/12)

---

## 1. Feature delivered

The first **vertical slice** through the whole stack — security filter
chain → controller → service → repository → H2 — rather than a
horizontal layer. The reason is diagnostic: when the second feature
fails, the failure is in that feature and not in plumbing that was never
exercised.

| Story | Capability |
|---|---|
| US-01 | A faculty member records attendance for a (student, subject, session date, period) |
| US-02 | Records appear in a paginated list, most recent session first, with a detail view |
| US-10 | Sign-in with role-based authorisation (needed before either of the above can be demonstrated) |
| FR-29 | Deterministic seeded fixtures (needed for repeatable verification) |

Deliberately **excluded**: workflow transitions. They belong to
`WorkflowService` in Stage 6, which is to be the only component allowed
to change `workflowStatus` (BR-07). Letting this controller advance the
workflow "just for now" would have undermined that rule before it existed.

### What it looks like

| Screen | Evidence |
|---|---|
| Sign-in | [`proofs/stage-05/01-login.png`](../proofs/stage-05/01-login.png) |
| Attendance list, paginated | [`proofs/stage-05/02-attendance-list.png`](../proofs/stage-05/02-attendance-list.png) |
| Record-attendance form | [`proofs/stage-05/03-new-record-form.png`](../proofs/stage-05/03-new-record-form.png) |
| Record detail with audit trail | [`proofs/stage-05/04-record-detail.png`](../proofs/stage-05/04-record-detail.png) |

---

## 2. Branch

```bash
git checkout develop
git checkout -b feature/attendance-core
```

Conforms to the naming grammar in `CONTRIBUTING.md`: type prefix
`feature`, single `/`, lower-case kebab-case description of the outcome.

---

## 3. Git operations exercised

| Operation | Command | Where it shows |
|---|---|---|
| Branch | `git checkout -b feature/attendance-core develop` | Branch list |
| Stage | `git add app/src/main/java/com/college/attendance/domain/` | Eight focused commits, one logical change each |
| Commit | `git commit -m "feat(domain): ..."` | Log below |
| Push | `git push -u origin feature/attendance-core` | Remote branch created |
| Pull | `git pull origin develop` | After merge, to sync local `develop` |
| Log | `git log --graph --oneline --decorate` | [`proofs/stage-05/git-evidence.log`](../proofs/stage-05/git-evidence.log) |
| Merge | Pull request #12, merge commit | `90948d0` |

### Commit history on the branch

| Commit | Message |
|---|---|
| `d5f812b` | `feat(domain): add student, account and attendance record entities` |
| `7a99f21` | `feat(security): add role-based authentication and seeded fixtures` |
| `5c0a18b` | `feat(service): add attendance creation and correction` |
| `6707345` | `feat(web): add attendance list, detail and record-entry form` |
| `f9487e8` | `test(service): cover creation, correction and authorisation rules` |
| `4494f28` | `docs(stage-05): capture local verification evidence for feature 1` |
| `bd72c2f` | `fix(security): refuse a student account with no linked roll number` ← review fix |
| `e3728c7` | `docs(stage-05): refresh verification log after the review fix` |

### Merge graph

```
*   90948d0 (develop) Merge pull request #12 from feature/attendance-core
|\
| * e3728c7 docs(stage-05): refresh verification log after the review fix
| * bd72c2f fix(security): refuse a student account with no linked roll number
| * 4494f28 docs(stage-05): capture local verification evidence for feature 1
| * f9487e8 test(service): cover creation, correction and authorisation rules
| * 6707345 feat(web): add attendance list, detail and record-entry form
| * 5c0a18b feat(service): add attendance creation and correction
| * 7a99f21 feat(security): add role-based authentication and seeded fixtures
| * d5f812b feat(domain): add student, account and attendance record entities
|/
* e7e970e (main) docs(stage-04): record repository initialisation
```

A **merge commit** was used rather than a squash, so that the eight
reviewable steps — and in particular the review fix as its own commit —
remain in history. The whole point of the review was the fix; squashing
would have hidden it.

---

## 4. Pull request and review

**[PR #12](https://github.com/Swaroop192005/CI-CD-Pipeline-for-a-Student-Attendance-Management/pull/12): feat: record and view attendance (feature 1, US-01/US-02)**
`feature/attendance-core` → `develop`

The pull request body maps each acceptance criterion to the test that
proves it, and the Definition of Done checklist is filled in honestly:
the Jenkins and Selenium rows are left unticked with the reason, because
neither exists until Stages 7 and 9.

### Review findings

A review was performed against the diff. GitHub does not permit an author
to formally request changes on their own pull request, so the review was
submitted as a comment review — the blocking item was still treated as
blocking and the merge waited for it.

#### Finding 1 — blocking — authorisation fails open

`CurrentUser.restrictedToRollNumber()` returned `Optional<String>`, with
empty documented as *"not restricted"*:

```java
return account()
        .filter(u -> u.getRole() == Role.STUDENT)
        .map(AppUser::getLinkedRollNumber);
```

`Optional.map` over a null value yields an **empty** Optional, not an
Optional containing null. So a `STUDENT` account whose
`linkedRollNumber` was never set produced exactly the same result as a
faculty account — and callers read that as "this role is not scoped".

**The account that most needed restricting was the one that got
unrestricted access.**

This was reachable, not theoretical: `linked_roll_number` has no
`NOT NULL` constraint for student rows, and nothing in seeding or an
admin flow enforces it. Stage 6 was about to build the student
self-service view (US-09, FR-25) directly on this method, so the hole
would have been inherited rather than introduced later.

**Fix** (`bd72c2f`): the two cases are now distinguishable. A student
account with a missing or blank roll number throws
`MisconfiguredAccountException` naming the account, so a misconfiguration
is a loud failure rather than a silent privilege escalation.

```java
Optional<AppUser> student = account().filter(u -> u.getRole() == Role.STUDENT);
if (student.isEmpty()) {
    return Optional.empty();          // genuinely unscoped role
}
String rollNumber = student.get().getLinkedRollNumber();
if (rollNumber == null || rollNumber.isBlank()) {
    throw new MisconfiguredAccountException(student.get().getUsername());
}
return Optional.of(rollNumber);
```

Five tests added in `CurrentUserTest`; two of them
(`studentWithoutRollNumberIsRefused`, `blankRollNumberIsRefused`) cover
precisely the case that was broken.

#### Finding 2 — non-blocking — unscoped detail view

`AttendanceController.detail()` loads any record for any authenticated
user, so a student could open another student's record. Deferred to
Stage 6 **by decision, not by omission**: the list is unscoped too, and
both should be restricted by the same predicate rather than by two rules
that can drift apart. The method now carries a note saying so, so the
current behaviour is not mistaken for intentional.

#### Finding 3 — nit — check-then-act on the duplicate test

`existsBy...` followed by `save` can, under two concurrent submissions,
surface a `DataIntegrityViolationException` instead of the readable
`DuplicateRecordException`. The database constraint still prevents bad
data, so this is message quality rather than correctness. Carried
forward.

---

## 5. Verification

```
mvn -B clean verify

Tests run: 32, Failures: 0, Errors: 0, Skipped: 0   (Surefire, unit)
Tests run:  7, Failures: 0, Errors: 0, Skipped: 0   (Failsafe, integration)
BUILD SUCCESS
```

Thirty-nine tests, up from thirty-four before the review fix.

Running instance, started from the packaged WAR:

```
DataSeeder : Seeded 5 users, 10 students, 300 attendance records
TomcatWebServer : Tomcat started on port 8080 with context path '/attendance'
GET /attendance (as faculty1) -> 200, 300 records, 10 rows, page 1 of 30
GET /attendance/actuator/health -> {"status":"UP"}
```

### Acceptance criteria coverage

| Criterion | Test |
|---|---|
| US-01 AC-1 form offers the choices | `AttendanceWebFlowIT#createFormRendersChoices` |
| US-01 AC-2 new record is DRAFT and listed | `AttendanceServiceTest#createStartsInDraft`, `AttendanceWebFlowIT#validSubmissionIsSaved` |
| US-01 AC-3 duplicate refused (BR-01) | `AttendanceServiceTest#rejectsDuplicate`, `AttendanceRecordRepositoryTest#uniqueConstraintEnforcedByDatabase` |
| US-01 AC-4 future date refused | `AttendanceServiceTest#futureDateRejected`, `AttendanceWebFlowIT#futureDateReturnsFormWithError` |
| US-01 AC-5 actor and timestamp stored | `AttendanceServiceTest#recordsActorAndTimestamp` |
| US-02 AC-1 list columns | `AttendanceWebFlowIT#listRendersSavedRecord` |
| US-02 AC-3 ordering | `AttendanceRecordRepositoryTest#defaultSortIsRecentFirst`, `#sameDateOrderedByPeriod` |
| US-03 AC-1..4 editability and ownership | `AttendanceServiceTest.Correcting` (7 tests) |
| FR-02 unauthenticated redirect | `AttendanceSecurityTest#anonymousRedirectedToLogin` |
| FR-26 student refused the create form | `AttendanceSecurityTest#studentMayNotOpenCreateForm` |
| FR-27 public health endpoint | `AttendanceSecurityTest#healthIsPublic` |
| Review finding 1 regression | `CurrentUserTest#studentWithoutRollNumberIsRefused`, `#blankRollNumberIsRefused` |

---

## 6. Evidence index

| File | Shows |
|---|---|
| [`proofs/stage-05/mvn-verify.log`](../proofs/stage-05/mvn-verify.log) | Full green build after the review fix |
| [`proofs/stage-05/verification.txt`](../proofs/stage-05/verification.txt) | Summarised test counts and runtime checks |
| [`proofs/stage-05/app-run.log`](../proofs/stage-05/app-run.log) | Startup, seeding line, context path binding |
| [`proofs/stage-05/git-evidence.log`](../proofs/stage-05/git-evidence.log) | Branch graph, commit list, merge commit, branch list |
| `proofs/stage-05/01..04-*.png` | Sign-in, list, form, detail |

---

## 7. Stage 5 Definition of Done

| # | Criterion | Status |
|---|---|---|
| D1 | Reviewed on a pull request with a comment addressed | PR #12 — one blocking finding fixed in `bd72c2f` |
| D2 | Every acceptance criterion has a passing test | Table in §5 |
| D3 | `mvn clean verify` green | 39 tests, BUILD SUCCESS |
| D5 | Jenkins pipeline green | **Not applicable** — Jenkins is installed in Stage 7 |
| D6 | Selenium gate passed | **Not applicable** — suite is built in Stage 9 |
| D8 | Stage documentation written | This document |
| D9 | Evidence captured | `proofs/stage-05/` |
| D11 | Merged and branch deleted | Merged in #12 |
| D12 | Backlog updated | US-01, US-02, US-10 marked Done |

**Outcome:** feature 1 works end to end on a feature branch, was reviewed
on a pull request, the review found a real fail-open authorisation defect
that was fixed with regression tests before merge, and the merge is
recorded as a merge commit so the review fix stays visible in history.
