# Stage 14 — Automated Provisioning and Reliability Validation

**Deliverable:** provisioned node, idempotency evidence, health-check
result and rollback/recovery demonstration.
**Issue:** [#10](https://github.com/Swaroop192005/CI-CD-Pipeline-for-a-Student-Attendance-Management/issues/10)

---

## 1. Provisioning a clean node

The node image ships Ubuntu 22.04, `sshd`, `python3` and `systemd` — and
nothing else. Every package, user, directory, file, port and service below
is the playbook's work.

```
$ ansible-playbook site.yml

PLAY RECAP
attendance-node : ok=33  changed=16  unreachable=0  failed=0

    "Health      : UP"
    "Environment : production"
    "Release     : 1.0.0"
    "URL         : http://127.0.0.1:80/attendance"
```

Verified from outside the node:

```
GET :8200/attendance/actuator/health  -> 200 {"status":"UP"}
GET :8200/attendance/actuator/info    -> {"app":{…,"environment":"production"}}
GET :8200/                            -> 302 (redirect to the context path)
```

---

## 2. Idempotency

```
Run 1 — clean node:   ok=33  changed=16  failed=0
Run 2 — same node:    ok=29  changed=0   failed=0
```

**`changed=0`.**

### Why this matters more than it looks

A playbook that reports changes on every run gives an operator no way to
tell real drift from the usual noise. They stop reading the recap — and
then miss the run that mattered. Idempotency is what makes the recap worth
reading at all.

### What makes it idempotent

Every task converges on a state rather than performing an action:

| Task | How it stays idempotent |
|---|---|
| Copy the artefact | Guarded by a `stat`; only copies when that release is absent. Copying 60 MB every run would report `changed` forever |
| Render the templates | Ansible compares content before writing |
| Create the directories | `state: directory` is declarative |
| Install packages | `state: present`, with `cache_valid_time` so repeat runs do not re-index |
| Restart the service | Only ever by a handler, and only when something notified it |
| Prune old releases | `changed_when: false` — it is housekeeping, not a change to the declared state |

---

## 3. Health check

`healthcheck.yml` is deliberately separate from `site.yml`: finding out
whether a node is healthy should not require running a provisioning
playbook against it.

It checks **three** things, because any one alone can be true while the
service is unusable:

```
"Unit        : active"      systemd thinks it is running
"Health      : UP"          it answers, through nginx, on the path a user takes
"Live release: 1.0.1"       the symlink
"Configured  : 1.0.1"       the environment file
```

The third is the one a health check usually omits. A service that is up
and healthy but running the wrong release is a failure no liveness probe
can see.

---

## 4. Rollback, driven by a real failure

Rather than roll back a working release to demonstrate the mechanism, a
release was made that genuinely had to be rolled back.

### 4.1 The bad release

Release `1.0.2` was a **truncated artefact** — 2 MB of a 60 MB WAR, which
is what an interrupted transfer or a failed build actually produces.

### 4.2 The deployment refused itself

```
$ ansible-playbook site.yml -e app_version=1.0.2

TASK [Wait for the application to answer its health endpoint]
fatal: [attendance-node]: FAILED! =>
  {"attempts": 30, "status": 502,
   "msg": "Status code was 502 and not [200]: HTTP Error 502: Bad Gateway"}

PLAY RECAP
attendance-node : ok=27  changed=5  failed=1
```

The node at that moment:

```
systemctl is-active attendance       -> activating     (crash-looping)
readlink -f /opt/attendance/current  -> /opt/attendance/releases/1.0.2
tail /var/log/attendance/attendance.log
  Error: Invalid or corrupt jarfile /opt/attendance/current/attendance.war
health via nginx                     -> HTTP 502
```

The playbook did not report success on a broken deployment. That is the
first half of reliability: a deployment that cannot tell it has failed
cannot be recovered from automatically.

### 4.3 The rollback

```
$ ansible-playbook rollback.yml

"Live now  : 1.0.2"
"Available : 1.0.2, 1.0.1, 1.0.0"
"Rolling back from 1.0.2 to 1.0.1"

TASK [Rewrite the environment file for the target release]   changed
TASK [Point `current` at the target release]                 changed
TASK [Restart the service]                                   changed
TASK [Wait for the rolled-back release to become healthy]    ok

"Rolled back : 1.0.2 -> 1.0.1"
"Health      : UP"

PLAY RECAP
attendance-node : ok=15  changed=3  failed=0
```

### 4.4 Verified after recovery

```
"Unit        : active"
"Health      : UP"
"Live release: 1.0.1"
"Configured  : 1.0.1"

GET :8200/attendance/actuator/health -> 200 {"status":"UP"}
```

Three changed tasks, under a minute, service restored.

---

## 5. Why rollback is a symlink and not a re-fetch

The release directories are kept on the node:

```
/opt/attendance/releases/1.0.0
/opt/attendance/releases/1.0.1
/opt/attendance/releases/1.0.2
/opt/attendance/current -> /opt/attendance/releases/1.0.1
```

A rollback that had to re-fetch an artefact would depend on the registry,
the network and the build that produced it still existing — which are
exactly the things that may be broken at the moment a rollback is needed.
Keeping five releases costs 300 MB of disk and removes that dependency
entirely.

## 6. The rollback playbook refuses safely

Three guards run **before** anything is touched:

| Guard | What it prevents |
|---|---|
| "Refuse when there is nothing to roll back to" | A first deployment silently "rolling back" to nothing |
| "Refuse when the chosen release is not on the node" | A mistyped `-e rollback_to=` pointing `current` at a path that does not exist |
| "Verify the target release still holds its artefact" | Rolling back into a directory that was pruned or partially copied |

This was not theoretical. The first rollback attempt failed on a wrong
template path — and failed **before** touching the symlink, leaving the
node exactly as it was rather than half rolled back. A half-applied
rollback is worse than none: the operator then has to work out which half.

---

## 7. Evidence index

| File | Shows |
|---|---|
| [`proofs/stage-14/provision-run1.log`](../proofs/stage-14/provision-run1.log) | First run, 16 changes |
| [`proofs/stage-14/provision-run2.log`](../proofs/stage-14/provision-run2.log) | Second run, **changed=0** |
| [`proofs/stage-14/idempotency-recap.txt`](../proofs/stage-14/idempotency-recap.txt) | Both recaps, the health check, the full rollback story, releases on the node |
| [`proofs/stage-14/deploy-1.0.1.log`](../proofs/stage-14/deploy-1.0.1.log) | A normal second release |
| [`proofs/stage-14/deploy-broken-1.0.2.log`](../proofs/stage-14/deploy-broken-1.0.2.log) | The corrupt release refusing its own health check |
| [`proofs/stage-14/rollback-run.log`](../proofs/stage-14/rollback-run.log) | The rollback, with its guards |
| [`proofs/stage-14/healthcheck.log`](../proofs/stage-14/healthcheck.log) | Standalone health check after recovery |
| [`proofs/stage-14/health-check.json`](../proofs/stage-14/health-check.json) | Health and info from outside the node |
| [`proofs/stage-14/screenshot-provisioned-node.png`](../proofs/stage-14/screenshot-provisioned-node.png) | The application served from the provisioned node, through nginx |

---

## 8. Stage 14 Definition of Done

| # | Criterion | Status |
|---|---|---|
| D3 | Playbooks run green | `site.yml`, `healthcheck.yml`, `rollback.yml` |
| D8 | Stage documentation written | This document |
| D9 | Evidence captured | `proofs/stage-14/` |
| D10 | Provisioned instance answers its health check | `UP`, through nginx |
| D12 | Backlog updated | US-19 Done |

**Outcome:** a clean node provisioned from scratch, a second run reporting
`changed=0`, a three-part health check that also catches a healthy service
running the wrong release, and a rollback demonstrated against a release
that genuinely failed — recovered in three tasks and under a minute, with
guards that refuse safely rather than half-applying.
