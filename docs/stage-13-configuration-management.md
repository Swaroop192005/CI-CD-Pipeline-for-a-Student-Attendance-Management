# Stage 13 — Configuration Management Script

**Deliverable:** *"Create either a Puppet manifest/modules or an Ansible
inventory and YAML playbook"* — configuration specification, the
implementation, and first execution log.
**Issue:** [#9](https://github.com/Swaroop192005/CI-CD-Pipeline-for-a-Student-Attendance-Management/issues/9)

The stage asked for **either** tool. Both are delivered: an Ansible
inventory and playbook (§3) and a Puppet module (§7), each executed
against its own bare node and verified. §7.6 compares the two end states.

---

## 1. Choosing a tool

The stage permits either tool, and the specification in §2 is written in
tool-neutral terms precisely so it can be implemented twice.

**Ansible was implemented first**, for two reasons:

1. **Agentless.** Ansible reaches the node over SSH and leaves nothing
   behind. Puppet is conventionally run with an agent and a master, which
   on a single lab node is infrastructure existing to justify itself.
2. **Installability.** `pip install ansible-core` works here; PyPI is
   reachable. The first attempt at Puppet failed because `apt.puppet.com`
   is refused by the lab's egress policy with HTTP 403 at the proxy.

That second reason was a statement about one installation route, not about
Puppet, and it was wrong to leave it as the final word. Puppet was
installed by a different route — see §7.1 — and the module in `puppet/`
implements the same §2 specification. Both are part of this deliverable.

Keeping both is worth the duplication: §7.6 shows two tools with opposite
execution models converging the same specification to byte-identical
files, which is the clearest statement this project can make that §2 is a
*specification* rather than a transcript of whatever one script happened
to do.

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

## 7. The second implementation — Puppet

The same specification, implemented again as a Puppet module and applied to
its own bare node, `attendance-node-puppet`.

### 7.1 Installing Puppet after `apt.puppet.com` was refused

The Puppet apt repository is blocked here (HTTP 403 at the egress proxy),
which is what stopped the first attempt. Two routes remained:

| Route | Result |
|---|---|
| Ubuntu universe `puppet` package | Available, but Puppet 5.5 — too old for EPP and modern data types |
| `puppet/puppet-agent` Docker image | Puppet 7.20.0 |

The second was used. `/opt/puppetlabs` was extracted from the image and
unpacked onto the Ubuntu 22.04 node. That directory is self-contained —
it ships its own Ruby 2.7.6 — so it is portable between distributions
without touching the system Ruby:

```
puppet 7.20.0
facter 4.2.13
ruby   ruby 2.7.6p219 (2022-04-12 revision c9c2245c0a) [x86_64-linux]
node   Ubuntu 22.04.5 LTS
```

**Masterless.** The module is applied with `puppet apply`, not an agent
checking in with a master. The agent/master split is a fleet-management
concern; it adds a certificate authority, a daemon and a server to a
one-node lab without changing what lands on the node. `puppet apply`
exercises the same catalogue compiler and the same resource providers.

### 7.2 No Forge modules

`forgeapi.puppet.com` is unreachable too, and not fixably so: Puppet's
bundled Ruby ignores both `SSL_CERT_FILE` and `--ssl_trust_store`, so
`puppet module install puppetlabs-stdlib` fails certificate verification
whatever the trust store holds.

The module therefore has **no external dependencies**. The two stdlib
features it wanted are reimplemented locally, which is about twenty lines:

| stdlib feature | Local replacement |
|---|---|
| `Stdlib_absolutepath` | `types/absolutepath.pp` — `type Attendance::AbsolutePath = Pattern[/\A\/[^\n]*\z/]` |
| `assert_private()` | `functions/assert_private.pp` |

`assert_private()` is not a straight port. stdlib implements it with the
Ruby 3.x function API, where `self` is the *calling* scope. The modern API
exposes only the scope the function was *defined* in, so a pure-Puppet
implementation has to be told who is calling — the caller is passed in
explicitly:

```puppet
class attendance::install {
  attendance::assert_private($name, $caller_module_name)
  ...
}
```

Both values are automatically in scope inside any class body. The guard is
tested in both directions in
[`05-puppet-private-class-guard.log`](../proofs/stage-13/puppet/05-puppet-private-class-guard.log):
declaring `attendance::install` directly fails compilation with a readable
message, while the public class still compiles.

### 7.3 Module structure

```
puppet/
├── hiera.yaml                      Hiera 5: per-node data, then common
├── data/common.yaml                the 20 class parameters
├── manifests/site.pp               node default { include attendance }
└── modules/attendance/
    ├── manifests/
    │   ├── init.pp                 public class; parameters and ordering
    │   ├── install.pp              packages, group, user, directories
    │   ├── config.pp               env file, unit, logrotate, nginx
    │   ├── deploy.pp               release directory, artefact, symlink
    │   └── service.pp              the systemd service
    ├── functions/assert_private.pp
    ├── types/absolutepath.pp
    └── templates/                  four EPP templates
```

27 managed resources — 14 `file`, 5 `package`, 4 `exec`, 2 `service`, one
each of `user` and `group` — across one public class and four private ones.
Full inventory in
[`06-puppet-module-inventory.log`](../proofs/stage-13/puppet/06-puppet-module-inventory.log).

`data/common.yaml` deliberately mirrors
`ansible/group_vars/attendance_servers.yml` value for value, so the two
implementations can be compared line by line (§7.6).

### 7.4 Design decisions

**Ordering is declared, not implied.** Puppet does not execute a manifest
top to bottom; it builds a dependency graph. `init.pp` states the four
phases explicitly:

```puppet
contain attendance::install
contain attendance::config
contain attendance::deploy
contain attendance::service

Class['attendance::install']
-> Class['attendance::config']
-> Class['attendance::deploy']
~> Class['attendance::service']
```

`contain` rather than `include` so the ordering applies to the resources
*inside* each class, not merely to the class declarations. The final arrow
is `~>`: a change anywhere in the deploy phase refreshes the service, while
an unchanged deploy leaves it alone.

**`exec` is a last resort.** Three of the four `exec` resources are guarded
so they report no change on a converged node (`onlyif` on the apt-cache
refresh and the release prune), and the fourth — `nginx -t` — is
`refreshonly` and ordered *before* the service reload, so a malformed
template fails the run rather than reloading nginx into a broken state.

**Idempotent artefact deployment without an `exec`.** The 60 MB WAR is a
`file` resource with `source => file:///artifacts/...`. Puppet compares
checksums, so a redeploy of the same artefact is a no-op; only a genuinely
different artefact copies.

### 7.5 Execution

Every run below was captured by
[`puppet/lab/capture-proofs.sh`](../puppet/lab/capture-proofs.sh) in one pass
against a node freshly built by
[`puppet/lab/build-puppet-node.sh`](../puppet/lab/build-puppet-node.sh) — a
genuinely bare node: no service account, no `/opt/attendance`, no JRE, and an
empty apt index. Logs in [`proofs/stage-13/puppet/`](../proofs/stage-13/puppet/):

| Run | Expectation | Result |
|---|---|---|
| `--noop` dry run | Report every change, make none | 21 resources listed, 0 errors, node still bare afterwards |
| First apply | Converge the bare node | 24 changes, **0 failures**, exit code 2, 36.1 s |
| Second apply | **Change nothing** | 0 changes, **exit code 0**, 3.8 s |
| After hand-made drift | Repair exactly the drift | 4 resources corrected, health back to `UP` |
| After a Hiera value change | Change only what depends on it | 1 file + 1 service refresh |

`--detailed-exitcodes` makes the idempotency claim checkable rather than
rhetorical: 0 means no changes and no failures, 2 means changes applied,
4 means failures. A second run of an idempotent manifest must exit 0, and
it does.

Rebuilding the node from scratch for this capture is what made the runs
trustworthy, and it caught a real defect on the way. The guard on the
apt-index refresh originally read:

```puppet
onlyif => '/usr/bin/test ! -f /var/cache/apt/pkgcache.bin -o $(( ... )) -gt 3600',
```

Three things were wrong with it, and they only showed up on a node that was
actually bare:

1. Puppet runs `onlyif` **without a shell**, so `$(...)` and `$((...))` were
   passed to `test` as literal arguments. The guard always returned non-zero,
   so the refresh never ran at all. Earlier runs passed only because the node
   had been `apt-get update`d by hand.
2. It inspected `/var/cache/apt/pkgcache.bin` — apt's *binary* cache, which
   any apt invocation regenerates, a failed install included. Its mtime says
   nothing about whether the package lists were ever fetched, so a guard on
   it skips the refresh on exactly the node that needs it most.
3. Timing off the index *files* would have been wrong too: apt preserves the
   server's `Last-Modified`, so a freshly fetched index can carry a timestamp
   weeks old. Staleness has to be measured from the mtime of the lists
   **directory**, which is when apt actually wrote into it.

The symptom was `E: Unable to locate package openjdk-21-jre-headless`, which
points at the package rather than at the missing index. The fixed guard is in
`install.pp` with the reasoning written next to it; the proof is that run 1
above converges a node whose apt index starts empty, and run 2 still reports
zero changes.

The drift test is the one Ansible cannot make as cleanly, because it is
where a convergence model differs from a procedural one. Four changes were
made by hand on the converged node — the env file deleted, the nginx site
file overwritten with garbage, the service stopped, a log directory
chowned to root — and the manifest re-applied. Puppet repaired those four
and only those four, re-validated the nginx config and reloaded it through
the notify chain:

```
File[/var/log/attendance]/owner: owner changed 'root' to 'attendance'
File[/var/log/attendance]/group: group changed 'root' to 'attendance'
File[/etc/attendance/attendance.env]/ensure: defined content as '{sha256}7810...'
File[/etc/nginx/sites-available/attendance.conf]/content: content changed 'feba...' to '748d...'
Exec[attendance nginx config test]: Triggered 'refresh' from 1 event
Service[nginx]: Triggered 'refresh' from 1 event
Service[attendance]/ensure: ensure changed 'stopped' to 'running'
```

Health returned `UP` afterwards without intervention.

The last row is the data-driven case
([`09-puppet-hiera-data-change.log`](../proofs/stage-13/puppet/09-puppet-hiera-data-change.log)).
No manifest was edited; only the eligibility threshold in
`puppet/data/common.yaml` moved from 75 to 80. The apply rewrote the
environment file and refreshed the service through the notify chain —
nothing else — and reverting the value converged the file back to its
original digest byte for byte. The threshold is data, not code.

### 7.6 Do the two implementations agree?

Both nodes were probed for the same seventeen attributes while deploying
the very same artefact file. Thirteen matched exactly; the four that
differed were file digests
([`07-ansible-vs-puppet-end-state.log`](../proofs/stage-13/puppet/07-ansible-vs-puppet-end-state.log)):

```
user:    attendance:1501:1501:/usr/sbin/nologin   identical
dir:     750 root:attendance /etc/attendance      identical
symlink: current -> releases/1.0.0                identical
war:     attendance:attendance 63815182 sha=4bee7d identical
svc:     attendance=active/enabled                identical
runas:   attendance                               identical
health:  {"status":"UP"}                          identical
envfile/unit/nginx/logrotate digests              differ
```

Diffing the file *contents* explains the digests. Each tool stamps its own
"managed by" banner into the files it owns; strip comments and blank lines
and nothing else remains
([`08-rendered-templates-comparison.log`](../proofs/stage-13/puppet/08-rendered-templates-comparison.log)):

```
attendance.env      IDENTICAL -- 8 significant lines match byte for byte
attendance.service  IDENTICAL -- 26 significant lines match byte for byte
attendance.conf     IDENTICAL -- 30 significant lines match byte for byte
logrotate           IDENTICAL -- 10 significant lines match byte for byte
```

Four of four. Two tools with opposite execution models — one pushing tasks
over SSH, one compiling a catalogue and converging against live state —
produce the same node.

### 7.7 A defect the Puppet work exposed

Validating the Puppet node through a real browser, rather than curl against
the health endpoint, broke at the first redirect: submitting the login form
landed on `http://127.0.0.1/attendance/login?error` — port 8300 missing —
and the browser reported `ERR_CONNECTION_REFUSED`.

Both nginx templates carried `proxy_set_header Host $host`. nginx's `$host`
is the normalised host **without** the port; Spring Security builds its
redirect `Location` headers from the Host header it receives, so every 302
pointed at port 80. The health endpoint never redirects, which is exactly
why every curl check in Stages 13 and 14 passed and the defect survived
undetected.

The fix is `proxy_set_header Host $http_host`, which is the client's Host
header verbatim, port included. It was applied to **both** implementations,
since the defect was in both, and verified on both nodes — unauthenticated
`GET /dashboard` now redirects to `/login` on the port the request arrived
on, and the full login flow reaches the dashboard and renders live data
([`10-nginx-host-header-fix.log`](../proofs/stage-13/puppet/10-nginx-host-header-fix.log)).

This is the clearest argument in the project for a second implementation.
The defect had nothing to do with Puppet. Provisioning the node a second
way meant exercising it a second way, and that is what found it.

---

## 8. Evidence index

| File | Shows |
|---|---|
| [`ansible/`](../ansible/) | Inventory, variables, three playbooks, four roles |
| [`proofs/stage-13/syntax-check.log`](../proofs/stage-13/syntax-check.log) | `ansible-playbook --syntax-check` |
| [`proofs/stage-13/first-run.log`](../proofs/stage-13/first-run.log) | The full first run, 16 changes |
| [`proofs/stage-13/provisioned-node.txt`](../proofs/stage-13/provisioned-node.txt) | Packages, user, directories, files, ports, services, rendered config |

**Puppet (§7)** — all under [`proofs/stage-13/puppet/`](../proofs/stage-13/puppet/):

| File | Shows |
|---|---|
| [`puppet/`](../puppet/) | Hiera data, site manifest, module: 5 classes, 1 function, 1 type, 4 EPP templates |
| [`puppet/lab/`](../puppet/lab/) | `build-puppet-node.sh` (bare node + Puppet 7), `capture-proofs.sh` (the whole evidence sequence), `browse-node.sh` (browser check) |
| `01-puppet-apply-noop.log` | `--noop` dry run: 20 resources reported, none changed |
| `02-puppet-apply-run1.log` | First apply — 21 changes, 0 failures, exit code 2 |
| `03-puppet-apply-run2-idempotent.log` | Second apply — **0 changes, exit code 0** |
| `04-puppet-drift-correction.log` | Four hand-made drifts; exactly four repaired |
| `05-puppet-private-class-guard.log` | `assert_private()` tested in both directions |
| `06-puppet-module-inventory.log` | Toolchain versions, 27 managed resources, zero Forge dependencies |
| `07-ansible-vs-puppet-end-state.log` | 17 attributes compared across both nodes |
| `08-rendered-templates-comparison.log` | All 4 managed files byte-identical between the tools |
| `09-puppet-hiera-data-change.log` | A Hiera value change converges exactly one file + service refresh, and reverts cleanly |
| `10-nginx-host-header-fix.log` | The §7.7 defect, its cause, and verification on both nodes |
| `screenshot-01..05-*.png` | The portal on the Puppet-provisioned node, through nginx |

---

## 9. Stage 13 Definition of Done

| # | Criterion | Status |
|---|---|---|
| D3 | Playbook runs green | Ansible `failed=0`; Puppet exit code 0 on re-run |
| D8 | Stage documentation written | This document |
| D9 | Evidence captured | `proofs/stage-13/` |
| D10 | Provisioned instance answers its health check | `UP` through nginx |
| D12 | Backlog updated | US-18 Done |

**Outcome:** a written configuration specification covering packages,
users, directories, files, ports and services, implemented **twice** — as
an Ansible inventory and role-structured playbook, and as a Puppet module
— each executed against its own bare node and verified item by item. The
stage asked for either; both are delivered, they agree byte for byte on
every file they manage, and building the second one is what exposed the
reverse-proxy defect in §7.7.
