# Viva questions and answers

Anticipated questions with answers grounded in this repository, so every
claim can be shown on screen. Where a question has an uncomfortable answer,
the uncomfortable answer is here: an examiner who finds a weakness you have
already named reads it as understanding, and one who finds a weakness you
hid reads it as luck.

Figures below are from the feature branch
(`claude/upbeat-cannon-o509o5`). On `main` the test counts are 73 unit and
7 integration, because the redesign and its tests live on the branch.


## Contents

1. [The basics: what each tool is](#1-the-basics-what-each-tool-is)
2. [The technology stack](#2-the-technology-stack)
3. [The project](#3-the-project)
4. [DevOps concepts](#4-devops-concepts)
5. [The pipeline](#5-the-pipeline)
6. [Testing](#6-testing)
7. [Docker](#7-docker)
8. [The application design](#8-the-application-design)
9. [Database](#9-database)
10. [Security](#10-security)
11. [The hard questions](#11-the-hard-questions)

---

## 1. The basics: what each tool is

Examiners usually open with a definition before asking anything specific.
Each answer below is the definition first, then one line on why this
project has it.

**Q. What is containerisation?**

Packaging an application together with everything it needs to run -- its
runtime, libraries and configuration -- into one unit that runs identically
anywhere, isolated from whatever else is on the machine. It is operating
system level virtualisation: containers share the host kernel rather than
each carrying a whole operating system.

*Here:* it is what lets the same artefact run on a laptop, in the quality
gate and in production without "it worked on my machine".

**Q. What is Docker?**

The tool that builds, runs and distributes containers. You write a
`Dockerfile` describing how to assemble an image; Docker builds the image
and runs containers from it.

*Here:* two distinct jobs. It packages the portal into an image, and it
provides the lab itself -- Jenkins, the registry, both Tomcats, the two
provisioned nodes and the deployed application are all containers standing
in for machines this project does not have.

**Q. Container versus virtual machine?**

A virtual machine virtualises hardware and runs a full guest operating
system, so it is heavy, slow to start and isolated at the hardware level. A
container virtualises the operating system, sharing the host kernel, so it
starts in seconds and is far smaller, with weaker isolation.

*Here:* eight containers run comfortably on one laptop. Eight virtual
machines would not.

**Q. Image versus container?**

An image is the immutable package, built once. A container is a running
instance of an image. One image, many containers.

*Here:* the same image runs as the staging container and the production
container. Nothing is rebuilt between environments, which is what makes
"it passed in staging" mean anything.

**Q. What is a container registry?**

A server that stores and distributes images, the way a Git remote stores
commits. You `push` an image to it and `pull` it elsewhere.

*Here:* a local `registry:2` container. The pipeline pushes the image it
built, and the deploy stages pull from it, so deployment uses a published
artefact rather than something sitting in a build directory.

**Q. What is Jenkins?**

An automation server. It watches a repository, and when something changes
it runs a defined sequence of steps -- build, test, package, deploy -- and
reports the result.

*Here:* it is what turns a collection of scripts into a pipeline with a
verdict. Eleven stages, defined in a `Jenkinsfile` that lives in the
repository.

**Q. What is a Jenkins pipeline, and what is a declarative pipeline?**

A pipeline is the sequence of stages a change passes through. Declarative
is the structured syntax -- `pipeline { stages { stage('Build') { ... } } }`
-- as opposed to scripted pipelines, which are general Groovy. Declarative
is more constrained and therefore more readable.

*Here:* declarative, so the stages are legible to someone who has not
written Groovy.

**Q. What is Selenium?**

A tool that drives a real browser programmatically: it opens pages, clicks,
types and reads what is on screen, so a test can do what a user does.

*Here:* 19 journeys that sign in as each role and complete real tasks --
open a session, mark attendance, strike a student, raise and escalate a
case. They catch what unit tests cannot: whether the page a human sees
actually works.

**Q. What is the Page Object pattern?**

Each page gets a class exposing what the page can do, so tests say
`openRoster().strikeFirst(reason)` instead of repeating CSS selectors. When
the markup changes, one class changes rather than every test.

*Here:* `SessionPage`, `CasePage`, `AttendanceListPage` and others under
`e2e/pages/`.

**Q. What is Maven?**

A build tool for Java. It compiles, runs tests, resolves dependencies and
packages the result, driven by `pom.xml`.

*Here:* a two-module reactor, `app` and `selenium-tests`, so one command
builds and verifies everything and the pipeline does not need bespoke build
steps.

**Q. What is Git, and what is a branching strategy?**

Git is distributed version control: every change is a commit, and branches
let work proceed in parallel. A branching strategy is the agreed convention
for how those branches are used.

*Here:* `feature/*` branches for new work, `bugfix/*` for fixes, merged
into `develop`, with `main` as the released line, plus version tags.

**Q. What is Ansible?**

A configuration management tool. You declare the state a machine should be
in, in YAML, and it makes the machine match. It is agentless: it connects
over SSH and needs nothing installed on the target.

*Here:* four roles -- `common`, `java`, `nginx`, `attendance_app` --
provision a node from bare to serving the application.

**Q. What is Puppet, and how does it differ from Ansible?**

Also configuration management, but traditionally agent-based and
pull-driven: nodes fetch their catalogue from a master and apply it. Its
language is declarative resource types rather than a task list.

*Here:* a masterless module applied with `puppet apply`, with manifests for
install, config, deploy and service. The project demonstrates both tools
because they are the two dominant ones in this space.

**Q. What is Apache Tomcat?**

A servlet container: a Java web server that runs WAR files.

*Here:* the pipeline deploys to Tomcat as well as running the application
standalone, to show the same artefact deploying to a conventional Java
application server.

**Q. What is Spring Boot?**

A framework that makes a Spring application self-contained and
convention-driven: it auto-configures the usual wiring and can embed its
own web server, so the application runs with `java -jar`.

*Here:* it is why one artefact can either run standalone or deploy to
Tomcat, which is what lets the pipeline demonstrate both.

**Q. What is an artefact?**

The output of a build that gets stored and deployed -- here the WAR file
and the Docker image containing it.

**Q. What is a WAR file?**

Web Application Archive: a packaged Java web application that a servlet
container can deploy.

**Q. What is H2?**

An embedded SQL database written in Java. It runs inside the application
process with no separate server.

*Here:* it keeps the lab reproducible on one machine. Its cost is named in
the database section below.

**Q. What is SCM polling, and how does it differ from a webhook?**

Polling means Jenkins asks the repository periodically whether anything
changed. A webhook means the repository tells Jenkins the moment it does.
Webhooks are immediate and cheaper; polling needs no inbound network route.

*Here:* polling every two minutes, because this controller runs on a laptop
and GitHub cannot reach it.

**Q. What is Configuration as Code?**

Defining a tool's own configuration in version-controlled files rather than
through its user interface, so it can be reviewed, diffed and rebuilt.

*Here:* `jenkins/casc.yaml` plus Job DSL define the Jenkins controller and
its jobs, so Jenkins can be destroyed and recreated identically instead of
being a machine nobody dares touch.

---

## 2. The technology stack

Every version below is the one actually resolved in the build, not a
version this document hopes for. Check with `mvn dependency:tree` or by
listing the WAR.

### Application

| Layer | Choice | Version | Why this one |
|---|---|---|---|
| Language | Java | 21 | Current LTS: records, pattern matching, virtual threads available |
| Framework | Spring Boot | 3.5.16 | One self-contained artefact that runs standalone or deploys to Tomcat |
| Web MVC | Spring WebMVC | 6.2.19 | Server-rendered controllers; no separate front-end build |
| Security | Spring Security | 6.5.11 | Role rules, BCrypt, CSRF, and built-in WebAuthn support |
| Templates | Thymeleaf | 3.1.5 | Natural templates: a page opens in a browser as plain HTML |
| Persistence | Spring Data JPA / Hibernate | 6.6.53 | Repository interfaces instead of hand-written JDBC |
| Database | H2 | 2.3.232 | Embedded, so the lab needs no database server |
| Passkeys | webauthn4j | 0.30.3 | The cryptography Spring Security delegates to |

### Build and test

| Purpose | Choice | Version |
|---|---|---|
| Build | Maven, two-module reactor | 3.9 |
| Unit tests | JUnit 5 with AssertJ and Mockito | Boot-managed |
| Coverage | JaCoCo | 0.8.13 |
| Browser tests | Selenium WebDriver | 4.50.0 |
| Test split | Surefire (unit) / Failsafe (integration, journeys) | Boot-managed |

### Infrastructure

| Purpose | Choice |
|---|---|
| CI/CD | Jenkins LTS (`jenkins/jenkins:lts-jdk21`), declarative pipeline, Configuration as Code, Job DSL |
| Containers | Docker, base `eclipse-temurin:21-jre-jammy` |
| Registry | `registry:2`, local |
| Browser grid | `selenium/standalone-chromium` |
| App server | Apache Tomcat, as a deployment target |
| Configuration management | Ansible (4 roles) and Puppet (masterless module) |
| Version control | Git and GitHub |

**Q. Why Java and Spring Boot rather than Node or Python?**

Spring Boot produces one self-contained artefact that can either run with
`java -jar` or deploy to Tomcat as a WAR. That single property is what lets
the pipeline demonstrate both container deployment and conventional
application-server deployment from the same build, without a second
toolchain.

**Q. Why Thymeleaf rather than React?**

A JavaScript front end would add a second build, a second set of
dependencies and a second deployable, none of which teaches anything about
CI/CD. Thymeleaf keeps the deployable count at one. Thymeleaf templates are
also valid HTML, so a page can be opened directly in a browser.

**Q. Why H2 rather than MySQL or PostgreSQL?**

It is embedded, so the whole lab runs on one machine with nothing to
install and nothing to start. It speaks standard SQL and JDBC, so moving to
PostgreSQL would be a configuration change, not a code change. The cost is
real and named in the database section: with `ddl-auto` and no migration
tool, schema changes are unmanaged.

**Q. Why JUnit 5 and AssertJ?**

JUnit 5 for nested test classes and display names, which let a test file
read as a specification. AssertJ because its failure messages name the
actual and expected values, which matters most on the day a test fails and
you were not expecting it to.

**Q. Why Selenium rather than Cypress or Playwright?**

Selenium is the W3C WebDriver standard and the tool the syllabus names. Its
remote grid model also suits this pipeline: the Jenkins controller has no
browser, so journeys run against a `selenium/standalone-chromium` container
over the network, with identical test code locally and in CI.

**Q. Why Maven rather than Gradle?**

Convention over configuration, and a declarative `pom.xml` an examiner can
read without knowing Groovy or Kotlin. The multi-module reactor also gives
a natural place to separate the application from its browser tests.

**Q. What is the Surefire and Failsafe split, and why does it matter?**

Surefire runs unit tests during `test`; Failsafe runs integration tests and
journeys during `verify`, after packaging. The split is what lets the
pipeline run fast unit tests early and expensive browser tests later,
against a real deployed artefact rather than a classpath.

---

## 3. The project

**Q. What does the system do, in one sentence?**

It records class attendance, lets students mark themselves present during a
session the faculty opens, and escalates the few students whose attendance
has fallen far enough to need a conversation.

**Q. What is the technology stack?**

Java 21, Spring Boot 3.5.16, Spring Security, Thymeleaf server-rendered
pages, Spring Data JPA over H2. Built with Maven as a two-module reactor:
`app` and `selenium-tests`.

**Q. Why server-rendered pages rather than a REST API with a JavaScript
front end?**

The project's subject is the delivery pipeline, not the front end. One
deployable artefact keeps the pipeline honest and simple: there is a single
thing to build, test, package and deploy. A separate front end would double
the build and deploy surface without demonstrating anything new about
CI/CD.

**Q. What are the roles?**

`FACULTY` opens and closes attendance sessions, corrects records, and
raises queries in its own attendance band. `HOD` owns the critical band and
decides escalated cases, including excusing sessions. `ADMIN` manages the
student roll. `STUDENT` marks their own attendance and answers cases about
themselves.

---

## 4. DevOps concepts

**Q. What is DevOps, in your own words?**

Replacing "I think it works" with evidence at every step, and automating
the steps so the evidence is produced whether or not anyone remembers to
ask for it.

**Q. What is the difference between Continuous Integration, Continuous
Delivery and Continuous Deployment?**

Integration means every change is merged and verified often, so breakage is
found in minutes. Delivery means every verified change is *ready* to
release, with a human choosing when. Deployment means it releases itself
with no human gate. This project implements Continuous Integration and
Continuous Delivery: the pipeline builds, tests and deploys to staging and
production environments, but a person triggers it.

**Q. Why both Docker and configuration management, when they overlap?**

They sit at different layers. Puppet and Ansible configure the host: the
container runtime, nginx, system users, log rotation. Docker packages the
application so the host no longer has to know anything about Java. Jenkins
decides when that package ships. The honest answer is that containers did
take over much of what configuration management used to do for application
deployment; what remains for Puppet is the machine underneath.

**Q. Why Ansible *and* Puppet?**

They are the two dominant tools and the project demonstrates both. Ansible
is agentless and push-based over SSH; the Puppet setup here is masterless,
applied with `puppet apply`. Either alone would have been enough
functionally.

**Q. What is idempotence and why does it matter?**

An operation that produces the same result however many times it runs. You
do not tell Ansible "install Java"; you declare "Java 21 is present". A
first run installs it, a second changes nothing, and a run against a node
somebody has altered corrects the drift. Without it, re-running a playbook
is a risk rather than a routine, so nobody re-runs it, so the servers drift.

---

## 5. The pipeline

**Q. Walk me through the pipeline.**

Eleven stages in `Jenkinsfile`: Checkout, Build, Unit test, Package,
Integration test, Quality gate (Selenium), Build and publish image, Deploy
container, Deploy to Tomcat, Verify deployment, Post actions.

**Q. Why is the Selenium gate before the image build and not after the
deploy?**

Because that ordering is what makes it a gate rather than a report. If the
journeys fail, no image is published and nothing is deployed. A test that
runs after deployment can only tell you what you have already inflicted on
users.

**Q. What exactly does the quality gate test against?**

A throwaway container started from the WAR that was just packaged — the
same artefact that gets deployed if the gate passes. Testing a different
build from the one deployed would make the gate decorative.

**Q. How is the pipeline configured?**

As code, two ways. The pipeline itself is a `Jenkinsfile` in the
repository, so it is versioned with the code it builds. Jenkins itself is
defined by `jenkins/casc.yaml` with Job DSL, so the controller can be
destroyed and rebuilt identically rather than clicked together.

**Q. How are artefacts versioned?**

`1.0.<build>-<commit>`, for example `1.0.19-6be7e00`: the build number
makes it unique, the commit makes it traceable. Images are pushed to a
local `registry:2` under that tag and `:latest`. Never only `:latest` — a
tag that moves cannot be rolled back to, which would make rollback
impossible.

**Q. How do you know a deployment worked?**

The Verify stage polls `/actuator/health` after deploying. Deploying and
working are different claims; without the check, the pipeline reports
success for a container that started and immediately crashed.

**Q. What happens if a deploy is bad?**

`ansible/rollback.yml` redeploys the previous versioned image. This is only
possible because tags are immutable.

---

## 6. Testing

**Q. What does your test suite cover?**

103 unit tests, 7 integration tests and 19 Selenium journeys. Unit tests
prove the business rules; integration tests prove the web layer and
persistence together; the journeys prove a real browser can complete a real
task.

**Q. Why all three levels? Isn't that duplication?**

They fail for different reasons, and each catches what the others cannot.
A concrete example from this project: the live roster page passed every
unit test while rendering a blank heading and silently hiding its own
controls, because the template read a property that did not exist.
Thymeleaf treats a missing property as false, so `th:if` hid the "Stop
taking attendance" and strike buttons. No unit test could have seen it; a
browser journey sees it immediately.

**Q. Give an example of a bug the tests actually caught.**

Three, all found by running tests rather than by reading code. `strike()`
told the user on screen that an override was "logged against your name" but
recorded only a remark naming the role, not the person. An escalated case
disappeared from the dashboard's open-case count, because the count listed
statuses by hand and `ESCALATED` was added later. And one Selenium journey
accepted a case with a year-wide excuse window, converting every absence of
that student to excused and silently breaking another journey's
assertions.

**Q. What is a quality gate?**

An automated check that can stop the pipeline. The distinction from a plain
test run is authority: a gate's failure prevents the next stage.

---

## 7. Docker

**Q. What is in your Dockerfile and why?**

Base `eclipse-temurin:21-jre-jammy` — a JRE, not a JDK, because the build
has already happened and shipping a compiler widens the attack surface. A
non-root `USER attendance`, so a compromise inside the container is not
root. OCI labels for traceability. A `HEALTHCHECK`, so the container
reports its own health rather than merely being "running".

**Q. Image versus container?**

An image is the immutable package; a container is a running instance of it.
The same image runs as the staging container and the production container,
which is the point: nothing is rebuilt between environments.

**Q. Why a private registry rather than Docker Hub?**

The lab runs offline on one machine, and a local `registry:2` demonstrates
the same push-and-pull mechanics without an external account or network
dependency.

---

## 8. The application design

**Q. Why did you replace per-record approval with shortage cases?**

Per-record approval produced a queue that grew with class size and told
nobody anything: a head of department clicking approve four hundred times
is not reviewing anything, and the approval is a proxy for scrutiny rather
than scrutiny. Now every record counts immediately, and attention goes to
the few students whose attendance is actually a problem.

**Q. How are the two case tiers divided?**

Faculty own students below the 75% eligibility threshold but above the 35%
shortage threshold. The head of department owns everything below 35%, plus
anything faculty escalate. The bands do not overlap, so a student is never
answering the same question to two people who cannot see each other's case.

**Q. Why can faculty close a case but not excuse sessions?**

Excusing changes the percentage that the eligibility decision rests on.
Faculty can be satisfied by an explanation; altering the figure stays with
the head of department.

**Q. What stops a student marking attendance from outside the classroom?**

Nothing a server can prove, and that is the honest answer. No server-side
check can establish that a body is in a room. What the system provides is a
live roster: faculty see each mark arrive and can strike anyone who is not
there, with the override recorded against their name. The control is human
judgement, supported rather than replaced.

**Q. What does the passkey actually prove, then?**

That the enrolled device is present and that a private key held in its
secure hardware signed a fresh challenge. It proves possession of a
registered device, not the identity of a body in a room. On one laptop
with one person's fingerprint enrolled it cannot distinguish students at
all; in a real deployment each student has their own device.

**Q. What is BR-03 and why does it matter?**

An excused session counts neither as attended nor towards the total. It
leaves both sides of the fraction. If it counted in the denominator only,
excusing an absence would still damage the percentage, which would make
accepting an explanation meaningless.

---

## 9. Database

**Q. Which database and why?**

H2 in file mode. It needs no separate server, which keeps the lab
reproducible on one machine, and it speaks standard SQL and JDBC, so the
application code would not change if it were pointed at PostgreSQL.

**Q. How is the schema managed?**

By Hibernate's `ddl-auto: update`, and this is the project's clearest
weakness. There is no Flyway or Liquibase, so there is no migration
history, no review of schema changes, and no safe path for a change that
existing rows violate. It bit us for real: adding a NOT NULL column to a
table with existing rows cannot be done this way, and the only remedy was
to delete the database. In production that is data loss.

**Q. How would you fix it?**

Flyway, with each change as a numbered, reviewed migration, and
`ddl-auto: validate` so the application refuses to start against a schema
it does not expect.

---

## 10. Security

**Q. How is authorisation enforced?**

In two layers. URL rules in `SecurityConfig` are the outer gate. Every
service that changes data re-checks the acting user's role and ownership,
because a URL pattern cannot express "this case, in this band" — only the
row knows that. Neither layer is trusted alone.

**Q. Give an example where the URL rule is not enough.**

Deciding a shortage case. Both faculty and the head of department may POST
a decision, so the URL rule lets both through; which tier a given case
belongs to is a property of that row. The refusal therefore lives in
`ShortageCaseService`, and there is a test that posts a decision onto the
other tier's case and asserts it is refused.

**Q. How are passwords stored?**

BCrypt hashes. No plaintext password is stored anywhere.

**Q. How does a student's scope get enforced?**

Their roll number comes from their account in the security context, never
from the request. On the weekly and monthly page the `rollNumber`
parameter is ignored for students rather than validated, because there is
no case in which honouring it would be correct.

---

## 11. The hard questions

**Q. What is wrong with this project?**

Four things worth naming. No database migrations, as above. Not truly
immutable infrastructure: containers are replaced but the H2 file persists
in a volume. No observability beyond a health endpoint — nothing reports
slow, or failing for a fraction of users; no metrics, no log aggregation,
no alerting. And a single node, so no load balancing, no blue-green, and a
window during deployment where the service is down.

**Q. Your pipeline was green while testing the wrong branch. How did that
happen?**

The job was pinned to `develop` while the work was on a feature branch, so
it kept building the pre-redesign application. It reported 100 passing
tests, and two of those test classes had been deleted weeks earlier. It was
caught by noticing the number did not match the local count. The fix was to
point the job at the branch under development; the lesson is that a green
build is only evidence if you know what it built.

**Q. If you had to do it again, what would you change first?**

Add Flyway before writing the second feature. Every later schema change,
including the one that forced a database deletion, would then have been a
reviewed migration.

**Q. What did you learn?**

That most of my own mistakes were checks that measured a proxy instead of
the thing itself. A script that reported "no build history" while showing
twenty-two builds. A status line that stated a test count typed in by hand
and wrong for weeks. A trigger script that fetched a CSRF crumb, validated
it, and never sent it — then blamed the resulting refusal on something
else. In each case something looked verified and was not, which is exactly
what automated testing exists to prevent.
