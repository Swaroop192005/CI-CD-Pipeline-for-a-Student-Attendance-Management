#!/usr/bin/env bash
#
# Builds the bare target-node image and generates the SSH key Ansible uses
# to reach it.
#
# The node is deliberately minimal - the playbook's job is to turn it into
# an application server, so anything it arrives with is something Stage 14
# does not get to prove it can provision.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KEY="$HERE/id_node"
IMAGE="${IMAGE:-attendance-target-node:1.0}"

if [ ! -f "$KEY" ]; then
  echo "Generating an SSH key for the control node"
  ssh-keygen -t ed25519 -N '' -C 'ansible@attendance-control' -f "$KEY" >/dev/null
fi
cp "$KEY.pub" "$HERE/authorized_keys"

# Two certificate files, for two different jobs:
#   bootstrap-bundle.crt - a complete trust store, because apt's GnuTLS
#                          backend will not accept a store holding only the
#                          proxy's CA
#   proxy-ca.crt         - the proxy CA alone, installed the proper way
#                          through update-ca-certificates once apt works
CA_BUNDLE="${CA_BUNDLE:-/root/.ccr/ca-bundle.crt}"
CA_SOURCE="${CA_SOURCE:-/root/.ccr/agent-proxy-ca.crt}"
cleanup() {
  rm -f "$HERE/certs/proxy-ca.crt" "$HERE/certs/bootstrap-bundle.crt" "$HERE/authorized_keys"
}
trap cleanup EXIT
[ -f "$CA_BUNDLE" ] && cp "$CA_BUNDLE" "$HERE/certs/bootstrap-bundle.crt"
[ -f "$CA_SOURCE" ] && cp "$CA_SOURCE" "$HERE/certs/proxy-ca.crt"

BUILD_ARGS=()
if [ -n "${HTTPS_PROXY:-}" ]; then
  BUILD_ARGS+=(--network host
               --build-arg "APT_PROXY=${HTTPS_PROXY}"
               --build-arg "HTTPS_PROXY_ENV=${HTTPS_PROXY}")
fi

docker build "${BUILD_ARGS[@]}" -t "$IMAGE" "$HERE"
echo "Built $IMAGE"
