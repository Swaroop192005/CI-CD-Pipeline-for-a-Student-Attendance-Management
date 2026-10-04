# Stage 11 — Docker Image and Container Lifecycle

**Deliverable:** Dockerfile, image details, Docker command log and running
container evidence.
**Issue:** [#7](https://github.com/Swaroop192005/CI-CD-Pipeline-for-a-Student-Attendance-Management/issues/7) · **Pull request:** [#17](https://github.com/Swaroop192005/CI-CD-Pipeline-for-a-Student-Attendance-Management/pull/17)

---

## 1. The Dockerfile

[`Dockerfile`](../Dockerfile) — a single runtime stage built around the
WAR Maven already produced.

### The image packages the artefact, it does not rebuild it

The most consequential decision in this stage.

The pipeline builds `attendance.war` once, runs the unit tests, the
integration tests and the Selenium quality gate **against that artefact**,
and only then builds the image around it. An image that rebuilt from
source would ship a binary that nothing had tested — *"it was the same
source"* is a weaker claim than *"it was the same artefact"*, and
bit-for-bit reproducible builds are hard enough that the distinction is
real rather than pedantic.

It also keeps a build toolchain out of the runtime image, which is both
wasted space and a larger attack surface than the application, and the
image builds in about three seconds because there is no dependency
resolution.

The cost: `docker build` needs a prior `mvn package`.
[`docker/build-image.sh`](../docker/build-image.sh) runs it when the
artefact is missing, and the pipeline always has one by that point.

### Runtime posture

| Choice | Reason |
|---|---|
| Non-root user `attendance` (uid 1001) | The application never writes outside `/app/data`. A container compromise should not begin as root |
| OCI labels: version, commit, build time | `docker inspect` answers "which commit is this?" instead of leaving someone to guess from a tag |
| Healthcheck, 60 s start period | A cold JVM plus schema creation takes a few seconds; a check that reports unhealthy during normal startup trains people to ignore it |
| `exec` form entrypoint | The JVM becomes PID 1 and receives SIGTERM directly. Without it the signal reaches a shell that does not forward it, so every `docker stop` waits the full ten-second timeout and then kills — losing the clean shutdown |
| `VOLUME /app/data` | Removing a container must not destroy a term's attendance records |
| Every setting as `ENV` | Overridable at run time without a rebuild (NFR-08), which is what lets one image serve staging and production |

### Image

```
REPOSITORY          TAG       IMAGE ID       SIZE
attendance-portal   1.0.0     00505b427f6d   525MB
```

525 MB total: 461 MB is the `eclipse-temurin:21-jre-jammy` base, 63.8 MB
is the application WAR, and 406 kB is the user creation.

```json
{
    "org.opencontainers.image.title": "Student Attendance Management Portal",
    "org.opencontainers.image.version": "1.0.0",
    "org.opencontainers.image.revision": "2b5c3f3",
    "org.opencontainers.image.created": "2026-10-05T08:14:08Z",
    "org.opencontainers.image.source": "https://github.com/Swaroop192005/CI-CD-Pipeline-for-a-Student-Attendance-Management"
}
```

---

## 2. The complete container lifecycle

Every command below was executed; the full session with real output is in
[`proofs/stage-11/docker-lifecycle.log`](../proofs/stage-11/docker-lifecycle.log).

| # | Phase | Command | Result |
|---|---|---|---|
| 1 | **Build** | `docker/build-image.sh attendance-portal:1.0.0` | 525 MB image, labels and healthcheck present |
| 2 | **Tag** | `docker tag attendance-portal:1.0.0 attendance-portal:latest` | Identical image IDs — a tag is a label, not a copy |
| 3 | **Run** | `docker run -d --name attendance-app -p 8100:8080 -v attendance-data:/app/data …` | Healthy in 10 s |
| 4 | **Inspect** | `docker inspect`, `docker port`, `docker exec … id`, `docker stats` | Runs as uid 1001, 8080→8100, 350 MiB |
| 5 | **Verify** | `curl …/actuator/health`, `…/info`, `…/login` | `UP`, correct environment, HTTP 200 |
| 6 | **Logs** | `docker logs` | Seeding, Tomcat bind, startup |
| 7 | **Stop** | `docker stop` | **0 s**, exit 143, not OOM-killed |
| 8 | **Start** | `docker start` | Healthy in 9 s, seeding skipped |
| 9 | **Restart** | `docker restart` | Healthy again |
| 10 | **Remove** | `docker rm` | Container gone, **volume remains** |
| 11 | **Prove** | New container on the same volume | Found the data, skipped seeding |
| 12 | **Clean up** | `docker rmi attendance-portal:latest` | Only the extra tag removed; `1.0.0` remains |

### Clean shutdown, demonstrated

```
$ docker stop attendance-app
attendance-app

  docker stop took 0 s. The default kill timeout is 10 s.

$ docker inspect attendance-app --format 'ExitCode: {{.State.ExitCode}}   OOMKilled: {{.State.OOMKilled}}'
ExitCode: 143   OOMKilled: false
```

Exit 143 is 128 + SIGTERM: the JVM received the signal, ran its shutdown
hooks and exited. Returning in under a second rather than after the
ten-second timeout is what the `exec` form buys.

### Volume persistence, proven rather than asserted

The container was **removed entirely** and a new one started on the same
volume:

```
# 10. RM - remove the container, keep the volume
$ docker ps -a --filter name=attendance-app
NAMES     STATUS
  (no rows: the container is gone)

$ docker volume ls --filter name=attendance-data
VOLUME NAME       DRIVER
attendance-data   local

# 11. PROVE IT - a brand new container on the same volume
$ docker logs attendance-app | grep -E 'Seeded|skipping seed'
DataSeeder : Datastore already populated (5 users); skipping seed
```

`skipping seed` is the proof: the new container found the previous one's
data and left it alone. For a system of record this is not a nicety —
losing a term's attendance to a container replacement would be worse than
the paper register it replaced.

### Healthcheck reaching healthy

```json
{
    "Status": "healthy",
    "FailingStreak": 0,
    "Log": [
        { "ExitCode": 1 },
        { "ExitCode": 0 }
    ]
}
```

The first probe failed because the JVM was still starting — which is
exactly why the start period exists, and why that first failure did not
mark the container unhealthy.

---

## 3. Evidence index

| File | Shows |
|---|---|
| [`Dockerfile`](../Dockerfile) | The image definition, with the reasoning in comments |
| [`docker/build-image.sh`](../docker/build-image.sh) | Build helper supplying the version metadata |
| [`proofs/stage-11/docker-lifecycle.log`](../proofs/stage-11/docker-lifecycle.log) | All twelve phases with real output |
| [`proofs/stage-11/image-details.txt`](../proofs/stage-11/image-details.txt) | Labels, env, layers, user, volume, healthcheck |
| [`proofs/stage-11/container-health.json`](../proofs/stage-11/container-health.json) | Healthcheck log reaching `healthy` |
| [`proofs/stage-11/screenshot-containerised-app.png`](../proofs/stage-11/screenshot-containerised-app.png) | The application running from the container |

---

## 4. Stage 11 Definition of Done

| # | Criterion | Status |
|---|---|---|
| D1 | Reviewed on a pull request | PR #17 |
| D3 | Build green | Image builds and runs |
| D8 | Stage documentation written | This document |
| D9 | Evidence captured | `proofs/stage-11/` |
| D10 | Deployed instance answers its health check | `healthy` |
| D11 | Merged | PR #17 merged into `develop` |
| D12 | Backlog updated | US-16 Done |

**Outcome:** the portal ships as a 525 MB image running as a non-root
user with a working healthcheck, and the full container lifecycle has been
exercised and recorded — including proof that removing a container does
not destroy its data.
