#!/usr/bin/env bash
#
# Brings up a bare node with Puppet 7 on it, ready for `puppet apply`.
#
# Puppet is installed from apt.puppet.com where that is reachable, which is
# both the normal way and the only one that gives a native build on arm64.
# Where it is refused -- as it was on the network this project was built on
# -- the script falls back to extracting /opt/puppetlabs from the official
# container image, a self-contained tree that ships its own Ruby. That
# image is published for linux/amd64 only, so the fallback is amd64-only.
#
# The Ubuntu universe package is not used either way: it is Puppet 5.5, too
# old for EPP and the modern data types this module relies on.
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

echo "==> Installing Puppet"
# Preferred route: the official apt repository, which publishes puppet-agent
# for arm64 as well as amd64, so the node runs natively on Apple Silicon.
# This is also simply the normal way to install Puppet.
#
# Fallback: extract /opt/puppetlabs from the official container image. That
# tree is self-contained (it ships its own Ruby), but it is published only
# for linux/amd64, so on an arm64 host it needs emulation. It exists here
# because the network this project was built on refuses apt.puppet.com.
PUPPET_INSTALLED=no
if docker exec "$NODE" bash -c '
      set -e
      apt-get update -qq >/dev/null 2>&1 || true
      apt-get install -y -qq curl ca-certificates >/dev/null 2>&1 || true
      curl -fsS --max-time 60 -o /tmp/puppet-release.deb \
        https://apt.puppet.com/puppet7-release-jammy.deb
      dpkg -i /tmp/puppet-release.deb >/dev/null
      apt-get update -qq
      DEBIAN_FRONTEND=noninteractive apt-get install -y -qq puppet-agent
   ' >/tmp/puppet-install.log 2>&1; then
  PUPPET_INSTALLED=apt
  echo "    installed from apt.puppet.com (native $(docker exec "$NODE" dpkg --print-architecture))"
else
  echo "    apt.puppet.com did not work from the node; falling back to the image."
  # Say why. The two common causes look identical in a bare failure and are
  # not equally interesting: a TLS-intercepting proxy whose CA the node does
  # not trust, versus the host simply being unreachable.
  if grep -qi "self-signed certificate\|unable to get local issuer" /tmp/puppet-install.log 2>/dev/null; then
    echo "    Reason: TLS interception -- the node does not trust the proxy CA."
  elif grep -qi "could not resolve\|connection refused\|timed out" /tmp/puppet-install.log 2>/dev/null; then
    echo "    Reason: the node cannot reach apt.puppet.com."
  fi
  echo "    Full output: /tmp/puppet-install.log"
  HOST_ARCH="$(uname -m)"
  if [ "$HOST_ARCH" = arm64 ] || [ "$HOST_ARCH" = aarch64 ]; then
    echo "    !! this host is $HOST_ARCH and $PUPPET_IMAGE is linux/amd64 only;"
    echo "       the extracted binaries will not run here."
  fi
  STAGE="$(mktemp -d)"
  trap 'rm -rf "$STAGE"' EXIT
  CID="$(docker create "$PUPPET_IMAGE")"
  docker export "$CID" | tar -x -C "$STAGE" opt/puppetlabs
  docker rm -f "$CID" >/dev/null
  tar -C "$STAGE" -cf - opt/puppetlabs | docker exec -i "$NODE" tar -C / -xf -
  PUPPET_INSTALLED=image
fi

docker exec "$NODE" ln -sf /opt/puppetlabs/bin/puppet  /usr/local/bin/puppet
docker exec "$NODE" ln -sf /opt/puppetlabs/bin/facter  /usr/local/bin/facter

if ! docker exec "$NODE" /opt/puppetlabs/bin/puppet --version >/dev/null 2>&1; then
  echo "!! puppet is not runnable on the node" >&2
  [ "$PUPPET_INSTALLED" = image ] && echo "   (an amd64 build on a non-amd64 host will not execute)" >&2
  exit 1
fi

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
