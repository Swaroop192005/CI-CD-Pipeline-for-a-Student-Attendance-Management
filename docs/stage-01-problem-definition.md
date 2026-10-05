# Stage 1 — Problem Definition and Scope

**Project:** Student Attendance Management Portal
**Course theme:** CI/CD Pipeline for a Student Attendance Management Portal
**Document status:** Approved (baseline for Stages 2–15)

---

## 1. Real-Time Need

Attendance is the single most audited academic record in an Indian
undergraduate institution. It drives examination eligibility (most
universities enforce a 75% minimum attendance rule), internal-marks
calculation, scholarship disbursal, hostel discipline, and statutory
reporting to the affiliating university and AICTE/NAAC inspectors.

Despite that weight, the typical department still runs attendance on
paper registers plus a spreadsheet that one clerk consolidates at the end
of the month. The study below was carried out across three departments
(CSE, ECE, Mechanical) of a 1,200-student engineering college.

### 1.1 Observed field data (baseline measurements)

| Observation | Measured baseline | Source |
|---|---|---|
| Faculty time spent per week on attendance paperwork | 2 h 10 min per faculty | Time study, 12 faculty, 2 weeks |
| Delay between class and attendance being visible to the student | 9–14 days | Register-to-spreadsheet audit |
| Monthly consolidation effort (department clerk) | 6–8 h per department | Clerk interview |
| Transcription error rate (register → spreadsheet) | 4.1% of rows | Sample recount of 480 rows |
| Shortage notices issued *after* the last correction window closed | 23 of 61 cases | Examination-section records |
| Disputes raised at semester end ("I was present") | 38 per semester per department | Grievance register |

### 1.2 Why it matters now

1. **Eligibility risk.** A student discovers a shortage only when the
   hall-ticket is withheld. By then the only remedy is a condonation
   appeal, which the college must process manually.
2. **No audit trail.** A paper register can be corrected in pencil. There
   is no record of *who* changed a mark, *when*, or *why* — which is
   exactly what a university audit asks for.
3. **No single source of truth.** The register, the clerk's spreadsheet
   and the faculty's personal copy disagree, and each is defended as
   authoritative.
4. **Correction is unsafe.** Because correction is unlogged, there is no
   way to distinguish a legitimate "medical leave, mark excused" from a
   favour.

---

## 2. Problem Statement

> Faculty members of the college record classroom attendance on paper
> registers that are manually consolidated into departmental
> spreadsheets. The process takes 9–14 days to surface a result to the
> student, carries a measured 4.1% transcription error rate, provides no
> audit trail for corrections, and allows no review step before a mark
> becomes official. As a consequence, 38% of examination-eligibility
> shortage notices per semester are issued after the correction window
> has already closed, leaving the institution to resolve disputes
> manually and the student with no recourse.
>
> The institution requires a single, role-aware, web-based system of
> record in which a faculty member can enter attendance for a class in
> under two minutes, a Head of Department can review and approve or
> reject that entry before it becomes official, and a student can see
> their own approved attendance percentage the same day — with every
> create, update and status transition permanently attributed to a named
> user.

---

## 3. Target Users and Stakeholders

### 3.1 Primary users (operate the system daily)

| User | Volume | Primary job-to-be-done | Success looks like |
|---|---|---|---|
| **Faculty / Lecturer** | ~85 | Record attendance for each class session; correct genuine mistakes | Marks a 60-student class in < 2 min, from the classroom |
| **Head of Department (HOD)** | 3–6 | Review submitted attendance; approve or reject with a reason | Clears the review queue in one sitting; sees what changed |
| **Student** | ~1,200 | Know their current attendance % per subject, and whether they are at risk | Same-day visibility; no surprise at semester end |
| **Administrator / Exam section** | 2–3 | Maintain the student roll; pull eligibility figures | Department summary without asking the clerk |

### 3.2 Stakeholder register

| # | Stakeholder | Role in project | Interest | Influence | Engagement strategy |
|---|---|---|---|---|---|
| S1 | Head of Department (CSE) | Project sponsor, approver | Accurate, defensible records | High | Sprint review sign-off each stage |
| S2 | Faculty members | Primary users | Low data-entry effort | High | Usability walkthrough at Stage 6 |
| S3 | Students | Beneficiaries | Transparency, early warning | Medium | Read-only self-service portal |
| S4 | Examination section | Downstream consumer | Eligibility lists on time | High | Dashboard + export in scope |
| S5 | College IT / System administrator | Operator | Deployable, restartable, observable | Medium | Dockerfile, Ansible playbook, runbook |
| S6 | University audit / NAAC inspector | External assurance | Immutable audit trail | High (periodic) | Attribution on every transition |
| S7 | Academic Coordinator | Timetable & subject owner | Subject/period correctness | Medium | Subject codes modelled explicitly |
| S8 | DevOps evaluator (course examiner) | Assesses engineering process | Demonstrable CI/CD pipeline | High | Stages 7–15 evidence pack |

### 3.3 Users explicitly **out of scope** for the MVP

Parents/guardians, the university's central ERP, and biometric
attendance devices. Each is recorded in the future-enhancement plan
(Stage 15), not the MVP.

---

## 4. Pain Points → Objectives Traceability

| ID | Pain point (current state) | Objective (target state) | Verified in |
|---|---|---|---|
| P1 | 9–14 day delay before a student sees attendance | Approved attendance visible to the student same day | Stage 6, Stage 9 (journey J4) |
| P2 | 4.1% transcription error rate | No re-keying: single entry point, validated at source | Stage 5, Stage 9 (journey J1) |
| P3 | Corrections leave no audit trail | Every create/update/transition stores actor + timestamp | Stage 6, Stage 9 (journey J3) |
| P4 | Marks become official with no review | Mandatory DRAFT → SUBMITTED → APPROVED/REJECTED workflow | Stage 6, Stage 9 (journey J3) |
| P5 | Anyone with the register can change anything | Role-based authorisation on every action | Stage 6, Stage 9 (journey J5) |
| P6 | Finding one student's record means reading a register | Search by roll number, subject, date range and status | Stage 6, Stage 9 (journey J2) |
| P7 | No departmental view without the clerk | Summary dashboard with per-status and per-subject figures | Stage 6 |
| P8 | Deployment is a person copying files | Reproducible build → test → image → deploy pipeline | Stages 7–14 |
| P9 | A bad release cannot be undone quickly | Versioned releases with a tested rollback path | Stage 14 |

---

## 5. Objectives

### 5.1 Product objectives

- **O1** Provide one authoritative store for attendance records, keyed by
  (student, subject, session date, period).
- **O2** Enforce a role-based review workflow so that no mark becomes
  official without a second pair of eyes.
- **O3** Attribute every state change to a named user with a timestamp.
- **O4** Give each role a purpose-built read path: search for faculty and
  admin, self-service for students, review queue for the HOD.
- **O5** Surface a departmental summary without manual consolidation.

### 5.2 Engineering (DevOps) objectives

- **O6** Version-control everything, including infrastructure and
  pipeline definitions, in one repository.
- **O7** Build and test on every commit, with the build failing fast on a
  compilation or unit-test error.
- **O8** Gate deployment behind an automated browser-level regression
  suite: failing tests must stop the deployment.
- **O9** Ship the application as an immutable, versioned container image
  published to a registry.
- **O10** Provision the target node from a declarative, idempotent
  configuration-management playbook.
- **O11** Make rollback to the previous stable release a scripted,
  demonstrated operation rather than an improvisation.

---

## 6. Constraints

### 6.1 Technical constraints

| # | Constraint | Consequence for design |
|---|---|---|
| C1 | Course mandates a JVM stack with Maven/Gradle/Ant | Java 21 + Maven multi-module reactor |
| C2 | Deployment target must be Tomcat or Nginx | WAR packaging, deployable to Tomcat 10.1 *and* runnable standalone |
| C3 | CI server must be Jenkins | Jenkins LTS, pipeline-as-code via `Jenkinsfile` |
| C4 | UI tests must be Selenium WebDriver | Server-rendered Thymeleaf UI with stable `data-testid` hooks |
| C5 | Config management must be Puppet or Ansible | Both. Ansible primary (agentless; no master node needed in the lab); a Puppet module implementing the same specification is delivered alongside it |
| C6 | Single lab machine, 4 vCPU / 16 GB | Containers, not VMs; H2 instead of a separate RDBMS server |
| C7 | No managed cloud services available | Local Docker registry instead of a hosted one |
| C8 | Restricted outbound network in the lab | Dependencies from Maven Central and Docker Hub only; Jenkins run from its official image |

### 6.2 Organisational and data constraints

| # | Constraint | Consequence |
|---|---|---|
| C9 | Student personal data is in scope | No real student data in the repository; seeded fixtures only |
| C10 | Faculty cannot be trained for more than 30 minutes | UI must mirror the paper register's mental model |
| C11 | Academic calendar fixed; 15 tasks, one project cycle | Scope frozen at 15 tasks before Stage 4 begins |
| C12 | Classrooms have intermittent Wi-Fi | Entry must survive a page reload; no long-lived client state |
| C13 | Single developer | Branch-and-pull-request discipline simulated but genuinely enforced |

### 6.3 Assumptions

1. Students and subjects are seeded once per semester by the administrator.
2. One record represents one (student, subject, date, period) tuple.
3. Timetable management is handled outside this system.
4. The institution accepts an application-level audit trail (actor +
   timestamp on the record) rather than a separate immutable ledger.

---

## 7. Measurable Success Criteria

A criterion is accepted only when its **verification method** has been
executed and the evidence is in the repository.

| ID | Success criterion | Baseline | Target | Verification method | Stage |
|---|---|---|---|---|---|
| SC1 | Time to record attendance for one class session | 4 min (paper) + re-keying | < 2 min | Timed Selenium journey J1 | 9 |
| SC2 | Delay before an approved mark is visible to the student | 9–14 days | Same session (< 1 min) | Selenium journey J4 asserts visibility | 9 |
| SC3 | Re-keying steps between class and system of record | 2 | 0 | Architecture review: single write path | 3, 6 |
| SC4 | Share of state changes carrying actor + timestamp | 0% | 100% | Unit tests on the workflow service | 6 |
| SC5 | Unauthorised action attempts correctly refused | n/a | 100% | Selenium journey J5 + security unit tests | 6, 9 |
| SC6 | Unit/integration test pass rate on the main branch | n/a | 100% | Jenkins build log | 7, 10 |
| SC7 | Line coverage of domain + service packages | n/a | ≥ 70% | JaCoCo report archived by Jenkins | 10 |
| SC8 | Critical user journeys covered by Selenium | 0 | 5 of 5 | Failsafe report | 9, 10 |
| SC9 | A failing Selenium test stops deployment | n/a | Always | Deliberate defect run, Stage 10 | 10 |
| SC10 | Commit → running container, no manual step | n/a | Fully automated | End-to-end pipeline run, Stage 12 | 12 |
| SC11 | Re-running provisioning changes nothing | n/a | 0 changed tasks | Ansible second-run recap (`changed=0`) | 14 |
| SC12 | Rollback to previous stable release | Undefined | < 2 min, scripted | Rollback demonstration, Stage 14 | 14 |
| SC13 | Post-deployment health check | Manual page visit | Automated, pass/fail | `/actuator/health` probe in playbook | 14 |

---

## 8. Approved MVP Scope — 15-Task Freeze

### 8.1 In scope

**Functional**

| F# | Capability | Roles |
|---|---|---|
| F1 | Create an attendance record for a (student, subject, date, period) | Faculty, Admin |
| F2 | View records: paginated list and single-record detail | All (students see only their own) |
| F3 | Update an attendance record while it is editable | Faculty (own), Admin |
| F4 | Search/filter by roll number, subject, date range, attendance status, workflow status | Faculty, HOD, Admin |
| F5 | Role-based status workflow: DRAFT → SUBMITTED → APPROVED / REJECTED, with rejection reason and re-submission | Faculty submits; HOD approves/rejects |
| F6 | Summary dashboard: totals by workflow status, attendance percentage, per-subject breakdown, at-risk list | Faculty, HOD, Admin; student sees own |
| F7 | Authentication and role-based authorisation | All |
| F8 | Student roll management (seeded; admin can view) | Admin |
| F9 | Health endpoint for automated probes | Operators |

**Engineering**

| E# | Capability |
|---|---|
| E1 | Git repository with branch policy, issue/PR templates, meaningful history |
| E2 | Maven multi-module build producing a deployable WAR |
| E3 | Jenkins CI job triggered by SCM change, archiving the artefact |
| E4 | `Jenkinsfile` pipeline: checkout → build → unit test → package → Selenium gate → image → deploy |
| E5 | Selenium WebDriver suite for 5 critical journeys, with failure screenshots |
| E6 | Dockerfile, versioned image, local registry, documented container lifecycle |
| E7 | Ansible playbook provisioning the target node idempotently |
| E8 | Health check and scripted rollback to the previous stable release |

### 8.2 Explicitly out of scope for the MVP

Biometric/RFID capture, mobile applications, SMS/e-mail notification,
parent portal, timetable generation, leave-application workflow,
university-ERP integration, multi-institution tenancy, bulk spreadsheet
import, PDF report generation, Kubernetes orchestration, and
externally managed RDBMS. Each is carried into the Stage 15
future-enhancement plan with a rationale.

### 8.3 The frozen 15-task plan

| Task | Title | Primary deliverable |
|---|---|---|
| 1 | Problem definition and scope | This document |
| 2 | Agile planning and DevOps workflow | Backlog, board, sprint plan, DoD, workflow diagram |
| 3 | Requirements, architecture, technology setup | SRS summary, use-case + architecture diagrams, data model, API list, local setup |
| 4 | Git and GitHub repository initialisation | Repository, README, `.gitignore`, templates, branch policy, issues |
| 5 | Feature development with branching | Feature 1 on a branch, pull request, review, merge |
| 6 | MVP completion and Git collaboration | Full MVP, resolved merge conflict, tag, updated backlog |
| 7 | Jenkins installation and CI job | Jenkins job, build log, trigger evidence, archived artefact |
| 8 | Pipeline as code and server deployment | `Jenkinsfile`, pipeline run, deployed URL, parameterised setting |
| 9 | Selenium test design and local execution | Test plan, scripts, local report, failure-screenshot mechanism |
| 10 | Continuous testing in Jenkins | Published report, failed-pipeline evidence, fix commit, green rerun |
| 11 | Docker image and container lifecycle | Dockerfile, image details, command log, running container |
| 12 | Jenkins–Docker continuous deployment | Versioned image, registry evidence, commit-to-container run |
| 13 | Configuration management script | Config specification, Ansible playbook, first execution log |
| 14 | Automated provisioning and reliability validation | Provisioned node, idempotency proof, health check, rollback demo |
| 15 | Final end-to-end release, documentation and viva | Final repository, demo, report, screenshots, presentation, viva pack |

### 8.4 Scope approval

| Role | Name | Decision | Date |
|---|---|---|---|
| Project sponsor (HOD, CSE) | S1 | Approved — MVP scope frozen at the 15 tasks above | Stage 1 close |
| Developer / DevOps engineer | Swaroop Naik | Accepted | Stage 1 close |

Any change after this point is raised as a GitHub issue labelled
`scope-change`, assessed against the frozen plan, and recorded in
`docs/stage-15-final-report.md`.
