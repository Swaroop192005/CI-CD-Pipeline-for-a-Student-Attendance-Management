# Presentation and Viva Guide

How to run this project in front of an examiner, what to show, in what
order, and what you will be asked. The last two sections are written from
the examiner's side of the table rather than yours.

---

## 0. Read this first

**The running lab is not on your laptop.** Jenkins with its 32 builds, the
registry with its image tags, the two provisioned nodes and the Tomcat
deployments were all built inside a cloud container. That container is
temporary. What is permanent is this repository: the source, the pipeline
definition, the playbooks and manifests, and 110 evidence files recording
every one of those runs.

So there are two honest ways to present, and you should decide which
before you walk in:

| | What you show | Cost | Risk |
|---|---|---|---|
| **A — Live app + evidence** | The portal running on your Mac, plus the committed logs and screenshots for the pipeline | ~5 min setup | Low. Recommended. |
| **A+ — add containers and the node** | Also the registry, staging and production containers, Tomcat, and the Ansible-provisioned node | ~20 min, once | Low once it has worked a first time. `scripts/start-demo.sh --tier docker` and `scripts/start-nodes.sh`. |
| **B — Rebuild the whole lab** | Everything live, including Jenkins | ~45 min, the night before, then minutes on later runs | Low once each piece has worked once. All eight components run on an ordinary machine. |

**Take option A.** The evidence is strong, and the thing that most often
sinks a viva is a live demo failing in the first two minutes. You keep the
part that demonstrates the application working — which is what they most
want to see — and you present the pipeline from logs that are more
detailed than anything you could produce live anyway.

---

## 1. Pre-flight

### The night before

```bash
cd ~/Desktop/CI-CD-Pipeline-for-a-Student-Attendance-Management
git pull origin main

java -version          # must be 21 or newer
mvn -v

bash scripts/start-demo.sh
```

Then open `http://localhost:8080/attendance/login`, sign in as
`admin1` / `Admin@123`, and click through every screen you plan to show.
Doing this once the night before is the single highest-value thing in this
guide: it is when you discover a wrong Java version, not during the viva.

Stop it afterwards:

```bash
bash scripts/start-demo.sh --stop
```

### Thirty minutes before

```bash
cd ~/Desktop/CI-CD-Pipeline-for-a-Student-Attendance-Management
bash scripts/start-demo.sh
bash scripts/demo-status.sh
```

Leave it running. Have these tabs open:

1. `http://localhost:8080/attendance/login`
2. The GitHub repository, on the **Tags** page
3. `docs/FINAL-REPORT.md` on GitHub
4. `proofs/stage-10/failed-pipeline.log` on GitHub — the gate blocking a deploy
5. A terminal in the project directory

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

### 2.3 — The pipeline (3 min, from evidence)

Open `proofs/stage-15/end-to-end-run.log` on GitHub (the full build log is
`proofs/stage-15/pipeline-build-17.log`).

> Eleven stages, 178 seconds, no manual step. Checkout, build, unit tests,
> package, integration tests, the Selenium gate, image build and push,
> container deploy, Tomcat deploy, and a verification stage that checks the
> deployed instance reports the environment the pipeline was asked for.

Then open `proofs/stage-10/failed-pipeline.log`, and have
`proofs/stage-10/01-failed-build.png` ready beside it.

> This is the part I would point at first. A green pipeline only proves it
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

### 2.4b — Jenkins (1 min)

`bash jenkins/start-local.sh` gives you a working controller at
`http://localhost:8081/` (admin / admin) with both jobs already defined by
Configuration-as-Code — worth showing for Stage 7 and 8, since the job
existing without anyone clicking through a wizard *is* the point of CasC.

**Do not try to show the quality gate from it.** A fresh controller has no
build history, and the history is what matters: open
`proofs/stage-10/failed-pipeline.log` for the run where the gate blocked a
deployment. One green build on a new controller proves less than that log
does, so show the controller for configuration-as-code and the log for the
gate.

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

Then open `docs/FINAL-REPORT.md` and work through §4 and the proofs. The
written evidence is the stronger artefact. Treating a failed demo calmly
costs you almost nothing; visibly panicking costs you a lot.

---

## 6. Command reference

```bash
cd ~/Desktop/CI-CD-Pipeline-for-a-Student-Attendance-Management

bash scripts/start-demo.sh               # portal on :8080               ~2 min
bash scripts/start-demo.sh --tier docker # + registry, containers, Tomcat ~8 min
bash scripts/start-nodes.sh              # + Ansible-provisioned node    ~12 min
bash jenkins/start-local.sh              # the Jenkins controller        ~8 min
bash scripts/demo-status.sh              # one-screen status
bash scripts/start-demo.sh --stop        # stop everything
bash scripts/start-nodes.sh --stop       # stop the node

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
