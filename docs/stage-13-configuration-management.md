# Stage 13 — Configuration Management Script

**Deliverable:** configuration specification, Ansible playbook and first
execution log.
**Issue:** [#9](https://github.com/Swaroop192005/CI-CD-Pipeline-for-a-Student-Attendance-Management/issues/9)

---

## 1. Why Ansible rather than Puppet

Both were permitted. Ansible was chosen for two reasons, one practical and
one environmental:

1. **Agentless.** Puppet is only meaningfully itself with an agent, and
   usually a master. On a single lab node that is infrastructure existing
   to justify itself. Ansible reaches the node over SSH and leaves nothing
   behind.
2. **`apt.puppet.com` is refused by the lab's egress policy** (HTTP 403 at
   the proxy), so Puppet could not have been installed here at all. PyPI
   *is* reachable, so `pip install ansible-core` works.

Recorded in `docs/00-environment-prerequisites.md` §3.4.

---

## 2. Configuration specification

What a bare node must become before it can serve the portal. This is the
specification the playbook implements, and the checklist Stage 14 verifies.

### 2.1 Packages

| Package | Why |
|---|---|
| `openjdk-21-jre-headless` | The runtime. A JRE, not a JDK: the artefact arrives pre-built, so a compiler on an application server is weight and attack surface for nothing |
| `nginx` | The edge, in front of the application |
| `curl` | Health probes |
| `unzip` | Artefact inspection when something needs diagnosing |
| `ca-certificates` | Trust store for anything the node fetches |

### 2.2 User and group

| Property | Value | Why |
|---|---|---|
| Group | `attendance` (gid 1501) | — |
| User | `attendance` (uid 1501) | Dedicated service account |
| Shell | `/usr/sbin/nologin` | Nobody signs in as the service |
| System account | yes | No home directory, no mail spool, outside the human uid range |

The application has no business running as root, and the account it does
run as has no business being usable interactively.

### 2.3 Directories

| Path | Owner | Mode | Purpose |
|---|---|---|---|
| `/opt/attendance` | `attendance` | 0755 | Application root |
| `/opt/attendance/releases` | `attendance` | 0755 | One directory per release, **kept**, not overwritten |
| `/opt/attendance/current` | `attendance` | symlink | Points at the live release |
| `/var/lib/attendance` | `attendance` | 0750 | H2 datastore |
| `/var/log/attendance` | `attendance` | 0750 | Application log |
| `/etc/attendance` | **root** | 0750 | Configuration — the service reads it and must not be able to rewrite it |

Keeping releases rather than overwriting is what makes Stage 14's rollback
a symlink change rather than a re-fetch.

### 2.4 Files

| File | Owner | Mode | Purpose |
|---|---|---|---|
| `/etc/attendance/attendance.env` | root:attendance | 0640 | Every setting the application reads |
| `/etc/systemd/system/attendance.service` | root:root | 0644 | Service unit |
| `/etc/nginx/sites-available/attendance.conf` | root:root | 0644 | Reverse proxy |
| `/etc/logrotate.d/attendance` | root:root | 0644 | Log rotation — without it the log grows until the disk fills, which is a slow outage that looks like a sudden one |

### 2.5 Ports

| Port | Bound by | Exposure |
|---|---|---|
| 80 | nginx | The only port a user touches |
| 8080 | the application | Behind nginx |
| 22 | sshd | Management, key-based only |

### 2.6 Services

| Service | Enabled at boot | Managed by |
|---|---|---|
| `attendance` | yes | `systemd`, unit written by the playbook |
| `nginx` | yes | `systemd` |
| `ssh` | yes | Present on the node image |

---

## 3. The playbook

```
ansible/
├── ansible.cfg                  Pipelining, no host-key prompts, 10 forks
├── inventory.ini                The target node and its connection details
├── group_vars/
│   └── attendance_servers.yml   Every path, port and version as a variable
├── site.yml                     Provision and deploy
├── healthcheck.yml              Standalone health check
├── rollback.yml                 Roll back to a previous release
├── roles/
│   ├── common/                  User, group, directories, utilities, logrotate
│   ├── java/                    The JRE
│   ├── attendance_app/          Release, config, systemd unit, symlink
│   └── nginx/                   Edge configuration
├── targetnode/                  A bare node image for Stage 14 to provision
└── lab/proxy-relay.py           Lab-only: bridges the loopback proxy
```

### Design decisions

**Releases are versioned directories with a `current` symlink.** The
symlink *is* the deployment: everything before it prepares a release, and
pointing it is the moment one becomes live. Rollback reverses exactly that
one step.

**The artefact is copied only when that release is absent.** Copying 60 MB
on every run would make the playbook report `changed` forever and destroy
the idempotency this stage exists to demonstrate.

**Nginx configuration is validated with `nginx -t` before the service is
enabled.** An invalid configuration that is already enabled takes the edge
down at the next restart — which may be hours later and look unrelated.

**Handlers restart; tasks do not.** The service is restarted only when
something actually changed, which is why the second run restarts nothing.

**The systemd unit is hardened**: `NoNewPrivileges`, `PrivateTmp`,
`ProtectSystem=strict`, `ProtectHome`, and `ReadWritePaths` limited to the
data and log directories. `TimeoutStopSec=30` gives the JVM time to shut
down cleanly — a forced kill mid-write is how an H2 file gets corrupted.

---

## 4. The target node

[`ansible/targetnode/`](../ansible/targetnode/) builds a deliberately
**bare** node: Ubuntu 22.04, `sshd`, `python3` (which Ansible needs on the
managed node) and `systemd` as PID 1. Nothing the application needs is
installed.

That is the point. Anything the node arrives with is something Stage 14
does not get to prove the playbook can provision.

systemd runs as PID 1 so the playbook manages real service units with the
`systemd` module, rather than a shell script pretending to be one.

Access is by SSH key only; password authentication is disabled in the
image. A password-authenticated node is not something to demonstrate as
good practice even in a lab.

---

## 5. First execution

```
$ ansible-playbook site.yml

PLAY RECAP
attendance-node : ok=33  changed=16  unreachable=0  failed=0  skipped=0

TASK [Report the provisioned service]
    "Health      : UP"
    "Environment : production"
    "Release     : 1.0.0"
    "URL         : http://127.0.0.1:80/attendance"
```

Sixteen changes on a clean node, ending with the application answering its
health endpoint **through nginx** — a deployment is not done until it is
reachable by the path a user actually takes.

### The node afterwards

```
== PACKAGES ==
  curl                             7.81.0-1ubuntu1.29
  nginx                            1.18.0-6ubuntu14.21
  openjdk-21-jre-headless:amd64    21.0.12.1+1-1~22.04.4

== USER AND GROUP ==
  attendance:x:1501:1501::/opt/attendance:/usr/sbin/nologin

== DIRECTORIES ==
  drwxr-x--- root        attendance  /etc/attendance
  drwxr-xr-x attendance  attendance  /opt/attendance
  drwxr-xr-x attendance  attendance  /opt/attendance/releases
  drwxr-x--- attendance  attendance  /var/lib/attendance
  drwxr-x--- attendance  attendance  /var/log/attendance

== PORTS ==
  0.0.0.0:80    nginx
  0.0.0.0:8080  java
  0.0.0.0:22    sshd

== SERVICES ==
  attendance     enabled=enabled   active=active
  nginx          enabled=enabled   active=active
```

Every row of the specification in §2, present on the node.

---

## 6. Problems met

### 6.1 The managed node could not reach the package mirrors

The node is on Docker's bridge network; the lab's HTTPS proxy listens on
the host's loopback only, where "loopback" from inside the node means the
node itself. The first run failed with five apt-cache retries and an empty
error.

**Resolution:** [`ansible/lab/proxy-relay.py`](../ansible/lab/proxy-relay.py),
a small TCP relay bound to `0.0.0.0` on the host that forwards to the
proxy, with the node pointed at the bridge gateway. It is lab plumbing, not
part of the system: on a network where the node reaches its mirrors
directly, `proxy_url` is left empty and the playbook skips the apt-proxy
task entirely.

### 6.2 "No package matching 'unzip' is available"

A misleading message. The node image clears `/var/lib/apt/lists`, and the
roles use `cache_valid_time` so repeat runs do not re-index for nothing —
which on a genuinely clean node skipped the very first refresh.

**Resolution:** an explicit cache refresh in `pre_tasks`, before any role
installs anything. The roles keep `cache_valid_time` for the repeat runs
that idempotency depends on.

### 6.3 The target node's trust store had to be bootstrapped

apt now speaks HTTPS through the proxy, so it needs a trust store before
it can fetch the `ca-certificates` package that would create one — and the
base image ships none. A file holding only the proxy CA is **not** enough:
apt's GnuTLS backend validates against the system store as a whole and
rejects it.

**Resolution:** bootstrap with a complete bundle, then let
`update-ca-certificates` regenerate the store properly once the package is
installed.

---

## 7. Evidence index

| File | Shows |
|---|---|
| [`ansible/`](../ansible/) | Inventory, variables, three playbooks, four roles |
| [`proofs/stage-13/syntax-check.log`](../proofs/stage-13/syntax-check.log) | `ansible-playbook --syntax-check` |
| [`proofs/stage-13/first-run.log`](../proofs/stage-13/first-run.log) | The full first run, 16 changes |
| [`proofs/stage-13/provisioned-node.txt`](../proofs/stage-13/provisioned-node.txt) | Packages, user, directories, files, ports, services, rendered config |

---

## 8. Stage 13 Definition of Done

| # | Criterion | Status |
|---|---|---|
| D3 | Playbook runs green | `failed=0` |
| D8 | Stage documentation written | This document |
| D9 | Evidence captured | `proofs/stage-13/` |
| D10 | Provisioned instance answers its health check | `UP` through nginx |
| D12 | Backlog updated | US-18 Done |

**Outcome:** a written configuration specification covering packages,
users, directories, files, ports and services, and an Ansible inventory
and role-structured playbook that implements it — executed against a bare
node and verified item by item.
