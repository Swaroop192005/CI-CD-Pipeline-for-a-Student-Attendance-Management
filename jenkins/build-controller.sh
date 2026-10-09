#!/usr/bin/env bash
#
# Builds the Jenkins controller image.
#
# Handles the two things the lab network makes necessary and an ordinary
# network does not:
#
#   1. The plugin manager must reach updates.jenkins.io through the
#      session's HTTPS proxy, which listens on loopback only - hence
#      --network host.
#   2. That proxy re-terminates TLS, so its CA has to be in the image's
#      JVM truststore - hence copying the bundle into jenkins/certs for
#      the duration of the build.
#
# On a network with neither, both steps are harmless no-ops.
set -euo pipefail

IMAGE="${IMAGE:-attendance-jenkins:1.0}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CA_SOURCE="${CA_SOURCE:-/root/.ccr/agent-proxy-ca.crt}"
CA_STAGED="$HERE/certs/proxy-ca.crt"

cleanup() { rm -f "$CA_STAGED"; }
trap cleanup EXIT

BUILD_ARGS=()
if [ -n "${HTTPS_PROXY:-}" ]; then
  echo "Using HTTPS proxy: $HTTPS_PROXY"
  # The proxy listens on loopback, so the build needs the host network
  # namespace to reach it at all.
  #
  # HTTPS_PROXY is passed for curl and friends, and the same endpoint is
  # passed again as JVM system properties because the JVM does not read
  # that environment variable - without them the plugin manager connects
  # directly and fails with a PKIX error that looks like a truststore
  # problem but is not one.
  proxy_host="$(echo "$HTTPS_PROXY" | sed -E 's|^[a-z]+://||; s|:.*$||')"
  proxy_port="$(echo "$HTTPS_PROXY" | sed -E 's|^.*:||; s|/.*$||')"
  BUILD_ARGS+=(--network host
               --build-arg "HTTPS_PROXY=$HTTPS_PROXY"
               --build-arg "https_proxy=$HTTPS_PROXY"
               --build-arg "JVM_PROXY_OPTS=-Dhttps.proxyHost=${proxy_host} -Dhttps.proxyPort=${proxy_port} -Dhttp.proxyHost=${proxy_host} -Dhttp.proxyPort=${proxy_port}"
               --build-arg "APT_PROXY=$HTTPS_PROXY")
fi

if [ -f "$CA_SOURCE" ]; then
  echo "Staging $CA_SOURCE for the image truststore"
  cp "$CA_SOURCE" "$CA_STAGED"
fi

docker build "${BUILD_ARGS[@]}" -t "$IMAGE" -f "$HERE/Dockerfile" "$HERE"
echo "Built $IMAGE"
