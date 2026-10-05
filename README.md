# Student Attendance Management Portal

A role-aware attendance system of record for an engineering college,
delivered end to end through a **Git → Jenkins → Selenium → Docker →
Ansible** continuous delivery pipeline.

The portal replaces paper attendance registers with a single
authoritative store in which a faculty member records a class session, a
Head of Department reviews and approves it, and a student sees their own
approved attendance the same day — with every state change attributed to
a named user and a timestamp.

---

## Why this project exists

Attendance decides examination eligibility, internal marks and
scholarship disbursal, yet it is usually kept on paper and consolidated
into a spreadsheet weeks later. The measured baseline in the department
studied for this project was:

| Pain point | Measured baseline |
|---|---|
| Delay before a student sees their attendance | 9–14 days |
| Transcription error rate (register → spreadsheet) | 4.1% |
| Shortage notices issued after the correction window closed | 23 of 61 |
| Faculty time on attendance paperwork | 2 h 10 min per week |
| Audit trail for a correction | None |

The full problem analysis, stakeholder register and measurable success
criteria are in [`docs/stage-01-problem-definition.md`](docs/stage-01-problem-definition.md).

---

## Features

The frozen MVP scope (Stage 1 §8). Status is kept current as the 15
project tasks complete.

| # | Capability | Roles | Requirement | Status |
|---|---|---|---|---|
| F1 | Record attendance for a (student, subject, date, period) | Faculty, Admin | FR-03 … FR-07 | Delivered — one record per student, subject, session date and period, enforced by a database constraint |
| F2 | View records — paginated list and detail view | All | FR-08, FR-09 | Delivered — 10 rows per page, most recent session first, full audit trail on the detail view |
| F3 | Update a record while it is still editable | Faculty (author), Admin | FR-10 … FR-12 | Delivered — draft and rejected records only; the original author is preserved on correction |
| F4 | Search and filter by roll number, subject, date range and status | Faculty, HOD, Admin | FR-13 … FR-15 | Delivered — five optional filters combined with AND, surviving pagination |
| F5 | Role-based status workflow `DRAFT → SUBMITTED → APPROVED / REJECTED` | Faculty submits, HOD decides | FR-16 … FR-22 | Delivered — one code path for every transition, each stamped with actor and time |
| F6 | Summary dashboard with attendance percentage and at-risk list | Faculty, HOD, Admin, Student (own) | FR-23, FR-24 | Delivered — approved records only, with a per-subject breakdown and an at-risk list |
| F7 | Authentication and role-based authorisation | All | FR-01, FR-02, FR-25, FR-26 | Delivered — BCrypt, CSRF, and rules enforced at both the URL and the service layer |
| F8 | Student roll view | Admin | FR-28 | Delivered |
| F9 | Health endpoint for automated probes | Operators | FR-27 | Delivered — public, and reached only after seeding completes |

---

## Technology stack

| Layer | Choice | Version |
|---|---|---|
| Language / runtime | Java (OpenJDK) | 21 LTS |
| Framework | Spring Boot (MVC, Data JPA, Security, Actuator) | 3.5.16 |
| View | Thymeleaf server-rendered templates | Boot-managed |
| Database | H2 — file mode at runtime, in-memory for tests | 2.x |
| Build | Apache Maven multi-module reactor | 3.9.x |
| Packaging | Executable WAR — one artefact, two deployment modes | — |
| Deployment target | Apache Tomcat (Nginx as the edge proxy) | 10.1 |
| CI/CD | Jenkins LTS with a declarative `Jenkinsfile` | `lts-jdk21` |
| Browser tests | Selenium WebDriver + Chromium/ChromeDriver 141 | 4.50.0 |
| Containers | Docker Engine + local `registry:2` | 29.x |
| Configuration management | Ansible (agentless) | core 2.19 |

Each choice is justified against a project constraint in
[`docs/stage-03-requirements-architecture.md`](docs/stage-03-requirements-architecture.md) §3.

---

## Repository layout

```
.
├── pom.xml                 Aggregator (reactor) POM
├── app/                    Spring Boot application, packaged as attendance.war
│   └── src/main/java/com/college/attendance/
│       ├── config/         Security, seeding, externalised properties
│       ├── domain/         Entities and enums
│       ├── repository/     Spring Data repositories and search specifications
│       ├── service/        AttendanceService, WorkflowService, DashboardService
│       ├── web/            Thymeleaf controllers
│       ├── api/            REST controllers under /api/v1
│       └── dto/            Request and response objects
├── selenium-tests/         Selenium WebDriver suite (Stage 9)
├── Jenkinsfile             Declarative CI/CD pipeline (Stage 8)
├── Dockerfile              Container image definition (Stage 11)
├── docker/                 Compose files and local registry helper
├── jenkins/                Job configuration and controller setup
├── ansible/                Inventory, playbooks and roles (Stage 13)
├── docs/                   Stage documentation, 1 through 15
└── proofs/                 Captured evidence per stage
```

---

## Quick start

### Prerequisites

JDK 21, Maven 3.9+, Git. Docker, Chromium + matching ChromeDriver and
Ansible are needed only for the later stages. The readiness checklist,
including how each tool was obtained in a restricted-network lab, is in
[`docs/00-environment-prerequisites.md`](docs/00-environment-prerequisites.md).

### Build and test

```bash
git clone https://github.com/Swaroop192005/CI-CD-Pipeline-for-a-Student-Attendance-Management.git
cd CI-CD-Pipeline-for-a-Student-Attendance-Management

mvn -B clean verify
```

### Run

```bash
# Option A - from source, embedded Tomcat
mvn -pl app spring-boot:run

# Option B - the packaged artefact, exactly as the container runs it
java -jar app/target/attendance.war
```

The application is then at **<http://localhost:8080/attendance>** and its
health endpoint at **<http://localhost:8080/attendance/actuator/health>**.

### Seeded accounts

Deterministic fixtures, created only when the datastore is empty. No real
student data is used anywhere in this repository.

| Username | Password | Role |
|---|---|---|
| `faculty1` | `Faculty@123` | Faculty |
| `faculty2` | `Faculty@123` | Faculty |
| `hod1` | `Hod@12345` | Head of Department |
| `admin1` | `Admin@123` | Administrator |
| `student1` | `Student@123` | Student (roll `1CS21CS001`) |

### Configuration

Every setting is overridable by environment variable, with no rebuild
(SRS NFR-08):

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8080` | Listen port |
| `SERVER_SERVLET_CONTEXT_PATH` | `/attendance` | Context path |
| `SPRING_DATASOURCE_URL` | `jdbc:h2:file:./data/attendance` | Datastore location |
| `ATTENDANCE_ENVIRONMENT` | `local` | Environment label shown in the UI banner |
| `ATTENDANCE_ELIGIBILITY_THRESHOLD` | `75` | Examination-eligibility percentage |
| `ATTENDANCE_SEED_DATA` | `true` | Seed fixtures when the datastore is empty |

---

## Documentation

| Stage | Deliverable | Document |
|---|---|---|
| 0 | Environment readiness | [`docs/00-environment-prerequisites.md`](docs/00-environment-prerequisites.md) |
| 1 | Problem definition and scope | [`docs/stage-01-problem-definition.md`](docs/stage-01-problem-definition.md) |
| 2 | Agile planning and DevOps workflow | [`docs/stage-02-agile-planning.md`](docs/stage-02-agile-planning.md) |
| 3 | Requirements, architecture, technology setup | [`docs/stage-03-requirements-architecture.md`](docs/stage-03-requirements-architecture.md) |
| 4 | Repository initialisation | `docs/stage-04-repository-initialisation.md` |
| 5 | Feature development with branching | `docs/stage-05-feature-branching.md` |
| 6 | MVP completion and Git collaboration | `docs/stage-06-mvp-collaboration.md` |
| 7 | Jenkins installation and CI job | `docs/stage-07-jenkins-ci.md` |
| 8 | Pipeline as code and server deployment | `docs/stage-08-pipeline-as-code.md` |
| 9 | Selenium test design and local execution | `docs/stage-09-selenium-tests.md` |
| 10 | Continuous testing in Jenkins | `docs/stage-10-continuous-testing.md` |
| 11 | Docker image and container lifecycle | `docs/stage-11-docker-lifecycle.md` |
| 12 | Jenkins–Docker continuous deployment | `docs/stage-12-jenkins-docker-cd.md` |
| 13 | Configuration management script | `docs/stage-13-configuration-management.md` |
| 14 | Provisioning and reliability validation | `docs/stage-14-provisioning-reliability.md` |
| 15 | Final release, documentation and viva | `docs/stage-15-final-report.md` |

Captured command logs, reports and screenshots for each stage live under
[`proofs/`](proofs/).

---

## Contributing

Branch naming rules, the commit-message convention, the pull request
process and the Definition of Done are in
[`CONTRIBUTING.md`](CONTRIBUTING.md). In short: branch from `develop` as
`feature/<short-kebab-description>`, write Conventional Commit messages,
open a pull request, get CI green, then merge and delete the branch.

---

## Project status

All fifteen project tasks are complete. The full workflow — Git commit →
Jenkins build → unit and integration tests → Selenium quality gate →
versioned Docker image → registry → container deployment → Ansible
provisioning → health check — runs end to end with no manual step.

See [`docs/stage-15-final-report.md`](docs/stage-15-final-report.md) for
the consolidated report, and [`docs/FINAL-REPORT.md`](docs/FINAL-REPORT.md)
for all fifteen task reports combined into one submission document.
