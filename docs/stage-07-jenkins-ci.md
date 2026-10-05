# Stage 7 — Jenkins Installation and Continuous Integration Job

**Deliverable:** configured Jenkins job, successful build log, trigger
evidence and archived artefact.
**Issue:** [#3](https://github.com/Swaroop192005/CI-CD-Pipeline-for-a-Student-Attendance-Management/issues/3) · **Pull request:** [#15](https://github.com/Swaroop192005/CI-CD-Pipeline-for-a-Student-Attendance-Management/pull/15)

---

## 1. Installation

| Property | Value |
|---|---|
| Product | Jenkins LTS |
| Version | **2.580.1** |
| JDK | 21 (Temurin, from the official image) |
| Installation method | Official container image `jenkins/jenkins:lts-jdk21` |
| Controller URL | <http://localhost:8081/> |
| `JENKINS_HOME` | Named Docker volume `jenkins_jenkins_home` |

### Why the container image rather than the WAR

The usual route — download `jenkins.war` from `get.jenkins.io`, or add the
Debian package repository — is unavailable here. Both hosts are refused by
the lab's egress policy:

```
$ curl -sSI https://get.jenkins.io/war-stable/
curl: (56) CONNECT tunnel failed, response 403
```

The official image is served from Docker Hub, which *is* reachable, and is
a supported installation method. It turned out to be the better choice
anyway: the controller version is pinned, `JENKINS_HOME` lives in a named
volume so job configuration and build history survive a restart, and the
controller can be rebuilt from this repository.

### Build and run

```bash
./jenkins/build-controller.sh                       # builds attendance-jenkins:1.0
docker compose -f jenkins/docker-compose.yml up -d  # starts the controller
```

---

## 2. Configuration as code

Nothing about this controller was configured by clicking. Everything is in
the repository:

| File | What it defines |
|---|---|
| [`jenkins/Dockerfile`](../jenkins/Dockerfile) | Controller image; plugins baked in at build time |
| [`jenkins/plugins.txt`](../jenkins/plugins.txt) | The plugin set (pipeline, git, maven, junit, jacoco, CasC, job-dsl, …) |
| [`jenkins/casc.yaml`](../jenkins/casc.yaml) | Security realm, authorisation, tool installations and **both jobs** |
| [`jenkins/docker-compose.yml`](../jenkins/docker-compose.yml) | How it runs, with a comment on why each mount exists |
| [`jenkins/build-controller.sh`](../jenkins/build-controller.sh) | Builds the image, handling the lab proxy explicitly |

A controller whose configuration exists only inside its own
`JENKINS_HOME` cannot be reproduced, and this project is about
reproducibility. Rebuilding from these files produces the same controller
with the same two jobs.

### Plugins installed at image build time

Plugins are installed while the image is built, not at first start. The
controller then comes up in a known state: no boot-time download, and no
window in which it is running with a partial plugin set.

---

## 3. The CI job

**`attendance-portal-ci`** — a freestyle Maven job, declared as Job DSL
inside `casc.yaml`.

| Setting | Value |
|---|---|
| SCM | `https://github.com/Swaroop192005/CI-CD-Pipeline-for-a-Student-Attendance-Management.git` |
| Branch | `*/develop` |
| Trigger | SCM polling, `H/2 * * * *` |
| Build step | `mvn -B clean verify` (Maven 3.9) |
| Test publishing | `**/surefire-reports/*.xml`, `**/failsafe-reports/*.xml` |
| Artefact | `app/target/attendance.war`, fingerprinted |
| Retention | 20 builds, 10 artefacts |
| Timeout | 30 minutes |

### Trigger evidence

```xml
<hudson.triggers.SCMTrigger>
  <spec>H/2 * * * *</spec>
  <ignorePostCommitHooks>false</ignorePostCommitHooks>
</hudson.triggers.SCMTrigger>
```

Polling log:

```
Git Polling Log
Started on Oct 5, 2026, 7:26:00 AM
Using strategy: Default
[poll] Last Built Revision: Revision 0312ac1f6e26d58680f0b3addc9315021c83b31c (refs/remotes/origin/develop)
```

Polling rather than a webhook because the lab controller is not reachable
from GitHub. The job is configured to accept post-commit hooks as well,
so moving to a webhook later needs no job change.

---

## 4. Build results

| Build | Result | Duration | Why |
|---|---|---|---|
| #1 | **FAILURE** | 3.5 s | Genuine: Maven could not resolve dependencies through the proxy (see §5) |
| #2 | **SUCCESS** | 62.5 s | 80 tests passed, artefact archived |

Build #1 is kept rather than hidden. A CI job that has never gone red has
not been shown to work, and the failure it caught was real.

### Build #2 console, abridged

```
Started by user admin
Checking out Revision 0312ac1f6e26d58680f0b3addc9315021c83b31c (refs/remotes/origin/develop)
Commit message: "docs(stage-06): record MVP completion, conflict resolution and release"
[workspace] $ /opt/maven/bin/mvn -B clean verify
...
[INFO] Tests run: 73, Failures: 0, Errors: 0, Skipped: 0     (Surefire)
[INFO] Tests run:  7, Failures: 0, Errors: 0, Skipped: 0     (Failsafe)
[INFO] BUILD SUCCESS
Archiving artifacts
Recording test results
Finished: SUCCESS
```

### Test results recorded by Jenkins

```json
{ "passCount": 80, "failCount": 0, "skipCount": 0, "duration": 31.114 }
```

### Archived artefact

```
GET /job/attendance-portal-ci/2/artifact/app/target/attendance.war
  -> HTTP 200, 63,815,182 bytes  (60.86 MiB)
```

Fingerprinted, so the same artefact can be traced across jobs later in
the pipeline.

---

## 5. Problems met and solved

Four, all environmental, all recorded with their diagnosis because each
produced a symptom that pointed somewhere other than the cause.

### 5.1 `apt` cannot reach the Debian repositories

```
E: Failed to fetch http://deb.debian.org/debian/dists/trixie/InRelease  403  Forbidden
```

Blocked by the egress policy, and `apt` speaks plain HTTP which the proxy
does not serve. **Resolution:** install nothing with `apt`. Maven and the
Docker CLI are bind-mounted from the host; the base image already has
JDK 21, git and curl.

### 5.2 The plugin manager could not verify the update centre

```
Unable to retrieve JSON from https://updates.jenkins.io/update-center.json:
  PKIX path building failed: unable to find valid certification path to requested target
```

Two separate causes, which is why it took two fixes:

1. **The proxy's CA was not trusted.** Importing it looked successful but
   changed nothing, because **`keytool -importcert` reads only the first
   certificate in a PEM file** and the chain had two. The Dockerfile now
   splits the file and imports each certificate separately.
2. **The JVM does not read `HTTPS_PROXY`.** Unlike curl and git, it needs
   `-Dhttps.proxyHost` and `-Dhttps.proxyPort`. With only the environment
   variable set, the plugin manager connected directly — and the resulting
   PKIX error reads like a certificate problem when it is really a proxy
   one.

Both are needed. Either alone leaves the same error message.

### 5.3 Jenkins would not start — Configuration as Code

Two invalid keys in `casc.yaml`, each failing the whole boot:

```
UnknownAttributesException: authorizationStrategy:
  No hudson.security.AuthorizationStrategy implementation found for loggedInAuthenticationToken
UnknownAttributesException: standard:
  Invalid configuration elements for type: class hudson.security.csrf.DefaultCrumbIssuer : excludeClientIPFromCrumb
```

**Resolution:** `loggedInUsersCanDoAnything`, and drop the crumb-issuer
block — the default needs no configuration. Worth noting that CasC fails
the boot rather than ignoring an unknown key, which is the right trade:
a controller that silently dropped a security setting would be worse.

### 5.4 Maven in the job could not resolve dependencies (build #1)

Same root cause as 5.2 — the Maven JVM needed the proxy properties and a
truststore containing the proxy CA. **Resolution:** the host's own Java
truststore is mounted into the controller and `mavenOpts` points at it, so
a build that works on the developer's machine works here for the same
reason. Stated explicitly in the job definition rather than relying on the
controller's `MAVEN_OPTS`, because the Maven build step does not reliably
inherit it.

---

## 6. Evidence index

| File | Shows |
|---|---|
| [`proofs/stage-07/ci-job-evidence.log`](../proofs/stage-07/ci-job-evidence.log) | Controller version, jobs from CasC, trigger config, poll log, build history, test counts, artefact download |
| [`proofs/stage-07/build-2-console.log`](../proofs/stage-07/build-2-console.log) | Full console of the green build |
| [`01-jenkins-dashboard.png`](../proofs/stage-07/01-jenkins-dashboard.png) | Controller dashboard with both jobs |
| [`02-ci-job.png`](../proofs/stage-07/02-ci-job.png) | Job page: #1 red, #2 green, artefact, test trend |
| [`03-build-console.png`](../proofs/stage-07/03-build-console.png) | Build console output |
| [`04-test-report.png`](../proofs/stage-07/04-test-report.png) | Published test report, 80 tests |

---

## 7. Stage 7 Definition of Done

| # | Criterion | Status |
|---|---|---|
| D1 | Reviewed on a pull request | PR #15 |
| D3 | Build green | Build #2, 80 tests |
| D5 | Jenkins pipeline green on the merge commit | Yes |
| D8 | Stage documentation written | This document |
| D9 | Evidence captured | `proofs/stage-07/` |
| D11 | Merged | PR #15 merged into `develop` |
| D12 | Backlog updated | US-12 Done |

**Outcome:** Jenkins 2.580.1 installed and fully configured from code, a
Maven job connected to the repository and triggered by SCM polling, a
genuine red build followed by a green one, 80 tests published and a
60.86 MiB artefact archived and downloadable.
