#!/usr/bin/env bash
#
# Brings up a bare node with Puppet 7 on it, ready for `puppet apply`.
#
# Puppet's apt repository (apt.puppet.com) is refused by this lab's egress
# policy, and the Ubuntu universe package is Puppet 5.5 -- too old for EPP
# and the modern data types this module uses. So Puppet is taken from the
# official container image instead: /opt/puppetlabs there is self-contained
# (it ships its own Ruby), which makes it portable onto the Ubuntu 22.04
# target without touching the system Ruby.
#
# The node itself is the same deliberately-bare image the Ansible target
# uses, built by ansible/targetnode/build-node.sh -- anything it arrives
# with is something the manifest does not get to prove it can provision.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"

NODE="${NODE:-attendance-node-puppet}"
IMAGE="${IMAGE:-attendance-target-node:1.0}"
PUPPET_IMAGE="${PUPPET_IMAGE:-puppet/puppet-agent:latest}"
APP_VERSION="${APP_VERSION:-1.0.0}"
# Same convention as ansible/group_vars/attendance_servers.yml
# (app_artifact_source: /artifacts/attendance-{{ app_version }}.war), so both
# implementations deploy the identical artefact and can be compared byte for
# byte. Falls back to the Maven output when nothing is staged there yet.
if [ -f "/artifacts/attendance-$APP_VERSION.war" ]; then
  ARTIFACT="${ARTIFACT:-/artifacts/attendance-$APP_VERSION.war}"
else
  ARTIFACT="${ARTIFACT:-$REPO/app/target/attendance.war}"
fi
SSH_PORT="${SSH_PORT:-2223}"
HTTP_PORT="${HTTP_PORT:-8300}"
APP_PORT="${APP_PORT:-8301}"

echo "==> Removing any previous $NODE"
docker rm -f "$NODE" >/dev/null 2>&1 || true

echo "==> Starting a bare node from $IMAGE"
docker run -d --name "$NODE" \
  --cgroupns=host --tmpfs /run --tmpfs /run/lock \
  -v /sys/fs/cgroup:/sys/fs/cgroup:rw \
  -p "$SSH_PORT:22" -p "$HTTP_PORT:80" -p "$APP_PORT:8080" \
  "$IMAGE" >/dev/null
for _ in $(seq 1 30); do
  docker exec "$NODE" systemctl is-system-running >/dev/null 2>&1 && break
  sleep 1
done

# Lab plumbing, not part of the Stage 13 specification: the node image bakes
# in Acquire::https::Proxy "http://127.0.0.1:40387", but inside the container
# 127.0.0.1 is the node itself. ansible/lab/proxy-relay.py listens on the
# bridge gateway and relays to the session's loopback proxy; point apt there.
# Ansible does the same thing in a pre_task in site.yml.
RELAY_PORT="${RELAY_PORT:-40388}"
GATEWAY="${GATEWAY:-172.17.0.1}"
# The session's proxy port is not stable across restarts, so take it from the
# environment rather than hardcoding it, and restart the relay if it is
# pointing somewhere stale.
UPSTREAM="$(printf '%s' "${HTTPS_PROXY:-http://127.0.0.1:40387}" | sed -E 's#^[a-z]+://##; s#/$##')"
if ! pgrep -f "[p]roxy-relay.py $RELAY_PORT $UPSTREAM" >/dev/null 2>&1; then
  pkill -f "[p]roxy-relay.py" >/dev/null 2>&1 || true
  echo "==> Starting the proxy relay: 0.0.0.0:$RELAY_PORT -> $UPSTREAM"
  nohup python3 "$REPO/ansible/lab/proxy-relay.py" "$RELAY_PORT" "$UPSTREAM" >/tmp/proxy-relay.log 2>&1 &
  sleep 2
fi
echo "==> Pointing the node's apt at the relay ($GATEWAY:$RELAY_PORT)"
docker exec "$NODE" sh -c \
  "printf 'Acquire::https::Proxy \"http://$GATEWAY:$RELAY_PORT\";\n' > /etc/apt/apt.conf.d/01proxy"

echo "==> Extracting /opt/puppetlabs from $PUPPET_IMAGE"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
CID="$(docker create "$PUPPET_IMAGE")"
docker export "$CID" | tar -x -C "$STAGE" opt/puppetlabs
docker rm -f "$CID" >/dev/null

echo "==> Installing Puppet onto $NODE"
tar -C "$STAGE" -cf - opt/puppetlabs | docker exec -i "$NODE" tar -C / -xf -
docker exec "$NODE" ln -sf /opt/puppetlabs/bin/puppet  /usr/local/bin/puppet
docker exec "$NODE" ln -sf /opt/puppetlabs/bin/facter  /usr/local/bin/facter

echo "==> Staging the build artefact"
docker exec "$NODE" mkdir -p /artifacts
if [ -f "$ARTIFACT" ]; then
  docker cp "$ARTIFACT" "$NODE:/artifacts/attendance-$APP_VERSION.war"
else
  echo "    !! $ARTIFACT not found -- stage it in /artifacts or run 'mvn -q package -DskipTests'" >&2
  exit 1
fi

echo "==> Copying the module tree to /puppet"
docker exec "$NODE" rm -rf /puppet
docker cp "$REPO/puppet" "$NODE:/puppet"

echo
docker exec "$NODE" /opt/puppetlabs/bin/puppet --version | sed 's/^/puppet /'
docker exec "$NODE" /opt/puppetlabs/bin/facter --version | sed 's/^/facter /'
echo
echo "Ready. Apply with:"
echo "  docker exec $NODE bash -c 'cd /puppet && puppet apply \\"
echo "      --modulepath=modules --hiera_config=hiera.yaml \\"
echo "      --detailed-exitcodes manifests/site.pp'"
