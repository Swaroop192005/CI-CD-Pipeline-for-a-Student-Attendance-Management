# Stage 8 — Pipeline as Code and Server Deployment

**Deliverable:** Jenkinsfile, successful pipeline run, deployed
application URL/screenshot and configuration evidence.
**Issue:** [#4](https://github.com/Swaroop192005/CI-CD-Pipeline-for-a-Student-Attendance-Management/issues/4) · **Pull request:** [#15](https://github.com/Swaroop192005/CI-CD-Pipeline-for-a-Student-Attendance-Management/pull/15)

---

## 1. The Jenkinsfile

[`Jenkinsfile`](../Jenkinsfile) — a declarative pipeline living in the
same repository as the application it builds, so a change to the build and
the change it builds are reviewed together and move through branches
together.

### Stages

| # | Stage | What it does | Why it is its own stage |
|---|---|---|---|
| 1 | Checkout | Clones, resolves the commit, derives `APP_VERSION` and the per-environment target name | Version and target are needed by every later stage |
| 2 | Build | `mvn -B -DskipTests clean compile` | A compile error should fail before any test runs |
| 3 | Unit test | `mvn -B test`, publishes Surefire | Fails in seconds on a unit regression instead of after the slower integration pass |
| 4 | Package | `mvn -B -DskipTests package`, archives the WAR | Produces the artefact that is deployed; archived and fingerprinted |
| 5 | Integration test | `mvn -B failsafe:integration-test failsafe:verify` | Slower, and only worth running once the unit tests are green |
| 6 | Deploy to Tomcat | Recreates the Tomcat container and copies the WAR in | The deployment itself |
| 7 | Verify deployment | Health check, then asserts the environment parameter took effect | A running container is not a working deployment |

The Stage 10 Selenium quality gate is inserted between 5 and 6, and the
Stage 12 image build and registry push between 5 and 6 as well — which is
why the pipeline is written with the test stages already separated.

### Parameters

| Parameter | Type | Default | Purpose |
|---|---|---|---|
| `DEPLOY_ENVIRONMENT` | choice: staging / production / local | `staging` | Environment label the deployed application reports |
| `APP_PORT` | string | `8090` | Host port the deployed Tomcat listens on |
| `RUN_SELENIUM` | boolean | `true` | Run the Stage 10 quality gate; clearing it skips the gate and therefore the deploy |

---

## 2. The parameterised setting, and why it is verified rather than assumed

`DEPLOY_ENVIRONMENT` is passed into the deployed container as an
environment variable, where Spring Boot's relaxed binding turns it into
`attendance.environment`. The application renders it in the top-bar badge
and returns it from `/actuator/info`.

The deploy stage **recreates the container** rather than only swapping the
WAR, because the label arrives as an environment variable: without
recreating, the parameter would have no effect at all while still
appearing to be applied.

The verify stage then asserts it:

```groovy
def reported = sh(script: "curl -s ${base}/actuator/info | sed -n 's/.*\"environment\":\"\\([^\"]*\\)\".*/\\1/p'",
                  returnStdout: true).trim()
if (reported != params.DEPLOY_ENVIRONMENT) {
    error("Deployed environment is '${reported}' but '${params.DEPLOY_ENVIRONMENT}' was requested. " +
          "The parameter did not reach the application.")
}
```

A deployment that is healthy but configured for the wrong environment is
exactly the failure this catches — and the kind that otherwise goes
unnoticed until someone finds production data in a staging datastore.

### A defect this found

The first version used a single fixed container name,
`attendance-tomcat`. Deploying staging therefore **removed the production
deployment**, and vice versa. The parameter looked effective — the new
deployment did report the requested environment — while silently
destroying the other one.

Fixed in `566b1db`: the container is named per environment, so the two
coexist and the parameter can be *seen* to work rather than only claimed
to.

---

## 3. Successful pipeline run

### Build #5 — `DEPLOY_ENVIRONMENT=staging`, `APP_PORT=8090`

| Stage | Status | Duration |
|---|---|---|
| Declarative: Checkout SCM | SUCCESS | 0.65 s |
| Checkout | SUCCESS | 1.11 s |
| Build | SUCCESS | 4.05 s |
| Unit test | SUCCESS | 24.11 s |
| Package | SUCCESS | 5.37 s |
| Integration test | SUCCESS | 15.19 s |
| Deploy to Tomcat | SUCCESS | 6.09 s |
| Verify deployment | SUCCESS | 22.46 s |
| **TOTAL** | **SUCCESS** | **80.6 s** |

```
Building 1.0.5-3b4a92f from HEAD (3b4a92f)
Deployment target container: attendance-tomcat-staging
Deploying 1.0.5-3b4a92f to Tomcat as 'staging' on port 8090
Health check passed after 8 attempt(s)
Deployed application reports environment: 'staging'
Parameter verified: requested 'staging', deployed 'staging'
Deployed application URL: http://localhost:8090/attendance
Pipeline succeeded. 1.0.5-3b4a92f is deployed at http://localhost:8090/attendance
Finished: SUCCESS
```

### Build #6 — `DEPLOY_ENVIRONMENT=production`, `APP_PORT=8091`

```
Deployment target container: attendance-tomcat-production
Deploying 1.0.6-3b4a92f to Tomcat as 'production' on port 8091
Health check passed after 6 attempt(s)
Deployed application reports environment: 'production'
Parameter verified: requested 'production', deployed 'production'
Deployed application URL: http://localhost:8091/attendance
```

---

## 4. Deployed application

### URLs

| Environment | URL | Health |
|---|---|---|
| staging | <http://localhost:8090/attendance> | `{"status":"UP"}` |
| production | <http://localhost:8091/attendance> | `{"status":"UP"}` |

### Both running at once, each correctly labelled

```
$ docker ps --filter name=attendance-tomcat
attendance-tomcat-production  tomcat:10.1-jdk21-temurin  0.0.0.0:8091->8080/tcp  Up
attendance-tomcat-staging     tomcat:10.1-jdk21-temurin  0.0.0.0:8090->8080/tcp  Up

$ curl -s :8090/attendance/actuator/info
{"app":{"name":"Student Attendance Management Portal","environment":"staging"}}

$ curl -s :8091/attendance/actuator/info
{"app":{"name":"Student Attendance Management Portal","environment":"production"}}
```

### Deployment target

| Property | Value |
|---|---|
| Container | Apache Tomcat 10.1 (`tomcat:10.1-jdk21-temurin`) |
| Servlet spec | Jakarta EE 10, matching Spring Boot 3.5 |
| Artefact | `attendance.war` copied into `/usr/local/tomcat/webapps/` |
| Context path | `/attendance` — from the WAR name, identical to the embedded run |
| Datastore | H2 file at `/usr/local/tomcat/data/attendance` |
| Deploy time | ~12 s from WAR copy to healthy |

The WAR is the *same artefact* that runs standalone under `java -jar`
in Stage 11, because `spring-boot-starter-tomcat` is scoped `provided`
and the Spring Boot plugin repackages it into `WEB-INF/lib-provided`.
One build output, two deployment modes.

### Screenshots

| View | File |
|---|---|
| Pipeline job with build history | [`01-pipeline-job.png`](../proofs/stage-08/01-pipeline-job.png) |
| Build #5 detail with artefact and commit | [`02-pipeline-build.png`](../proofs/stage-08/02-pipeline-build.png) |
| Deployed staging instance | [`03-deployed-staging-dashboard.png`](../proofs/stage-08/03-deployed-staging-dashboard.png) |
| Deployed production instance | [`04-deployed-production-dashboard.png`](../proofs/stage-08/04-deployed-production-dashboard.png) |

---

## 5. Evidence index

| File | Shows |
|---|---|
| [`Jenkinsfile`](../Jenkinsfile) | The pipeline definition |
| [`proofs/stage-08/pipeline-run.log`](../proofs/stage-08/pipeline-run.log) | Full console, staging run |
| [`proofs/stage-08/pipeline-run-production.log`](../proofs/stage-08/pipeline-run-production.log) | Full console, production run |
| [`proofs/stage-08/parameter-evidence.txt`](../proofs/stage-08/parameter-evidence.txt) | Both runs side by side, both instances live, stage timings, artefact download |
| [`proofs/stage-08/deployed-health.json`](../proofs/stage-08/deployed-health.json) | Health and info from the deployed instance |

---

## 6. Stage 8 Definition of Done

| # | Criterion | Status |
|---|---|---|
| D1 | Reviewed on a pull request | PR #15 |
| D3 | Build green | Builds #5 and #6 |
| D5 | Jenkins pipeline green | Yes, end to end |
| D8 | Stage documentation written | This document |
| D9 | Evidence captured | `proofs/stage-08/` |
| D10 | Deployed instance answers its health check | Both environments `UP` |
| D11 | Merged | PR #15 merged into `develop` |
| D12 | Backlog updated | US-13 Done |

**Outcome:** a declarative pipeline in the repository takes a commit
through build, unit test, package, integration test and deployment to
Tomcat 10.1, with the environment setting parameterised — and verified at
the end of the run rather than assumed.
