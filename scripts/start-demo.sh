#!/usr/bin/env bash
#
# Brings the project up for a demonstration.
#
# Three tiers, because they cost very different amounts of time and the
# first one is the only one you always need:
#
#   app     the portal itself on :8080                        ~2 min
#   docker  + registry, image, staging and production         ~6 min
#   full    + Jenkins with its pipeline                       ~12 min
#
# Usage:
#   bash scripts/start-demo.sh                # app tier (default)
#   bash scripts/start-demo.sh --tier docker
#   bash scripts/start-demo.sh --tier full
#   bash scripts/start-demo.sh --stop         # stop everything this started
#
# Requires: JDK 21, Maven. Tiers above 'app' also need Docker running.
set -uo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO"

TIER=app
for a in "$@"; do
  case "$a" in
    --tier) shift; TIER="${1:-app}";;
    app|docker|full) [ "${PREV:-}" = "--tier" ] && TIER="$a";;
    --stop) TIER=stop;;
  esac
  PREV="$a"; shift || true
done

APP_PORT=8080
CTX=/attendance
ok()   { printf "  \033[32m✓\033[0m %s\n" "$*"; }
bad()  { printf "  \033[31m✗\033[0m %s\n" "$*"; }
step() { printf "\n\033[1m==> %s\033[0m\n" "$*"; }

# ---------------------------------------------------------------- stop ----
if [ "$TIER" = stop ]; then
  step "Stopping"
  [ -f /tmp/attendance-demo.pid ] && kill "$(cat /tmp/attendance-demo.pid)" 2>/dev/null && ok "portal (java)" && rm -f /tmp/attendance-demo.pid
  docker stop attendance-app-staging attendance-app-production \
              attendance-tomcat-staging attendance-tomcat-production \
              attendance-jenkins attendance-registry \
              attendance-node attendance-node-puppet >/dev/null 2>&1 && ok "containers"
  echo; echo "Stopped. Data volumes are kept, so a restart keeps its records."
  exit 0
fi

# ------------------------------------------------------------ preflight ----
step "Checking prerequisites"
# grep for the version line rather than taking the first: a JAVA_TOOL_OPTIONS
# banner, if the environment sets one, is printed ahead of it.
JV=$(java -version 2>&1 | grep -iE '(openjdk|java) version' | head -1 | grep -oE '"[0-9]+' | tr -d '"')
if [ -z "$JV" ]; then bad "java not found - install JDK 21"; exit 1
elif [ "$JV" -lt 21 ]; then bad "java $JV found, need 21 or newer"; exit 1
else ok "java $JV"; fi
command -v mvn >/dev/null && ok "maven $(mvn -v 2>/dev/null | head -1 | awk '{print $3}')" || { bad "maven not found"; exit 1; }
if [ "$TIER" != app ]; then
  docker info >/dev/null 2>&1 && ok "docker" || { bad "docker is not running - start Docker Desktop"; exit 1; }
fi

# ---------------------------------------------------------------- build ----
step "Building the application"
# sed, not grep -v: under `set -o pipefail` a grep that filters out every
# line exits 1 and would fail the pipeline even though Maven succeeded.
if mvn -q -pl app -DskipTests package 2>&1 | sed '/Picked up JAVA_TOOL_OPTIONS/d' | tail -5; then
  WAR=$(ls -1 app/target/*.war 2>/dev/null | grep -v original | head -1)
  [ -n "$WAR" ] && ok "packaged $(basename "$WAR") ($(du -h "$WAR" | cut -f1))" || { bad "no war produced"; exit 1; }
else
  bad "build failed"; exit 1
fi

# ------------------------------------------------------------ app tier ----
step "Starting the portal on :$APP_PORT"
if curl -fsS --noproxy '*' "http://127.0.0.1:$APP_PORT$CTX/actuator/health" >/dev/null 2>&1; then
  ok "already running"
else
  # JAVA_TOOL_OPTIONS is cleared for the app's own JVM. Some build and CI
  # environments set it to route the JVM through an HTTP proxy, which makes
  # /actuator/info hang on its outbound lookup while /actuator/health still
  # answers -- a confusing half-up state. A developer machine normally has it
  # unset, so this is a no-op there.
  ( unset JAVA_TOOL_OPTIONS; nohup java -jar "$WAR" > /tmp/attendance-demo.log 2>&1 & echo $! > /tmp/attendance-demo.pid )
  printf "  waiting for health "
  for i in $(seq 1 60); do
    curl -fsS --noproxy '*' "http://127.0.0.1:$APP_PORT$CTX/actuator/health" >/dev/null 2>&1 && break
    printf "."; sleep 2
  done
  echo
  if curl -fsS --noproxy '*' "http://127.0.0.1:$APP_PORT$CTX/actuator/health" >/dev/null 2>&1; then
    ok "portal UP  (log: /tmp/attendance-demo.log)"
  else
    bad "portal did not come up - see /tmp/attendance-demo.log"; tail -20 /tmp/attendance-demo.log; exit 1
  fi
fi

# --------------------------------------------------------- docker tier ----
if [ "$TIER" = docker ] || [ "$TIER" = full ]; then
  step "Registry"
  if docker ps --format '{{.Names}}' | grep -qx attendance-registry; then ok "already running"
  else
    docker start attendance-registry >/dev/null 2>&1 \
      || docker run -d --name attendance-registry -p 5000:5000 \
           -v docker_registry_data:/var/lib/registry registry:2 >/dev/null
    sleep 2; ok "registry on :5000"
  fi

  step "Image"
  if docker images --format '{{.Repository}}:{{.Tag}}' | grep -qx "attendance-portal:1.0.0"; then
    ok "attendance-portal:1.0.0 present"
  else
    docker build -q -t attendance-portal:1.0.0 . >/dev/null && ok "built attendance-portal:1.0.0"
  fi
  docker tag attendance-portal:1.0.0 localhost:5000/attendance-portal:1.0.0 2>/dev/null
  docker push -q localhost:5000/attendance-portal:1.0.0 >/dev/null 2>&1 && ok "pushed to the local registry"

  step "Containers: staging and production"
  for pair in "staging:8100" "production:8101"; do
    envn=${pair%%:*}; port=${pair##*:}
    name="attendance-app-$envn"
    if docker ps --format '{{.Names}}' | grep -qx "$name"; then ok "$name already running"
    elif docker start "$name" >/dev/null 2>&1; then ok "$name restarted on :$port"
    else
      docker run -d --name "$name" -p "$port:8080" \
        -e ATTENDANCE_ENVIRONMENT="$envn" \
        -e SERVER_SERVLET_CONTEXT_PATH=/attendance \
        -e ATTENDANCE_ELIGIBILITY_THRESHOLD=75 \
        -e "SPRING_DATASOURCE_URL=jdbc:h2:file:/app/data/attendance;DB_CLOSE_ON_EXIT=FALSE" \
        -v "attendance-data-$envn:/app/data" \
        localhost:5000/attendance-portal:1.0.0 >/dev/null && ok "$name started on :$port"
    fi
  done
fi

# ----------------------------------------------------------- full tier ----
if [ "$TIER" = full ]; then
  step "Jenkins"
  if docker ps --format '{{.Names}}' | grep -qx attendance-jenkins; then ok "already running"
  elif docker start attendance-jenkins >/dev/null 2>&1; then ok "restarted"
  else bad "no attendance-jenkins container - see jenkins/build-controller.sh"; fi
  if [ "$(uname -s)" = Darwin ]; then
    printf "  \033[33m!\033[0m macOS: the controller uses network_mode host, which Docker\n"
    printf "    Desktop does not support. Use the committed evidence in\n"
    printf "    proofs/stage-07..10/ for the Jenkins part of the demo.\n"
  fi
fi

# --------------------------------------------------------------- report ----
step "Ready"
printf "\n  %-34s %s\n" "PORTAL (run this one)" "http://localhost:$APP_PORT$CTX/login"
if [ "$TIER" = docker ] || [ "$TIER" = full ]; then
  printf "  %-34s %s\n" "staging container"    "http://localhost:8100/attendance/login"
  printf "  %-34s %s\n" "production container" "http://localhost:8101/attendance/login"
  printf "  %-34s %s\n" "registry tags"        "http://localhost:5000/v2/attendance-portal/tags/list"
fi
[ "$TIER" = full ] && printf "  %-34s %s\n" "Jenkins (Linux only)" "http://localhost:8081/"
cat <<'EOF'

  Sign in with any of:
    admin1    / Admin@123      administrator
    hod1      / Hod@12345      head of department, approves records
    faculty1  / Faculty@123    records attendance
    student1  / Student@123    sees only their own approved records

  Status at any time:  bash scripts/demo-status.sh
  Stop everything:     bash scripts/start-demo.sh --stop
EOF
