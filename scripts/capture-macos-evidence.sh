#!/usr/bin/env bash
#
# Captures evidence for one specific claim: that this project's pipeline
# and lab run on a second, unrelated machine - the developer's own Mac -
# and not only in the Linux container the project was built in.
#
# It writes to proofs/stage-15/macos/ and touches nothing else. The other
# 111 proof files record when each stage was done and are deliberately
# left alone: re-dating them would misrepresent the project's timeline,
# and most of them record events (a merge conflict, a blocked build, a
# rollback against a corrupt artefact) that happened once.
#
# usage: bash scripts/capture-macos-evidence.sh [jenkins-build-number]
#
set -uo pipefail          # not -e: a missing component should be recorded,
                          # not abort the capture

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
out="$repo_root/proofs/stage-15/macos"
mkdir -p "$out"

green(){ printf '\033[32m%s\033[0m' "$1"; }
red()  { printf '\033[31m%s\033[0m' "$1"; }
dim()  { printf '\033[2m%s\033[0m'  "$1"; }

ok=0; missing=0

note() {   # note <file> <label>
  if [ -s "$2" ]; then
    echo "  $(green "✓") $1"
    ok=$((ok+1))
  else
    echo "  $(red "·") $1 $(dim "(nothing captured)")"
    rm -f "$2"
    missing=$((missing+1))
  fi
}

echo "Capturing second-machine evidence into proofs/stage-15/macos/"
echo

# --- 1. what machine this is -------------------------------------------
# The whole point of this directory is that it is a different machine, so
# record that first and in the system's own words.
{
  echo "# Host"
  echo
  date
  echo
  uname -a
  echo
  if command -v sw_vers >/dev/null 2>&1; then sw_vers; fi
  echo
  echo "# Docker"
  echo
  docker version 2>&1
  echo
  docker info --format 'Server: {{.ServerVersion}}   OS: {{.OperatingSystem}}   Arch: {{.Architecture}}   Driver: {{.Driver}}' 2>&1
} > "$out/01-host-and-docker.txt" 2>&1
note "01-host-and-docker.txt" "$out/01-host-and-docker.txt"

# --- 2. the lab, as the status script sees it ---------------------------
if [ -x "$repo_root/scripts/demo-status.sh" ] || [ -f "$repo_root/scripts/demo-status.sh" ]; then
  # Strip the colour codes: a log full of escape sequences is unreadable
  # in a browser, which is where an examiner will open it.
  bash "$repo_root/scripts/demo-status.sh" 2>&1 \
    | sed -E 's/\x1b\[[0-9;]*m//g' > "$out/02-lab-status.txt"
  note "02-lab-status.txt" "$out/02-lab-status.txt"
fi

# --- 3. the containers themselves --------------------------------------
docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}' \
  > "$out/03-running-containers.txt" 2>&1
note "03-running-containers.txt" "$out/03-running-containers.txt"

# --- 4. each endpoint, in its own words --------------------------------
# A health check proves it answers. /actuator/info proves *which*
# configuration answered, which is the stronger claim.
{
  for spec in \
      "portal:8080" \
      "container-staging:8100" \
      "container-production:8101" \
      "tomcat-staging:8090" \
      "tomcat-production:8091" \
      "ansible-node:8200" \
      "puppet-node:8300"
  do
    name=${spec%%:*}; port=${spec##*:}
    printf '%-22s :%s  ' "$name" "$port"
    health=$(curl -sf --max-time 5 "http://localhost:$port/attendance/actuator/health" 2>/dev/null)
    if [ -n "$health" ]; then
      info=$(curl -sf --max-time 5 "http://localhost:$port/attendance/actuator/info" 2>/dev/null)
      printf '%s  %s\n' "$health" "$info"
    else
      printf 'not reachable\n'
    fi
  done
} > "$out/04-endpoint-health.txt" 2>&1
note "04-endpoint-health.txt" "$out/04-endpoint-health.txt"

# --- 5. the registry's accumulated tags --------------------------------
# Proof that images were pushed here repeatedly, not once.
reg_port=$(docker port attendance-registry 2>/dev/null | head -1 | awk -F: '{print $NF}')
if [ -n "${reg_port:-}" ]; then
  {
    echo "# Registry on localhost:$reg_port"
    echo
    curl -s --max-time 5 "http://localhost:$reg_port/v2/_catalog"
    echo
    curl -s --max-time 5 "http://localhost:$reg_port/v2/attendance-portal/tags/list"
    echo
  } > "$out/05-registry-tags.txt" 2>&1
  note "05-registry-tags.txt" "$out/05-registry-tags.txt"
else
  echo "  $(red "·") 05-registry-tags.txt $(dim "(registry not running)")"
  missing=$((missing+1))
fi

# --- 6. the pipeline build log -----------------------------------------
if docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "${JENKINS_CONTAINER:-attendance-jenkins}"; then
  bash "$repo_root/scripts/capture-pipeline-proof.sh" ${1:+"$1"} 2>&1 \
    | sed -E 's/\x1b\[[0-9;]*m//g' | sed 's/^/      /'
else
  echo "  $(red "·") pipeline log $(dim "(Jenkins not running - start it, run a build, then re-run this)")"
  missing=$((missing+1))
fi

echo
echo "$ok captured, $missing unavailable."
echo
echo "Review them before committing - they describe your machine:"
echo "  open proofs/stage-15/macos/"
echo
echo "Then:"
echo "  git add proofs/stage-15/ && git commit -m 'docs(proofs): second-machine evidence from macOS'"
