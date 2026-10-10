# Presentation and Viva Guide

How to run this project in front of an examiner, what to show, in what
order, and what you will be asked. The last two sections are written from
the examiner's side of the table rather than yours.

---

## 0. Read this first

**The whole lab now runs on your laptop, Jenkins included.** That was not
true when this guide was first written — the original runs happened in a
cloud container — so if you remember being told to present the pipeline
from logs, that advice is out of date. Every component has since been made
to work on macOS and has been verified there: the portal, the registry,
staging and production containers, two Tomcat deployments, the Ansible
node, the Puppet node, and a Jenkins controller whose build history
contains a fully green eleven-stage run.

That changes what you should show. A live controller with real build
history is strictly better evidence than a log file, because a log file
could have been written by anyone.

| | What you show | Cost | Risk |
|---|---|---|---|
| **A — Live app only** | The portal on your Mac, the pipeline from committed logs | ~5 min | Low, but you are now underselling the project |
| **B — Live app + live Jenkins** | Everything above, plus the controller at `:8081` with its green build #5 and the red build from the gate demonstration | ~15 min the night before, ~5 min on the day | Low. It has worked. |

**Take option B.** Start everything the night before, confirm it with
`scripts/demo-status.sh`, and leave it running. The one thing you should
still present from evidence is the **blocked** build — see §2.3, because
making the gate fail on demand is not something to attempt live.

If anything is down on the morning and you cannot fix it in five minutes,
fall back to option A without hesitation. §5 covers that.

---

## 1. Pre-flight

### The night before

```bash
cd ~/Desktop/CI-CD-Pipeline-for-a-Student-Attendance-Management
git pull origin main

java -version          # must be 21 or newer
mvn -v

bash scripts/start-demo.sh --tier full    # app, containers, Tomcat, registry
bash scripts/start-nodes.sh               # the Ansible and Puppet nodes
bash jenkins/start-local.sh               # the Jenkins controller
bash scripts/demo-status.sh               # everything, in one table
```

Then open `http://localhost:8080/attendance/login`, sign in as
`admin1` / `Admin@123`, and click through every screen you plan to show.
Doing this once the night before is the single highest-value thing in this
guide: it is when you discover a wrong Java version, not during the viva.

**Leave all of it running overnight.** Nothing here needs to be restarted,
and restarting is where things break. If your Mac sleeps, the containers
resume with it.

### Thirty minutes before

```bash
cd ~/Desktop/CI-CD-Pipeline-for-a-Student-Attendance-Management
bash scripts/demo-status.sh
```

Every row should read **UP**. If one does not, §5 tells you what to do.

Then trigger one pipeline run, so the top of the build history is from
today: Jenkins → **attendance-portal-pipeline** → **Build with
Parameters** → **Build**. It takes about two and a half minutes. Let it
finish before anyone walks in — a build in progress is not what you want
on screen.

When it goes green, save the log into the repository, because the
controller is a container and the log dies with it:

```bash
bash scripts/capture-pipeline-proof.sh
```

Have these tabs open:

1. `http://localhost:8080/attendance/login` — the portal
2. `http://localhost:8081/job/attendance-portal-pipeline/` — the Stage View
3. `http://localhost:8100/attendance/login` — the container Jenkins deployed
4. The GitHub repository, on the **Tags** page
5. `docs/FINAL-REPORT.md` on GitHub
6. `proofs/stage-10/failed-pipeline.log` on GitHub — the gate blocking a deploy
7. A terminal in the project directory

---

## 2. The demonstration — 12 minutes

Timings assume a 15-minute slot. Cut §2.5 first if you are short.

### 2.1 — Frame the problem (1 min, no screen)

> Attendance at our department is taken on paper and keyed in at the end
> of term. A student finds out they are short of the 75% needed to sit the
> exam when the hall ticket is withheld — too late to do anything. The
> measured baseline is 9 to 14 days from a class being taught to that fact
> being visible. The target was the same session.

Do not open with the technology. Open with the problem; the fifteen stages
are how you solved it.

### 2.2 — The application (4 min)

Sign in as **`hod1` / `Hod@12345`**.

> **Read the numbers off your own screen, do not memorise them.** The
> seeder generates sessions relative to today's date, so the totals differ
> between instances and between days. What is stable is the *relationship*
> between them, which is the thing worth explaining.

| Show | Say |
|---|---|
| Dashboard | "Overall attendance is *[read it]*%, from *[counted]* counted sessions — note that is fewer than the *[approved]* approved records, because excused absences are removed from both sides of the percentage. And only approved records count at all, so a draft is never published as a fact about a student." |
| At-risk list | "This is the whole point. A student below the 75% threshold, surfaced while there is still time to act." |
| Review queue | "*[n]* records waiting. Faculty record, I approve. A student never sees a draft." |
| Approve one | "Every transition is stamped with who did it and when." |
| Sign out, in as `student1` / `Student@123` | "Same data, their records only, approved only. Authorisation is enforced at the URL and again at the service layer." |

The two business rules behind those numbers, if pressed:

- **BR-03** — excused absences are removed from the numerator *and* the
  denominator, so an excused student is neither rewarded nor punished.
- **BR-04** — only `APPROVED` records count toward the percentage, so an
  unreviewed draft cannot affect a student's standing.

The role switch is the strongest 30 seconds in the demo. Make sure you do it.

### 2.3 — The pipeline (3 min, live)

Open `http://localhost:8081/job/attendance-portal-pipeline/` and show the
**Stage View**: five builds, eleven columns, build #5 green the whole way
across.

> Eleven stages, 153 seconds, no manual step. Checkout, build, unit tests,
> package, integration tests, the Selenium gate, image build and push,
> container deploy, Tomcat deploy, and a verification stage that checks the
> deployed instance reports the environment the pipeline was asked for.

Click into build #5 → **Console Output** and scroll to two places.

The gate:

```
Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
Selenium quality gate passed.
```

And the verification stage, which is the one most worth pausing on:

```
Parameter verified: requested 'staging', deployed 'staging'
```

> That stage does not just check for a 200. It reads `/actuator/info` off
> the deployed instance, extracts the environment it reports, and compares
> it to the parameter the build was given. A container that is healthy but
> configured for the wrong environment fails there. That is the difference
> between a health check and a deployment verification.

Then point at the containers on `:8100` and `:8090`:

> Those two deployments were not placed there by a script I ran. Jenkins
> put them there, in that build, from the artefact that build produced.

**Then the blocked build — this part stays on evidence**, because making
the gate fail on demand is not something to attempt in front of an
examiner. Open `proofs/stage-10/failed-pipeline.log`, and have
`proofs/stage-10/01-failed-build.png` ready beside it.

> This is the part that matters most. A green pipeline only proves it
> can pass. I introduced a deliberate regression and build #10 went red at
> the Selenium gate — and the deploy stages never executed.

Quote the log rather than paraphrasing it; the last lines say it outright:

```
Stage "Deploy to Tomcat" skipped due to earlier failure(s)
Stage "Verify deployment" skipped due to earlier failure(s)
ERROR: Selenium quality gate failed. The deployment stages will not run.
Finished: FAILURE
```

> A gate that has never been shown to block is not a gate.

One detail worth knowing in case an examiner greps the log: there *is* a
single `docker run` line in it. That is the Selenium container the gate
itself starts, not a deployment. The deploy stages are named explicitly as
skipped, which is the stronger evidence anyway.

### 2.4 — Configuration management (2 min)

**Live, if `scripts/start-nodes.sh` worked on your machine.** This is the
better version of this section, because idempotency is a claim about what
happens when you run something *again* — and running it again in front of
someone is the whole argument.

Have the node already provisioned before you start. Then run the playbook
a second time, live:

```bash
cd ansible
ansible-playbook -i inventory.ini site.yml -e proxy_url='' \
    -e app_artifact_source=../app/target/attendance.war
```

> This node is already provisioned. Watch the recap: thirty-odd tasks, and
> **changed=0**. Nothing moved, because the playbook describes a desired
> state rather than a list of actions — which is what makes it safe to
> re-run against a node in an unknown condition.

Then show `http://localhost:8200/attendance/login` — the same portal, on a
node that started as a bare Ubuntu container with no JRE, no service
account and no configuration.

**From evidence, if the node tier did not run.** Open
`proofs/stage-13/puppet/03-puppet-apply-run2-idempotent.log`.

### 2.4b — Configuration as code, in Jenkins itself (1 min)

You have already used the controller in §2.3, so this is one extra click
rather than a new section. Go to **Manage Jenkins → Configuration as
Code**.

> Nobody clicked through a wizard to create that job. The controller, both
> jobs, the tool definitions and the credentials come from
> `jenkins/casc.yaml` and a Job DSL script in the repository. If this
> controller is deleted, `bash jenkins/start-local.sh` rebuilds it
> identically — which is the same argument the Ansible and Puppet sections
> make, applied to the CI server instead of the application host.

That is the Stage 7 and 8 answer: the CI server is itself under version
control.

> Stage 13 asked for Ansible *or* Puppet. I did both, against the same
> written specification. Second run of the Puppet manifest: exit code 0,
> zero changes. That is idempotency as a checkable fact, not a claim.

Then `08-rendered-templates-comparison.log`:

> All four files the two tools manage are byte-identical once you strip
> each tool's own banner comment. Two tools with opposite execution models
> producing the same node is what tells you the specification was real.

### 2.5 — Rollback (2 min, from evidence) — *cut this if short*

Open `proofs/stage-14/rollback-run.log`.

> I deployed a deliberately corrupt artefact, the health check caught it,
> and the rollback playbook returned the node to the previous release in
> three tasks and under a minute. The artefact was genuinely broken — a
> truncated WAR — not a simulated failure.

---

## 3. Evaluator's assessment

Written as if I were marking this.

### What scores well

**The negative evidence.** Most student projects show that something
works. This one shows the quality gate *failing* and blocking a deploy,
and a rollback against a genuinely corrupt artefact. That is the single
clearest signal that the pipeline is real.

**Idempotency proven, not asserted.** `changed=0` from Ansible and
`exit 0` from Puppet, with the semantics of the exit code explained in the
log itself.

**Two implementations compared.** Doing Stage 13 twice and diffing the
output is beyond what was asked, and the byte-identical result is a
genuinely strong claim about the specification.

**Defects documented honestly.** The reverse-proxy bug — `Host $host`
dropping the port, which broke every redirect while every curl health
check still passed — is written up with cause and detection gap, not
quietly fixed. Examiners notice this.

**Traceability.** Requirements through to tests through to evidence, with
13 success criteria measured against a stated baseline.

**Portability demonstrated, not assumed.** The pipeline was written on
Linux and then run unmodified on macOS, Apple Silicon and Docker Desktop
— a different OS, CPU architecture and container runtime — with the same
eleven stages and the same 100 tests passing. One `Jenkinsfile` reads two
environment variables and works in both. §1a of the final report lists
the five assumptions that had to be fixed to get there, and each one is a
better story than a green log: every one of them was a check that measured
a *proxy* for success instead of the thing itself, and so reported success
while the real condition was false.

### Where it is weak — expect these

| # | Weakness | Honest answer |
|---|---|---|
| 0 | **The domain model is not how attendance really works** — a Head of Department approving every class session | The one most likely to be raised by an examiner who knows colleges, and the one to concede fastest. "In practice a faculty mark is official on save; sign-off attaches to post-lock corrections or to the consolidated pre-exam statement. I chose a per-session workflow because I needed three roles with genuinely different authority over one record for the Selenium journeys to assert anything across roles. It's limitation A7, and F15 is the realistic design." Then defend BR-04, which survives either way: a mistaken record should never become a fact about a student's eligibility |
| 1 | **`ddl-auto: update`, no migrations** | "The schema is managed by Hibernate, which is fine for a single-node demo and wrong for production. Flyway or Liquibase with versioned migrations is the correct answer, and it is the first thing I would add." Do not defend this one. |
| 2 | **Coverage measured but not enforced** | JaCoCo has `prepare-agent` only — no `check` goal, no threshold. "Coverage is reported but not gated. The gate is the Selenium suite. A coverage minimum in the `check` goal would make it a real quality gate too." |
| 3 | **No HTTPS anywhere** | "Everything is plain HTTP. nginx terminating TLS with a real certificate is the production answer; in the lab there is no certificate authority to issue one." |
| 4 | **Deploy has downtime** | The pipeline does `docker rm -f` then `docker run`. "It is a replace, so there is a gap of a few seconds. Blue-green with two containers behind nginx, or a rolling update, removes it." |
| 5 | **H2, not a real database** | "H2 in file mode. Postgres is the production choice. H2 was chosen so the whole stack runs on one node with no external dependency." |
| 6 | **The 'target node' is a container** | "It runs systemd and is provisioned over SSH exactly as a VM would be, but it is a container, so this does not prove anything about real hardware or cloud instances." |
| 7 | **Jenkins and the deploy target share a host** | No real separation between CI and the environment it deploys to. |
| 8 | **Demo credentials hardcoded** | Seeded users with fixed passwords in `DataSeeder`. Fine for a demo, and worth saying out loud before they ask. |
| 9 | **One browser** | Selenium runs against Chromium only. No cross-browser matrix. |
| 10 | **No monitoring** | A health endpoint is not observability. Prometheus and alerting would be the next layer. |

Naming two or three of these yourself, before you are asked, reads as
judgement. Waiting to be caught on them reads as not knowing. If you only
raise one, raise **#0** — it is the one a domain-aware examiner will reach
for, and conceding it immediately turns the weakest part of the project
into evidence that you understood the trade-off you were making.

### Likely mark

Strong. The breadth is complete — all fifteen stages with evidence — and
the depth in Stages 10, 13 and 14 is beyond the brief. The weaknesses are
all *scope* decisions appropriate to a single-node lab rather than
mistakes, with the exception of `ddl-auto` and the unenforced coverage
threshold, which are real gaps you should concede immediately.

---

## 4. Questions you will be asked

**"Did you write this yourself?"**
Answer honestly and specifically. Vague answers sound worse than the
truth. Talk about decisions you can defend: why the WAR is executable *and*
deployable to Tomcat, why the seeder moved from `ApplicationRunner` to
`@PostConstruct`, why the gate container needed `docker cp` instead of a
volume mount. Specific technical reasoning is what distinguishes
understanding from ownership.

**"Why both Ansible and Puppet?"**
"The stage allowed either. Doing both tested whether my Stage 13 document
was a specification or just a description of what the playbook happened to
do. It was a specification — the two tools produce byte-identical files.
And building the second one found a bug in the first: both nginx templates
dropped the port from the Host header, which broke every redirect, and
every curl health check passed anyway because the health endpoint never
redirects."

**"What is idempotency and why does it matter?"**
"Running it twice changes nothing the second time. It matters because it
means the playbook describes a desired state rather than a list of
actions, so it is safe to re-run on a node in an unknown condition.
Ansible reports `changed=0`; Puppet exits 0 under `--detailed-exitcodes`,
where 2 would mean changes and 4 would mean failures."

**"What happens if a deployment fails at 2am?"**
"The health check catches it — it verifies the service answers, reports
UP, and is running the release that was requested, which also catches a
healthy service running the wrong version. Rollback is a playbook run
against the previous release, demonstrated in under a minute. It is not
automatic, though. Automatic rollback on a failed health check is the
honest next step."

**"Why not Kubernetes?"**
"One node. Kubernetes solves orchestration across many, and here it would
be infrastructure existing to justify itself. It is in the enhancement
plan for when there is more than one node."

**"Show me a test that fails."**
Have `proofs/stage-10/failed-pipeline.log` and `01-failed-build.png` open.
Do not try to break something live.

**"What was the hardest problem?"**
Pick a real one. The PKIX certificate failures are a good answer — two
distinct causes, the JVM not reading `HTTPS_PROXY` and `keytool` importing
only the first certificate in a chain, both needing fixing before Maven
would build in Jenkins at all.

---

## 5. If the live demo fails

Do not debug in front of the examiner. You have 110 evidence files and
40 screenshots. Say:

> The environment isn't cooperating — let me show you the recorded run
> instead, which has more detail anyway.

Then open `docs/FINAL-REPORT.md` and work through §4 and the proofs.
Treating a failed demo calmly costs you almost nothing; visibly panicking
costs you a lot.

Two specific recoveries, in case they happen:

| What is down | What to do instead |
|---|---|
| Jenkins | `proofs/stage-15/macos-pipeline-build-05.log` is the same green run as a file, and `proofs/stage-10/failed-pipeline.log` is the blocked one. You lose the live controller, not the argument. |
| Port 8080 held by an old process | `lsof -i :8080`, then `kill -9 <pid>`. A process suspended with Ctrl+Z ignores a plain `kill`. Or run the portal on another port and say so. |

Do not try to restart Jenkins mid-demo. It takes minutes.

---

## 6. Command reference

```bash
cd ~/Desktop/CI-CD-Pipeline-for-a-Student-Attendance-Management

bash scripts/start-demo.sh               # portal on :8080               ~2 min
bash scripts/start-demo.sh --tier docker # + registry, containers, Tomcat ~8 min
bash scripts/start-demo.sh --tier full   # + both Tomcat deployments      ~10 min
bash scripts/start-nodes.sh              # + Ansible and Puppet nodes    ~12 min
bash jenkins/start-local.sh              # the Jenkins controller        ~8 min
bash scripts/demo-status.sh              # one-screen status
bash scripts/capture-pipeline-proof.sh   # save a build log into proofs/
bash scripts/start-demo.sh --stop        # stop everything
bash scripts/start-nodes.sh --stop       # stop the nodes

# if port 5000 is taken by macOS AirPlay Receiver
DOCKER_REGISTRY=localhost:5001 bash jenkins/start-local.sh

mvn -pl app verify                       # 73 unit + 7 integration tests
```

| Account | Password | Role |
|---|---|---|
| `admin1` | `Admin@123` | Administrator |
| `hod1` | `Hod@12345` | Head of Department — approves |
| `faculty1` | `Faculty@123` | Records attendance |
| `student1` | `Student@123` | Own approved records only |

| Document | What it is |
|---|---|
| `docs/FINAL-REPORT.md` | All fifteen task reports combined |
| `docs/stage-NN-*.md` | The individual task reports |
| `proofs/stage-NN/` | Logs and screenshots per stage |
| `puppet/README.md` | What is deliverable and what is lab scaffolding |
