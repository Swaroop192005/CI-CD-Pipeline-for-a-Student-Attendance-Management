# Stage 12 — Jenkins–Docker Continuous Deployment

**Deliverable:** versioned image, registry evidence and an end-to-end
commit-to-container pipeline.
**Issue:** [#8](https://github.com/Swaroop192005/CI-CD-Pipeline-for-a-Student-Attendance-Management/issues/8) · **Pull request:** [#17](https://github.com/Swaroop192005/CI-CD-Pipeline-for-a-Student-Attendance-Management/pull/17)

---

## 1. The complete pipeline

Eleven stages, one commit to a running container, no manual step:

| # | Stage | Duration |
|---|---|---|
| 1 | Checkout | 1.2 s |
| 2 | Build | 4.3 s |
| 3 | Unit test | 24.2 s |
| 4 | Package | 5.6 s |
| 5 | Integration test | 12.6 s |
| 6 | Quality gate: Selenium | 85.7 s |
| 7 | **Build and publish image** | 13.0 s |
| 8 | **Deploy container** | 9.8 s |
| 9 | Deploy to Tomcat | 6.1 s |
| 10 | Verify deployment | 16.3 s |
| | **TOTAL** | **181.2 s** |

Three minutes from commit to a healthy container, with the quality gate
accounting for 47% of it — the right thing to be spending time on.

The image and registry stages sit **after** the quality gate. An image
built from code that failed its journeys is an image nobody should be
able to pull by accident.

---

## 2. The registry

No managed container registry is reachable from this lab
(`docs/00-environment-prerequisites.md`, limitation L3), so the pipeline
publishes to a local `registry:2` on port 5000, defined in
[`docker/registry-compose.yml`](../docker/registry-compose.yml).

Its storage is a named volume, so pushed images survive a restart of the
registry itself. That matters for Stage 14, where rollback means pulling
a tag an *earlier* build pushed.

```
$ curl -s http://localhost:5000/v2/
HTTP 200
```

`127.0.0.0/8` is already in Docker's default insecure-registry set, so no
daemon change was needed.

---

## 3. Versioned images

```
localhost:5000/attendance-portal:1.0.<build>-<commit>
localhost:5000/attendance-portal:latest
```

Both are pushed. The version tag is the one that matters: **a tag that
moves cannot be rolled back to**, and Stage 14's rollback is precisely
"run the previous version again" — which requires the previous version to
still have a name. `latest` exists only as a convenience for a human
pulling by hand.

### Tags accumulate across builds

```json
{
    "name": "attendance-portal",
    "tags": [
        "latest",
        "1.0.0",
        "1.0.14-c5dcba3",
        "1.0.13-c5dcba3"
    ]
}
```

Two pipeline runs, two immutable version tags. That list *is* the rollback
capability.

### Published, then read back

The pipeline does not trust the push to have worked; it asks the registry:

```
Published:
  localhost:5000/attendance-portal:1.0.13-c5dcba3
  localhost:5000/attendance-portal:latest

Registry now holds:
{"name":"attendance-portal","tags":["latest","1.0.0","1.0.13-c5dcba3"]}
```

---

## 4. Automatic container deployment

The deploy stage **pulls from the registry**, rather than using the image
still sitting in the local build cache:

```
Deploying localhost:5000/attendance-portal:1.0.13-c5dcba3 as attendance-app-staging on port 8100
1.0.13-c5dcba3: Pulling from attendance-portal
Digest: sha256:d15441d6c2644ca4dd7a76a46da065728aaa5c09efb28ba9b53fe1e17229b58d
Status: Image is up to date for localhost:5000/attendance-portal:1.0.13-c5dcba3
Container healthy after 4 attempt(s)
attendance-app-staging  localhost:5000/attendance-portal:1.0.13-c5dcba3  Up 9 seconds  0.0.0.0:8100->8080/tcp
{"app":{"name":"Student Attendance Management Portal","environment":"staging"}}
Containerised application URL: http://localhost:8100/attendance
```

Pulling is deliberate. It is the path a separate target node would take,
and exercising a different one here would prove nothing about that node.

### Deployment properties

| Property | Value | Reason |
|---|---|---|
| Container name | `attendance-app-<environment>` | One target per environment; staging must not replace production |
| Data volume | `attendance-data-<environment>`, **not removed on redeploy** | Replacing a container must never destroy attendance records |
| Restart policy | `unless-stopped` | Survives a daemon or host restart |
| Labels | `app.version`, `app.commit` | Which build is running, answerable from `docker inspect` |
| Health | Waited for, with the container log printed on timeout | A running container is not a working deployment |

### Provenance from the running container

```json
{
    "app.commit": "c5dcba3",
    "app.version": "1.0.14-c5dcba3",
    "org.opencontainers.image.revision": "c5dcba3",
    "org.opencontainers.image.created": "2026-10-05T08:24:51Z",
    "org.opencontainers.image.source": "https://github.com/Swaroop192005/CI-CD-Pipeline-for-a-Student-Attendance-Management"
}
```

From a running container alone, one command gives the commit it was built
from. That is the chain the audit requirement needs at the infrastructure
level, mirroring the per-record audit trail at the application level.

---

## 5. Both deployment modes, from one build

```
Tomcat WAR  :8090 -> {"app":{…,"environment":"staging"}}   health: {"status":"UP"}
Container   :8100 -> {"app":{…,"environment":"staging"}}   health: {"status":"UP"}
```

The same `attendance.war`, deployed to Tomcat 10.1 *and* running
standalone inside the container — the "one artefact, two deployment
modes" property chosen back in Stage 3 (NFR-07), now demonstrated with
both live at once.

---

## 6. Evidence index

| File | Shows |
|---|---|
| [`proofs/stage-12/pipeline-e2e.log`](../proofs/stage-12/pipeline-e2e.log) | Full console of the end-to-end run |
| [`proofs/stage-12/image-tags.txt`](../proofs/stage-12/image-tags.txt) | Stage table, published tags, registry read-back, pull-based deploy, both modes live |
| [`proofs/stage-12/registry-catalog.json`](../proofs/stage-12/registry-catalog.json) | The registry's own catalogue and tag list |
| [`proofs/stage-12/deployed-container.txt`](../proofs/stage-12/deployed-container.txt) | Container status, healthcheck log, restart policy, volume |
| [`01-e2e-pipeline-build.png`](../proofs/stage-12/01-e2e-pipeline-build.png) | The pipeline run in Jenkins |
| [`screenshot-deployed-container.png`](../proofs/stage-12/screenshot-deployed-container.png) | The application served from the deployed container |

---

## 7. Stage 12 Definition of Done

| # | Criterion | Status |
|---|---|---|
| D1 | Reviewed on a pull request | PR #17 |
| D5 | Jenkins pipeline green | Builds #13 and #14 |
| D6 | Selenium quality gate passed | Yes — the image stages run only after it |
| D8 | Stage documentation written | This document |
| D9 | Evidence captured | `proofs/stage-12/` |
| D10 | Deployed instance answers its health check | Container `healthy` |
| D11 | Merged | PR #17 merged into `develop` |
| D12 | Backlog updated | US-17 Done |

**Outcome:** one commit drives build, test, quality gate, versioned image,
registry publication and a fresh container deployment pulled from that
registry — three minutes end to end with no manual step, and with
accumulated version tags that make the Stage 14 rollback possible.
