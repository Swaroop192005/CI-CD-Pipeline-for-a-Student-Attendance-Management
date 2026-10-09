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
if curl -fsS --noproxy '*' --max-time 5 "http://127.0.0.1:$APP_PORT$CTX/actuator/health" >/dev/null 2>&1; then
  ok "already running"
elif lsof -ti ":$APP_PORT" >/dev/null 2>&1; then
  # Something holds the port but does not answer our health endpoint. Most
  # often a JVM left behind by a previous run that was suspended with Ctrl+Z
  # rather than stopped. Starting a second one here would bind-fail and exit
  # immediately, and the health loop below would then spin for two minutes
  # against a port that is never going to answer. Say so and stop instead.
  bad "port $APP_PORT is held by something that is not answering $CTX/actuator/health:"
  lsof -i ":$APP_PORT" 2>/dev/null | sed '1d' | awk '{printf "      %s (pid %s), owner %s\n", $1, $2, $3}' | sort -u
  echo
  echo "    Free it and run this again:"
  echo "        kill \$(lsof -ti :$APP_PORT)"
  echo
  # A process suspended with Ctrl+Z is in state T. SIGTERM is queued for it
  # but not acted on until it resumes, so a plain kill looks like it did
  # nothing and the same pid is still holding the port on the next run.
  # SIGKILL cannot be blocked or deferred.
  for _p in $(lsof -ti ":$APP_PORT" 2>/dev/null); do
    if [ "$(ps -o stat= -p "$_p" 2>/dev/null | cut -c1)" = "T" ]; then
      printf "    \033[33mNote:\033[0m pid %s is *stopped* (state T), almost certainly suspended\n" "$_p"
      echo "    with Ctrl+Z. A plain kill will not reach it -- use:"
      echo "        kill -9 \$(lsof -ti :$APP_PORT)"
      echo
    fi
  done
  echo "    If you suspended an earlier run with Ctrl+Z, that is almost"
  echo "    certainly what this is. 'jobs' lists it; 'kill -9 %1' ends it."
  echo "    Use Ctrl+C, not Ctrl+Z, to stop a script: Ctrl+Z only suspends it"
  echo "    and leaves the application holding the port."
  exit 1
else
  # JAVA_TOOL_OPTIONS is cleared for the app's own JVM. Some build and CI
  # environments set it to route the JVM through an HTTP proxy, which makes
  # /actuator/info hang on its outbound lookup while /actuator/health still
  # answers -- a confusing half-up state. A developer machine normally has it
  # unset, so this is a no-op there.
  ( unset JAVA_TOOL_OPTIONS; nohup java -jar "$WAR" > /tmp/attendance-demo.log 2>&1 & echo $! > /tmp/attendance-demo.pid )
  APP_PID=$(cat /tmp/attendance-demo.pid)
  printf "  waiting for health "
  for i in $(seq 1 60); do
    curl -fsS --noproxy '*' --max-time 5 "http://127.0.0.1:$APP_PORT$CTX/actuator/health" >/dev/null 2>&1 && break
    # Stop waiting the moment the JVM is gone -- otherwise a process that
    # died on startup costs two minutes of dots before anyone sees the error.
    if ! kill -0 "$APP_PID" 2>/dev/null; then
      echo; bad "the application exited during startup. Last lines of /tmp/attendance-demo.log:"
      tail -15 /tmp/attendance-demo.log | sed 's/^/      /'
      exit 1
    fi
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
  # The registry is recreated rather than coaxed, because its storage is the
  # named volume docker_registry_data -- images pushed earlier survive the
  # container being thrown away. That makes "remove and recreate" the safe
  # repair for every broken state, and there are several: a container that
  # exists but was created without -p (it runs, and nothing can reach it), one
  # stopped with a stale port binding, or a half-made container left by a
  # create that failed.
  #
  # Correctness is judged by whether the registry API answers on the host
  # port, never by whether a container is running.
  REGISTRY_PORT="${REGISTRY_PORT:-5000}"
  REGISTRY_UP=no

  registry_answers() {  # $1 = host port
    curl -fsS --noproxy '*' --max-time 3 "http://127.0.0.1:$1/v2/" >/dev/null 2>&1
  }
  published_port() {    # the host port the container actually publishes, if any
    # `docker port`, not a Go template over .NetworkSettings.Ports: Docker
    # Desktop publishes a port on both IPv4 and IPv6, so ranging over that map
    # concatenates the two values and yields "50015001" rather than "5001".
    docker port attendance-registry 5000/tcp 2>/dev/null | head -1 | awk -F: '{print $NF}'
  }

  # Already serving? Adopt whatever port it is on and leave it alone.
  EXISTING=$(published_port)
  if [ -n "$EXISTING" ] && registry_answers "$EXISTING"; then
    REGISTRY_PORT="$EXISTING"; REGISTRY_UP=yes
    ok "already serving on :$REGISTRY_PORT"
  else
    [ -n "$(docker ps -aq -f name=^attendance-registry$)" ] && \
      docker rm -f attendance-registry >/dev/null 2>&1

    # Find a port the registry can actually have. 5000 is the convention, but
    # on macOS it is usually taken by AirPlay Receiver, which is not worth
    # making anyone turn off to see a demo.
    CHOSEN=""
    for try in "$REGISTRY_PORT" 5001 5002 5003; do
      [ -n "$CHOSEN" ] && break
      lsof -ti ":$try" >/dev/null 2>&1 && continue
      ERR=$(docker run -d --name attendance-registry -p "$try:5000" \
              -v docker_registry_data:/var/lib/registry registry:2 2>&1 >/dev/null)
      if [ -z "$ERR" ]; then
        for _ in $(seq 1 15); do registry_answers "$try" && break; sleep 1; done
        if registry_answers "$try"; then CHOSEN="$try"; else
          docker rm -f attendance-registry >/dev/null 2>&1
        fi
      else
        docker rm -f attendance-registry >/dev/null 2>&1
      fi
    done

    if [ -n "$CHOSEN" ]; then
      REGISTRY_PORT="$CHOSEN"; REGISTRY_UP=yes
      if [ "$REGISTRY_PORT" = 5000 ]; then ok "registry serving on :5000"
      else
        ok "registry serving on :$REGISTRY_PORT"
        printf "    \033[33mnote\033[0m :5000 was taken, so the registry moved to :%s.\n" "$REGISTRY_PORT"
        printf "         On macOS :5000 is usually AirPlay Receiver (System Settings ->\n"
        printf "         General -> AirDrop & Handoff). Nothing here depends on 5000.\n"
      fi
    else
      bad "registry could not be started on 5000-5003"
      [ -n "$ERR" ] && echo "$ERR" | sed 's/^/      /'
      echo "      Carrying on without it. The portal and both environment"
      echo "      containers are unaffected; proofs/stage-12/ evidences the"
      echo "      registry hop."
    fi
  fi

  step "Image"
  if docker images --format '{{.Repository}}:{{.Tag}}' | grep -qx "attendance-portal:1.0.0"; then
    ok "attendance-portal:1.0.0 present"
  else
    docker build -q -t attendance-portal:1.0.0 . >/dev/null && ok "built attendance-portal:1.0.0"
  fi
  if [ "$REGISTRY_UP" = yes ]; then
    docker tag attendance-portal:1.0.0 "localhost:$REGISTRY_PORT/attendance-portal:1.0.0" 2>/dev/null
    if docker push -q "localhost:$REGISTRY_PORT/attendance-portal:1.0.0" >/dev/null 2>&1; then
      ok "pushed to the local registry"
    else
      bad "push to the local registry failed (the image itself is fine)"
    fi
  else
    printf "  \033[33m-\033[0m skipped the push: no registry\n"
  fi

  step "Containers: staging and production"
  # Pull from the registry when it is up, so the demo shows the real path:
  # built image -> registry -> deployed container. With no registry, run the
  # same image from the local daemon rather than failing -- the point of the
  # two containers is one image serving two environments, which holds either
  # way, and the registry hop is evidenced in proofs/stage-12/.
  if [ "$REGISTRY_UP" = yes ]; then IMG="localhost:$REGISTRY_PORT/attendance-portal:1.0.0"
  else IMG=attendance-portal:1.0.0; fi
  for pair in "staging:8100" "production:8101"; do
    envn=${pair%%:*}; port=${pair##*:}
    name="attendance-app-$envn"
    if docker ps --format '{{.Names}}' | grep -qx "$name"; then
      ok "$name already running"
      continue
    fi
    if docker ps -a --format '{{.Names}}' | grep -qx "$name"; then
      ERR=$(docker start "$name" 2>&1 >/dev/null)
    else
      ERR=$(docker run -d --name "$name" -p "$port:8080" \
        -e ATTENDANCE_ENVIRONMENT="$envn" \
        -e SERVER_SERVLET_CONTEXT_PATH=/attendance \
        -e ATTENDANCE_ELIGIBILITY_THRESHOLD=75 \
        -e "SPRING_DATASOURCE_URL=jdbc:h2:file:/app/data/attendance;DB_CLOSE_ON_EXIT=FALSE" \
        -v "attendance-data-$envn:/app/data" \
        "$IMG" 2>&1 >/dev/null)
    fi
    if docker ps --format '{{.Names}}' | grep -qx "$name"; then
      ok "$name on :$port"
    else
      bad "$name did not start"
      [ -n "$ERR" ] && echo "$ERR" | sed 's/^/      /'
      echo "$ERR" | grep -qiE "port is already allocated|address already in use" \
        && echo "      Something already holds :$port -- check with: lsof -i :$port"
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
  [ "$REGISTRY_UP" = yes ] && printf "  %-34s %s\n" "registry tags" \
      "http://localhost:$REGISTRY_PORT/v2/attendance-portal/tags/list"
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
