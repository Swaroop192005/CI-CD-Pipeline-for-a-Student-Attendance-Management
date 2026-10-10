#!/usr/bin/env bash
#
# Copy a Jenkins build's console log out of the running controller and into
# proofs/, so the evidence survives the container.
#
# The log is read straight from JENKINS_HOME rather than over HTTP: that
# path needs no authentication and no network, and it is the same bytes the
# web console serves.
#
# Usage:
#   bash scripts/capture-pipeline-proof.sh            # newest build
#   bash scripts/capture-pipeline-proof.sh 5          # build #5
#   bash scripts/capture-pipeline-proof.sh 5 attendance-portal-ci
#
set -euo pipefail

CONTAINER=${JENKINS_CONTAINER:-attendance-jenkins}
JOB=${2:-attendance-portal-pipeline}
BUILD=${1:-}

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
out_dir="$repo_root/proofs/stage-15"

red()  { printf '\033[31m%s\033[0m' "$1"; }
green(){ printf '\033[32m%s\033[0m' "$1"; }

if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
  echo "$(red "✗") the Jenkins container '$CONTAINER' is not running."
  echo "  start it with: bash jenkins/start-local.sh"
  exit 1
fi

builds_dir="/var/jenkins_home/jobs/$JOB/builds"

if ! docker exec "$CONTAINER" test -d "$builds_dir"; then
  echo "$(red "✗") no job '$JOB' on this controller. Jobs present:"
  docker exec "$CONTAINER" sh -c 'ls /var/jenkins_home/jobs 2>/dev/null' | sed 's/^/    /'
  exit 1
fi

# Newest build = highest numeric directory name, not the newest mtime:
# mtime changes when a build is merely viewed.
if [[ -z "$BUILD" ]]; then
  BUILD=$(docker exec "$CONTAINER" sh -c \
      "ls $builds_dir | grep -E '^[0-9]+\$' | sort -n | tail -1")
  if [[ -z "$BUILD" ]]; then
    echo "$(red "✗") job '$JOB' has no builds yet."
    exit 1
  fi
  echo "No build given; using the newest, #$BUILD."
fi

log="$builds_dir/$BUILD/log"
if ! docker exec "$CONTAINER" test -f "$log"; then
  echo "$(red "✗") build #$BUILD of '$JOB' has no console log."
  exit 1
fi

# Report the build's own verdict rather than inferring one, and keep it in
# the filename so a green and a red capture cannot be confused later.
result=$(docker exec "$CONTAINER" sh -c \
    "grep -oE 'Finished: [A-Z]+' '$log' | tail -1 | awk '{print \$2}'" || true)
result=${result:-UNKNOWN}

case "$result" in
  SUCCESS) suffix="" ;;
  UNKNOWN) suffix="-incomplete" ;;
  *)       suffix="-$(echo "$result" | tr '[:upper:]' '[:lower:]')" ;;
esac

mkdir -p "$out_dir"
dest="$out_dir/macos-pipeline-build-$(printf '%02d' "$BUILD")${suffix}.log"

docker exec "$CONTAINER" cat "$log" > "$dest"

lines=$(wc -l < "$dest" | tr -d ' ')
size=$(du -h "$dest" | cut -f1)

if [[ "$result" == "SUCCESS" ]]; then
  echo "$(green "✓") captured build #$BUILD of '$JOB' — $result"
else
  echo "$(red "·") captured build #$BUILD of '$JOB' — $result"
fi
echo "  ${dest#"$repo_root"/}  ($lines lines, $size)"

if [[ "$result" != "SUCCESS" ]]; then
  echo
  echo "  Note: this build did not succeed, so the filename records that."
  echo "  docs/VIVA-DEMO-GUIDE.md and the final report cite the green run as"
  echo "  proofs/stage-15/macos-pipeline-build-05.log."
fi

echo
echo "Commit it:"
echo "  git add ${dest#"$repo_root"/} && git commit -m 'docs(proofs): console log for the macOS pipeline run'"
