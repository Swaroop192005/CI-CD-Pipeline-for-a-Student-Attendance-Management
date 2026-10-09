#!/usr/bin/env bash
#
# Brings up the provisioned target node locally and runs the Ansible
# playbook against it -- the Stage 13/14 part of the demo, which otherwise
# only exists as recorded evidence.
#
# This is the slowest and least portable tier, so it is a separate script
# rather than part of start-demo.sh. Budget 10-15 minutes the first time.
#
# Requires: Docker, and Ansible on this machine (brew install ansible, or
# pip3 install ansible-core).
#
#   bash scripts/start-nodes.sh            # Ansible node on :8200
#   bash scripts/start-nodes.sh --puppet   # also try the Puppet node (see below)
#   bash scripts/start-nodes.sh --stop
set -uo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO"
ok()   { printf "  \033[32m✓\033[0m %s\n" "$*"; }
bad()  { printf "  \033[31m✗\033[0m %s\n" "$*"; }
warn() { printf "  \033[33m!\033[0m %s\n" "$*"; }
step() { printf "\n\033[1m==> %s\033[0m\n" "$*"; }

WANT_PUPPET=no
for a in "$@"; do
  case "$a" in
    --puppet) WANT_PUPPET=yes;;
    --stop)
      step "Stopping the nodes"
      docker rm -f attendance-node attendance-node-puppet >/dev/null 2>&1 && ok "removed"
      exit 0;;
  esac
done

step "Checking prerequisites"
docker info >/dev/null 2>&1 && ok "docker" || { bad "docker is not running"; exit 1; }
if command -v ansible-playbook >/dev/null 2>&1; then
  ok "ansible $(ansible --version 2>/dev/null | head -1 | grep -oE '[0-9]+\.[0-9]+(\.[0-9]+)?' | head -1)"
else
  bad "ansible-playbook not found. Install it with one of:"
  echo "        brew install ansible"
  echo "        pip3 install --user ansible-core"
  exit 1
fi
WAR=$(ls -1 "$REPO"/app/target/*.war 2>/dev/null | grep -v original | head -1)
if [ -z "$WAR" ]; then
  bad "no built artefact. Run this first:  mvn -q -pl app -DskipTests package"; exit 1
fi
ok "artefact $(basename "$WAR")"

step "Building the bare node image"
# build-node.sh adds proxy build args only when HTTPS_PROXY is set, and the
# Dockerfile guards every proxy step, so on an ordinary machine this builds a
# plain Ubuntu node that uses the normal mirrors.
if bash ansible/targetnode/build-node.sh >/tmp/node-build.log 2>&1; then
  ok "attendance-target-node:1.0"
else
  bad "image build failed; last lines of /tmp/node-build.log:"; tail -15 /tmp/node-build.log | sed 's/^/      /'; exit 1
fi

step "Starting the node (systemd as PID 1)"
docker rm -f attendance-node >/dev/null 2>&1
ERR=$(docker run -d --name attendance-node \
        --privileged --cgroupns=host \
        -v /sys/fs/cgroup:/sys/fs/cgroup:rw --tmpfs /run --tmpfs /run/lock \
        -p 2222:22 -p 8200:80 -p 8201:8080 \
        attendance-target-node:1.0 2>&1 >/dev/null)
if [ -n "$ERR" ]; then bad "could not start the node"; echo "$ERR" | sed 's/^/      /'; exit 1; fi
printf "  waiting for systemd and sshd "
for _ in $(seq 1 45); do
  docker exec attendance-node systemctl is-system-running >/dev/null 2>&1 \
    && docker exec attendance-node systemctl is-active ssh >/dev/null 2>&1 && break
  printf "."; sleep 2
done
echo
if docker exec attendance-node systemctl is-active ssh >/dev/null 2>&1; then
  ok "node up, sshd listening on :2222"
else
  bad "systemd did not come up inside the container"
  docker logs attendance-node 2>&1 | tail -10 | sed 's/^/      /'
  warn "systemd in a container needs --privileged and cgroup access; some"
  warn "Docker Desktop versions are awkward about it. The recorded runs are"
  warn "in proofs/stage-13/ and proofs/stage-14/."
  exit 1
fi

step "Staging the artefact where the playbook expects it"
# app_artifact_source defaults to /artifacts/attendance-<version>.war on the
# *control* host. Point it at the build output instead of asking anyone to
# create /artifacts on their own machine.
ok "using $WAR"

step "Running the playbook"
# The inventory's proxy_url is the lab relay address, which exists only in
# the container this project was built in. An ordinary machine reaches the
# Ubuntu mirrors directly, and the playbook skips the apt-proxy task when
# the value is blank -- so decide by whether this environment actually has
# an egress proxy rather than assuming either way.
if [ -n "${HTTPS_PROXY:-}" ]; then
  RELAY_PORT="${RELAY_PORT:-40388}"
  GATEWAY="${GATEWAY:-172.17.0.1}"
  UPSTREAM="$(printf '%s' "$HTTPS_PROXY" | sed -E 's#^[a-z]+://##; s#/$##')"
  # Start a relay only if nothing is listening yet. Deliberately no pkill:
  # matching on a command line is unreliable -- a pattern like proxy-relay.py
  # also matches any shell whose own command line happens to contain it,
  # including the one running this script.
  if ! (exec 3<>/dev/tcp/127.0.0.1/$RELAY_PORT) 2>/dev/null; then
    nohup python3 "$REPO/ansible/lab/proxy-relay.py" "$RELAY_PORT" "$UPSTREAM" >/tmp/proxy-relay.log 2>&1 &
    sleep 2
  fi
  PROXY_ARG="http://$GATEWAY:$RELAY_PORT"
  warn "this environment has an egress proxy; relaying it to the node at $PROXY_ARG"
else
  PROXY_ARG=""
  ok "no egress proxy here, the node will reach the mirrors directly"
fi
export ANSIBLE_HOST_KEY_CHECKING=False
export ANSIBLE_FORCE_COLOR=0
if (cd ansible && ansible-playbook -i inventory.ini site.yml \
      -e proxy_url="$PROXY_ARG" -e "app_artifact_source=$WAR" 2>&1) | tee /tmp/node-provision.log | tail -18
then :; fi
if grep -qE "failed=[1-9]|unreachable=[1-9]" /tmp/node-provision.log; then
  bad "the playbook reported failures -- full log in /tmp/node-provision.log"
  exit 1
fi

step "Verifying"
for _ in $(seq 1 30); do
  curl -fsS --noproxy '*' --max-time 3 http://127.0.0.1:8200/attendance/actuator/health >/dev/null 2>&1 && break
  sleep 2
done
if curl -fsS --noproxy '*' --max-time 5 http://127.0.0.1:8200/attendance/actuator/health >/dev/null 2>&1; then
  ok "provisioned node serving on :8200"
  echo "      $(curl -fsS --noproxy '*' --max-time 5 http://127.0.0.1:8200/attendance/actuator/info)"
  echo
  echo "  Run it a second time to show idempotency -- expect changed=0:"
  echo "      cd ansible && ansible-playbook -i inventory.ini site.yml \\"
  echo "          -e proxy_url='$PROXY_ARG' -e app_artifact_source=$WAR"
else
  bad "the node did not answer on :8200; see /tmp/node-provision.log"
fi

if [ "$WANT_PUPPET" = yes ]; then
  step "Puppet node"
  ARCH=$(uname -m)
  if [ "$ARCH" = arm64 ] || [ "$ARCH" = aarch64 ]; then
    warn "this machine is $ARCH, and puppet/puppet-agent is published only for"
    warn "linux/amd64. Both the Puppet tree and the node would have to run"
    warn "under emulation, which is slow and frequently breaks systemd."
    warn "Skipping. The Puppet runs are evidenced in proofs/stage-13/puppet/,"
    warn "including the idempotency and drift-correction logs."
  else
    bash puppet/lab/build-puppet-node.sh && \
    docker exec attendance-node-puppet bash -c \
      'export PATH=/opt/puppetlabs/bin:$PATH; cd /puppet && puppet apply \
         --modulepath=modules --hiera_config=hiera.yaml --detailed-exitcodes manifests/site.pp'
  fi
fi
