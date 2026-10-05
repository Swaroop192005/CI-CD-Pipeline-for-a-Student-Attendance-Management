# Evidence Index

Every claim in the project reports is backed by a file here. This index
maps each one to the claim it supports.

## Stage 0 — Environment readiness

| File | Supports |
|---|---|
| `stage-00/tool-versions.txt` | Every tool version used, captured by running each command |

## Stage 3 — Working local setup

| File | Supports |
|---|---|
| `stage-03/mvn-verify.log` | A green `mvn clean verify` on the baseline |
| `stage-03/app-startup.log` | The application binding `/attendance` |
| `stage-03/health-check.json` | `/actuator/health` returning `UP` |
| `stage-03/home-page.html` | The rendered page with its configuration bound |
| `stage-03/screenshot-login.png`, `screenshot-skeleton-home.png` | The running skeleton |

## Stage 4 — Repository initialisation

| File | Supports |
|---|---|
| `stage-04/initial-commits.log` | The seven initial commits and the tracked file list |

## Stage 5 — Feature 1 with branching

| File | Supports |
|---|---|
| `stage-05/mvn-verify.log` | 39 tests green after the review fix |
| `stage-05/verification.txt` | Per-class test counts, seeding, the list page |
| `stage-05/app-run.log` | Startup with `Seeded 5 users, 10 students, 300 attendance records` |
| `stage-05/git-evidence.log` | Branch graph, commit list, merge commit |
| `stage-05/01..04-*.png` | Sign-in, list, entry form, record detail |

## Stage 6 — MVP, conflict, tag

| File | Supports |
|---|---|
| `stage-06/mvn-verify.log` | 80 tests green |
| `stage-06/workflow-demo.log` | The full transition chain and every authorisation refusal, exercised live |
| `stage-06/merge-conflict.log` | The raw `git merge` output, the conflict as git produced it, and the resolution |
| `stage-06/release-tag.log` | The annotated `v1.0.0` tag, and why it is not on origin |
| `stage-06/01..07-*.png` | Every role's view |

## Stage 7 — Jenkins CI

| File | Supports |
|---|---|
| `stage-07/ci-job-evidence.log` | Controller version, jobs from CasC, SCM trigger, poll log, build history, 80 tests, artefact download |
| `stage-07/build-2-console.log` | The green build in full |
| `stage-07/01..04-*.png` | Dashboard, job page with red-then-green history, console, test report |

## Stage 8 — Pipeline as code

| File | Supports |
|---|---|
| `stage-08/pipeline-run.log`, `pipeline-run-production.log` | Two parameterised runs |
| `stage-08/parameter-evidence.txt` | Both environments live at once with correct labels; stage timings |
| `stage-08/deployed-health.json` | Health and info from the deployed instance |
| `stage-08/01..04-*.png` | Pipeline job, build detail, both deployed environments |

## Stage 9 — Selenium suite

| File | Supports |
|---|---|
| `stage-09/local-run.log` | 20 journeys green |
| `stage-09/failsafe-summary.txt` | Per-journey results, pinned versions, the measured criteria |
| `stage-09/failure-capture-demo.log` | A deliberate failure, to prove the mechanism |
| `stage-09/failure-screenshot-demo.png`, `failure-report-demo.txt` | What it captured |

## Stage 10 — Quality gate

| File | Supports |
|---|---|
| `stage-10/quality-gate-evidence.txt` | Builds #9, #10 and #11 side by side, with the zero-count proof that no deployment ran |
| `stage-10/failed-pipeline.log` | The red build |
| `stage-10/successful-rerun.log` | The green rerun after the fix |
| `stage-10/gate-failure-screenshot.png`, `gate-failure-report.txt` | What the browser saw when the gate failed |
| `stage-10/01..04-*.png` | Red build, failing test report, green rerun, build history |

## Stage 11 — Container lifecycle

| File | Supports |
|---|---|
| `stage-11/docker-lifecycle.log` | All twelve lifecycle phases with real output, including volume persistence across a container removal |
| `stage-11/image-details.txt` | Labels, environment, layers, non-root user, volume, healthcheck |
| `stage-11/container-health.json` | The healthcheck reaching `healthy` |
| `stage-11/screenshot-containerised-app.png` | The application from the container |

## Stage 12 — Registry and CD

| File | Supports |
|---|---|
| `stage-12/pipeline-e2e.log` | The eleven-stage run |
| `stage-12/image-tags.txt` | Published tags, registry read-back, pull-based deploy, both modes live |
| `stage-12/registry-catalog.json` | The registry's own catalogue |
| `stage-12/deployed-container.txt` | Container status, healthcheck, restart policy, volume |
| `stage-12/01-e2e-pipeline-build.png`, `screenshot-deployed-container.png` | The run and the result |

## Stage 13 — Configuration management

| File | Supports |
|---|---|
| `stage-13/syntax-check.log` | `ansible-playbook --syntax-check` |
| `stage-13/first-run.log` | The first provisioning run, 16 changes |
| `stage-13/provisioned-node.txt` | Packages, user, directories, files, ports, services and the rendered configuration, item by item against the specification |

## Stage 14 — Reliability

| File | Supports |
|---|---|
| `stage-14/provision-run1.log` | First run, `changed=16` |
| `stage-14/provision-run2.log` | Second run, **`changed=0`** |
| `stage-14/idempotency-recap.txt` | Both recaps, the three-part health check, the full rollback story |
| `stage-14/deploy-1.0.1.log` | A normal second release |
| `stage-14/deploy-broken-1.0.2.log` | A corrupt release refusing its own health check |
| `stage-14/rollback-run.log` | The rollback and its guards |
| `stage-14/healthcheck.log`, `health-check.json` | Health after recovery |
| `stage-14/screenshot-provisioned-node.png` | The application through nginx on the provisioned node |

## Stage 15 — End to end

| File | Supports |
|---|---|
| `stage-15/end-to-end-run.log` | One commit through every phase to three healthy targets |
| `stage-15/pipeline-build-17.log` | The pipeline run in full |
| `stage-15/ansible-provision.log` | The same artefact provisioned onto the managed node |
| `stage-15/01..09-*.png` | Every role's view, the Jenkins dashboard and the final pipeline run |
