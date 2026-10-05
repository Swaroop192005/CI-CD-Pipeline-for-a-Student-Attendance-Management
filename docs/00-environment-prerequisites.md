# Environment Prerequisites — Readiness Check

Run before Stage 4, so that no stage is blocked half-way by a missing
tool. Every row below was verified by executing the command shown; the
captured output is in `proofs/stage-00/tool-versions.txt`.

## 1. Required toolchain

| # | Requirement | Needed by stage | Verify command | Result |
|---|---|---|---|---|
| 1 | JDK 21 | 3–15 | `java -version` | **OK** — OpenJDK 21.0.11 |
| 2 | Apache Maven 3.9+ | 3–15 | `mvn -v` | **OK** — Maven 3.9.11 |
| 3 | Git 2.30+ | 4–15 | `git --version` | **OK** |
| 4 | Docker Engine + CLI | 11–14 | `docker --version` | **OK** — 29.6.2 |
| 5 | Docker daemon reachable | 11–14 | `docker info` | **OK** after starting `dockerd` (see §3.1) |
| 6 | Docker Compose v2 | 12–14 | `docker compose version` | **OK** — v5.3.1 |
| 7 | Jenkins LTS with JDK 21 | 7–12 | `docker run jenkins/jenkins:lts-jdk21` | **OK** via official image (see §3.2) |
| 8 | Chromium / Chrome | 9, 10 | `chrome --version` | **OK** — Chromium 141.0.7390.37 |
| 9 | ChromeDriver, same major version | 9, 10 | `chromedriver --version` | **Fixed** — see §3.3 |
| 10 | Ansible core 2.15+ | 13, 14 | `ansible --version` | **Installed** — see §3.4 |
| 11 | Maven Central reachable | 3–15 | `curl -I https://repo.maven.apache.org/maven2/` | **OK** |
| 12 | Docker Hub reachable | 7, 11, 12 | `docker pull eclipse-temurin:21-jre-jammy` | **OK** |
| 13 | Local Docker registry | 12 | `docker run registry:2` | **OK** |

## 2. Baseline machine

| Property | Value |
|---|---|
| OS kernel | Linux 6.18 (x86-64) |
| vCPU | 4 |
| Memory | 15.7 GiB |
| Container runtime | Docker 29.6.2, overlayfs storage driver, BuildKit enabled |
| Privileges | root (required to start `dockerd` and bind port 80) |

## 3. Gaps found and how each was closed

### 3.1 Docker daemon not running

`docker info` initially failed with
`dial unix /var/run/docker.sock: connect: no such file or directory`.
The CLI was installed but no daemon was started.

```bash
nohup dockerd > /var/log/dockerd.log 2>&1 &
sleep 10
docker info | tail -5          # Server section now present
docker run --rm hello-world    # end-to-end check
```

The daemon reports `storage-driver=overlayfs`,
`containerd-snapshotter=true` and completes BuildKit initialisation, so
image builds (Stage 11) and the local registry (Stage 12) are available.

### 3.2 Jenkins download host blocked

`https://get.jenkins.io` is refused by the lab's egress policy (HTTP 403
at the proxy), so the usual `jenkins.war` download and the Debian package
repository are both unavailable.

**Resolution:** install Jenkins from its official container image, which
is served by Docker Hub and *is* reachable:

```bash
docker pull jenkins/jenkins:lts-jdk21
```

This is a supported installation method, keeps the controller version
pinned, and has the side benefit that the Jenkins home directory is a
named volume, so Stage 7's job configuration survives restarts. Every
Stage 7–12 deliverable is produced against this controller.

### 3.3 ChromeDriver / browser major-version mismatch

The pre-installed driver was ChromeDriver 147 while the available browser
was Chromium 141. ChromeDriver refuses to drive a browser of a different
major version, which would have failed every Stage 9 test.

```bash
# Discover the driver build matching the installed browser major version
curl -s https://googlechromelabs.github.io/chrome-for-testing/known-good-versions-with-downloads.json

# Install the matching 141 driver
curl -o chromedriver.zip \
  https://storage.googleapis.com/chrome-for-testing-public/141.0.7390.122/linux64/chromedriver-linux64.zip
unzip -j chromedriver.zip 'chromedriver-linux64/chromedriver' -d /usr/local/bin
chmod +x /usr/local/bin/chromedriver
chromedriver --version        # ChromeDriver 141.0.7390.122
```

Both the browser and the driver are now pinned to major version 141, and
the Selenium suite is configured with explicit `webdriver.chrome.driver`
and browser-binary paths rather than relying on auto-resolution. This
directly serves NFR-14 and removes the most common cause of browser-test
flakiness.

### 3.4 Neither Ansible nor Puppet installed

Stage 13 permits either tool. **Both were installed, and both are
delivered.**

**Ansible** was straightforward — PyPI is reachable, and it is agentless,
so there is nothing to install on the target node:

```bash
pip install ansible-core      # ansible [core 2.19.13]
ansible --version
```

**Puppet** needed a second attempt. Its package repository
(`apt.puppet.com`) is blocked by the same egress policy that blocks the
Jenkins download site, which is what stopped the first try. Two routes
remained: the Ubuntu universe package (Puppet 5.5 — too old for EPP and
modern data types) and the official Docker image. The image was used,
extracting the self-contained `/opt/puppetlabs` tree — it ships its own
Ruby 2.7.6, so it is portable onto the Ubuntu 22.04 target without
touching the system Ruby:

```bash
docker create --name p puppet/puppet-agent:latest
docker export p | tar -x -C /tmp opt/puppetlabs    # -> puppet 7.20.0
```

One limitation remains and is not fixable: `forgeapi.puppet.com` is
unreachable, and Puppet's bundled Ruby ignores both `SSL_CERT_FILE` and
`--ssl_trust_store`, so `puppet module install` cannot verify the Forge
certificate whatever the trust store holds. The module therefore carries
**no Forge dependencies**; the two `stdlib` features it needed
(`Stdlib_absolutepath` and `assert_private()`) are reimplemented locally
in about twenty lines. See `docs/stage-13-configuration-management.md` §7.2.

**Decision:** Stage 13/14 are delivered with Ansible as the primary
implementation (inventory plus a YAML playbook with roles) **and** a
Puppet module implementing the same specification, applied masterless
with `puppet apply`. Both are executed against their own bare node and
verified; the two end states are compared in §7.6 of the Stage 13
document.

## 4. Pinned versions used throughout the project

| Component | Pinned version |
|---|---|
| OpenJDK | 21.0.11 |
| Apache Maven | 3.9.11 |
| Spring Boot | 3.5.16 |
| Apache Tomcat (deploy target) | 10.1 |
| Selenium Java | 4.50.0 |
| Chromium | 141.0.7390.37 |
| ChromeDriver | 141.0.7390.122 |
| Docker Engine | 29.6.2 |
| Jenkins | `jenkins/jenkins:lts-jdk21` |
| Docker registry | `registry:2` |
| Ansible core | 2.19.13 |

## 5. Known environment limitations

These are environmental, not defects in the application. Each is carried
into the Stage 15 troubleshooting guide with its workaround.

| # | Limitation | Workaround in use |
|---|---|---|
| L1 | `get.jenkins.io` blocked by egress policy | Jenkins installed from its official Docker image |
| L2 | `apt.puppet.com` and `forgeapi.puppet.com` blocked by egress policy | Puppet 7.20.0 extracted from the official Docker image instead; the module carries no Forge dependencies, reimplementing the two `stdlib` features it needed locally |
| L3 | No managed container registry available | Local `registry:2` on port 5000 |
| L4 | Single node — no separate CI and target hosts | Target node provisioned as a container; Ansible connects over the Docker connection plugin |
| L5 | Pre-installed ChromeDriver mismatched the browser | Version-matched driver installed to `/usr/local/bin` |
| L6 | Outbound HTTPS is proxied with TLS re-termination | Builds use the pre-configured CA bundle; no verification is disabled |
