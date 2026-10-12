#!/usr/bin/env bash
#
# Builds and starts the Jenkins controller on an ordinary machine.
#
#   bash jenkins/start-local.sh          # build if needed, then start
#   bash jenkins/start-local.sh --rebuild
#   bash jenkins/start-local.sh --stop
#
# Takes 5-10 minutes the first time: the image installs ~40 plugins, the
# docker client and Maven.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ok()   { printf "  \033[32m✓\033[0m %s\n" "$*"; }
bad()  { printf "  \033[31m✗\033[0m %s\n" "$*"; }
step() { printf "\n\033[1m==> %s\033[0m\n" "$*"; }

case "${1:-}" in
  --stop)
    docker compose -f "$HERE/docker-compose.local.yml" down >/dev/null 2>&1 \
      && ok "stopped (the home volume is kept, so builds survive)"; exit 0;;
  --rebuild) REBUILD=yes;;
  *) REBUILD=no;;
esac

step "Checking prerequisites"
docker info >/dev/null 2>&1 && ok "docker" || { bad "docker is not running"; exit 1; }
docker compose version >/dev/null 2>&1 && ok "docker compose" \
  || { bad "docker compose not available"; exit 1; }

step "Image"
if [ "$REBUILD" = yes ] || ! docker image inspect attendance-jenkins:1.0 >/dev/null 2>&1; then
  echo "  building (5-10 min: ~40 plugins, docker client, Maven)..."
  # Through build-controller.sh, so a machine behind an egress proxy gets
  # the build args it needs. On an ordinary machine it adds none.
  bash "$HERE/build-controller.sh" >/tmp/jenkins-build.log 2>&1
  # Same trap as the node image: a buildx builder on the docker-container
  # driver reports success while leaving its output in the build cache, so
  # the build passes and the image is nowhere the daemon can see it.
  if ! docker image inspect attendance-jenkins:1.0 >/dev/null 2>&1; then
    echo "  the build reported success but the image is not in the local store;"
    echo "  rebuilding with --load"
    docker build --load -t attendance-jenkins:1.0 "$HERE" >>/tmp/jenkins-build.log 2>&1
  fi
fi
docker image inspect attendance-jenkins:1.0 >/dev/null 2>&1 \
  && ok "attendance-jenkins:1.0 ($(docker image inspect attendance-jenkins:1.0 --format '{{.Architecture}}'))" \
  || { bad "image missing after build; last lines of /tmp/jenkins-build.log:"
       tail -25 /tmp/jenkins-build.log | sed 's/^/      /'
       echo
       echo "      If that mentions buildx or 'failed to solve', try:"
       echo "          docker buildx use default"
       exit 1; }

step "Starting the controller"
docker compose -f "$HERE/docker-compose.local.yml" up -d >/dev/null 2>&1
printf "  waiting for Jenkins to answer "
for _ in $(seq 1 60); do
  curl -fsS --noproxy '*' --max-time 3 http://127.0.0.1:8081/login >/dev/null 2>&1 && break
  printf "."; sleep 3
done
echo
if curl -fsS --noproxy '*' --max-time 10 http://127.0.0.1:8081/login >/dev/null 2>&1; then
  ok "Jenkins is up at http://localhost:8081/   (admin / admin)"
  jobs=$(docker exec attendance-jenkins sh -c 'ls /var/jenkins_home/jobs 2>/dev/null' 2>/dev/null | tr '\n' ' ')
  [ -n "$jobs" ] && echo "      jobs defined by Configuration-as-Code: $jobs"
  # Report the history this controller actually has. The build record
  # lives in the jenkins_home volume, so it survives restarts of the
  # container and grows every run - a fixed "no build history" line went
  # stale the moment the first build finished.
  builds=$(docker exec attendance-jenkins sh -c \
      'ls -d /var/jenkins_home/jobs/*/builds/[0-9]* 2>/dev/null | wc -l' 2>/dev/null | tr -d ' ')
  if [ "${builds:-0}" -gt 0 ]; then
    echo "      build history on this controller: $builds recorded"
    echo
    echo "  SCM polling runs every 2 minutes, so a build may start on its own"
    echo "  shortly after startup if there are commits it has not built yet."
    echo "  Let it finish before starting the lab nodes: the quality gate adds"
    echo "  a JVM and a Chromium, and the two together can exhaust Docker's"
    echo "  memory allocation."
  else
    cat <<'EOF'
      no build history yet -- trigger one from the UI to give this
      controller a run of its own. proofs/stage-10/ holds the run where the
      quality gate blocked a deployment, which is the thing worth showing.
EOF
  fi
else
  bad "Jenkins did not answer on :8081"
  docker logs attendance-jenkins 2>&1 | tail -15 | sed 's/^/      /'
fi
