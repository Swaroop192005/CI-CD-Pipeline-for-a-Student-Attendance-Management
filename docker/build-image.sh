#!/usr/bin/env bash
#
# Builds the application image around the WAR Maven produced.
#
# Packages the artefact rather than rebuilding it, so the image contains
# exactly the binary the tests and the quality gate ran against. See the
# comment at the top of the Dockerfile.
#
# usage: docker/build-image.sh [tag]
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"

TAG="${1:-attendance-portal:dev}"
WAR="$ROOT/app/target/attendance.war"

if [ ! -f "$WAR" ]; then
  echo "No artefact at $WAR - packaging first."
  (cd "$ROOT" && mvn -B -pl app -am -DskipTests package)
fi

GIT_COMMIT="$(git -C "$ROOT" rev-parse --short HEAD 2>/dev/null || echo unknown)"
BUILD_TIME="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
APP_VERSION="${APP_VERSION:-${TAG##*:}}"

echo "Building ${TAG}  (version ${APP_VERSION}, commit ${GIT_COMMIT}, war $(du -h "$WAR" | cut -f1))"
docker build \
  --build-arg "APP_VERSION=${APP_VERSION}" \
  --build-arg "GIT_COMMIT=${GIT_COMMIT}" \
  --build-arg "BUILD_TIME=${BUILD_TIME}" \
  -t "$TAG" -f "$ROOT/Dockerfile" "$ROOT"

echo "Built ${TAG}"
docker images "${TAG%%:*}" --format '  {{.Repository}}:{{.Tag}}  {{.Size}}'
