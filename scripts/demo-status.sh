#!/usr/bin/env bash
#
# One screen showing the state of every moving part. Run it before you
# present, and again any time an examiner asks "is that actually live?".
set -uo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO"

g() { printf "\033[32m%s\033[0m" "$1"; }
r() { printf "\033[31m%s\033[0m" "$1"; }
y() { printf "\033[33m%s\033[0m" "$1"; }

probe() { # url -> UP / DOWN
  # Two attempts, and a timeout generous enough for a cold one. Spring MVC
  # initialises its DispatcherServlet on the *first* request, so the very
  # first call to an otherwise healthy instance can take several seconds --
  # long enough that a short timeout reports DOWN for an application that is
  # running perfectly, and UP for the same one a moment later.
  if curl -fsS --noproxy '*' --max-time 15 "$1" >/dev/null 2>&1; then g "UP  "; return; fi
  if curl -fsS --noproxy '*' --max-time 15 "$1" >/dev/null 2>&1; then g "UP  "; return; fi
  r "DOWN"
}
env_of() { curl -fsS --noproxy '*' --max-time 15 "$1" 2>/dev/null | sed -n 's/.*"environment":"\([^"]*\)".*/\1/p'; }

printf "\n\033[1mSTUDENT ATTENDANCE PORTAL — demo status\033[0m\n"
printf "%s\n" "$(date '+%Y-%m-%d %H:%M:%S')"

printf "\n\033[1mApplication endpoints\033[0m\n"
printf "  %-24s %-28s %-6s %s\n" "WHAT" "URL" "STATE" "ENV"
for row in \
  "portal (java -jar)|http://127.0.0.1:8080/attendance|" \
  "staging container|http://127.0.0.1:8100/attendance|" \
  "production container|http://127.0.0.1:8101/attendance|" \
  "tomcat staging|http://127.0.0.1:8090/attendance|" \
  "tomcat production|http://127.0.0.1:8091/attendance|" \
  "node (Ansible)|http://127.0.0.1:8200/attendance|attendance-node" \
  "node (Puppet)|http://127.0.0.1:8300/attendance|attendance-node-puppet" ; do
  lbl=${row%%|*}; rest=${row#*|}; url=${rest%|*}; needs=${rest##*|}
  printf "  %-24s %-28s " "$lbl" "${url#http://127.0.0.1}"
  # A provisioned node that was never built on this machine is not a
  # failure, and showing it red next to real failures teaches you to ignore
  # red. Say what it is instead.
  if [ -n "$needs" ] && ! docker ps -aq -f "name=^${needs}$" 2>/dev/null | grep -q .; then
    printf "%s   %s\n" "$(y "n/a ")" "not built here - see proofs/stage-13, stage-14"
    continue
  fi
  probe "$url/actuator/health"
  printf "   %s\n" "$(env_of "$url/actuator/info")"
done

if command -v docker >/dev/null && docker info >/dev/null 2>&1; then
  printf "\n\033[1mContainers\033[0m\n"
  docker ps -a --filter name=attendance --format '  {{.Names}}|{{.Status}}' \
    | sort | while IFS='|' read -r n s; do
        case "$s" in Up*) printf "  %-32s %s\n" "${n#  }" "$(g "$s")";;
                     *)   printf "  %-32s %s\n" "${n#  }" "$(y "$s")";; esac
      done

  printf "\n\033[1mRegistry\033[0m\n"
  # Read the port off the container rather than assuming 5000: start-demo.sh
  # moves the registry to 5001-5003 when 5000 is taken, which on macOS it
  # usually is (AirPlay Receiver).
  # `docker port`, not a Go template over .NetworkSettings.Ports: Docker
  # Desktop publishes on both IPv4 and IPv6, and ranging over that map
  # concatenates both values into "50015001".
  RPORT=$(docker port attendance-registry 5000/tcp 2>/dev/null | head -1 | awk -F: '{print $NF}')
  RPORT=${RPORT:-5000}
  tags=$(curl -fsS --noproxy '*' --max-time 4 "http://127.0.0.1:$RPORT/v2/attendance-portal/tags/list" 2>/dev/null \
         | tr ',' '\n' | grep -oE '[0-9]+\.[0-9]+\.[0-9]+[^"]*' | sort -V)
  if [ -n "$tags" ]; then
    printf "  serving on :%s, %s image tags pushed:\n" "$RPORT" "$(echo "$tags" | wc -l | tr -d ' ')"
    echo "$tags" | sed 's/^/    /'
  else
    printf "  %s\n" "$(y "registry not reachable on :$RPORT")"
  fi

  printf "\n\033[1mJenkins\033[0m\n"
  if docker ps --format '{{.Names}}' | grep -qx attendance-jenkins; then
    builds=$(docker exec attendance-jenkins sh -c 'ls -d /var/jenkins_home/jobs/*/builds/[0-9]* 2>/dev/null | wc -l' 2>/dev/null | tr -d ' ')
    printf "  %s  %s recorded builds\n" "$(g "running")" "${builds:-?}"
  elif docker ps -aq -f name=^attendance-jenkins$ 2>/dev/null | grep -q .; then
    printf "  %s  start it with: docker start attendance-jenkins\n" "$(y "stopped")"
  else
    # Deliberately not presented live, and not a failure. A controller built
    # here would start with no build history, and the history is the whole
    # point: build #10 went red at the Selenium gate with the deploy stages
    # skipped. One green build on a fresh controller proves less than that
    # log does.
    printf "  %s  presented from evidence, by choice\n" "$(y "n/a ")"
    printf "        proofs/stage-10/failed-pipeline.log   the gate blocking a deploy\n"
    printf "        proofs/stage-10/01-failed-build.png   the same, in the UI\n"
    printf "        proofs/stage-15/pipeline-build-17.log a full green run, 11 stages\n"
  fi
fi

printf "\n\033[1mRepository\033[0m\n"
printf "  branch        %s\n" "$(git rev-parse --abbrev-ref HEAD)"
printf "  HEAD          %s\n" "$(git log --oneline -1)"
printf "  tags          %s\n" "$(git tag -l | tr '\n' ' ')"
printf "  tests         %s unit + %s integration (last recorded run)\n" 73 7
printf "  proofs        %s files across %s stages\n" \
  "$(find proofs -type f 2>/dev/null | wc -l | tr -d ' ')" \
  "$(ls -d proofs/*/ 2>/dev/null | wc -l | tr -d ' ')"
echo
