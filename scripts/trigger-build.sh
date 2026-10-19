#!/usr/bin/env bash
# Trigger the pipeline and report what happened.
#
# Written because the equivalent curl is three commands that fail in three
# different ways, each with a message that does not name the real cause:
#
#   * No cookie jar -> HTTP 403 "No valid crumb was included in the
#     request". Jenkins binds the crumb to a session, so a crumb fetched by
#     one request and presented by another is not valid.
#   * Jenkins still starting -> the crumb endpoint returns an HTML error
#     page, that HTML goes into the -H header, and Jetty answers HTTP 400
#     "Illegal character VCHAR='<'" -- a complaint about the header, with
#     nothing to say that the controller was simply not up yet.
#   * A parameter the job does not define is ignored silently, so a typo
#     looks like it worked.
#
# Usage:
#   bash scripts/trigger-build.sh                        # pipeline, defaults
#   bash scripts/trigger-build.sh --env staging --port 8100
#   bash scripts/trigger-build.sh --job attendance-portal-ci --no-selenium
set -euo pipefail

JENKINS="${JENKINS_URL:-http://localhost:8081}"
USER_PASS="${JENKINS_AUTH:-admin:admin}"
JOB=attendance-portal-pipeline
ENVIRONMENT=local
PORT=8090
SELENIUM=true
WAIT_SECONDS=120

while [ $# -gt 0 ]; do
  case "$1" in
    --job) shift; JOB="${1:?--job needs a name}";;
    --env) shift; ENVIRONMENT="${1:?--env needs a value}";;
    --port) shift; PORT="${1:?--port needs a value}";;
    --no-selenium) SELENIUM=false;;
    -h|--help) sed -n '2,24p' "$0"; exit 0;;
    *) echo "unknown option: $1" >&2; exit 2;;
  esac
  shift
done

say()  { printf '  %s\n' "$*"; }
ok()   { printf '  \033[32m✓\033[0m %s\n' "$*"; }
bad()  { printf '  \033[31m✗\033[0m %s\n' "$*"; }

# -g disables curl's URL globbing. Jenkins' tree queries contain [ and ],
# which curl otherwise reads as a range: the request never leaves, it fails
# with "bad range in URL", and a 2>/dev/null on the caller turns that into
# an empty answer that looks like a legitimate "no parameters".
jcurl() { curl -g -sS --noproxy '*' -u "$USER_PASS" "$@"; }

printf '\n\033[1mTriggering %s\033[0m\n' "$JOB"

# ---- wait for the controller -------------------------------------------
# A restart takes tens of seconds, and every failure below is unreadable if
# it is really just "not up yet".
printf '  waiting for Jenkins '
ready=no
for _ in $(seq 1 $((WAIT_SECONDS / 3))); do
  if curl -sf --noproxy '*' --max-time 3 "$JENKINS/login" >/dev/null 2>&1; then
    ready=yes; break
  fi
  printf '.'; sleep 3
done
echo
if [ "$ready" != yes ]; then
  bad "Jenkins did not answer on $JENKINS within ${WAIT_SECONDS}s"
  say "is the container running?  docker ps -f name=attendance-jenkins"
  exit 1
fi
ok "Jenkins is up"

# ---- crumb, in a session the POST will reuse ---------------------------
COOKIE=$(mktemp)
trap 'rm -f "$COOKIE"' EXIT

# /login answering does not mean the controller has finished starting: it
# serves that page while the crumb issuer is still a 503. So the crumb is
# retried rather than demanded once, which is what makes "restart, then
# trigger" work instead of failing on the first try.
printf '  asking for a crumb '
CRUMB=
for _ in $(seq 1 $((WAIT_SECONDS / 3))); do
  CRUMB=$(jcurl -c "$COOKIE" \
    "$JENKINS/crumbIssuer/api/xml?xpath=concat(//crumbRequestField,\":\",//crumb)" 2>/dev/null || true)
  case "$CRUMB" in
    *Crumb:*) break ;;
  esac
  CRUMB=
  printf '.'; sleep 3
done
echo

# Checked rather than trusted: an HTML error page here becomes an illegal
# header further down, and the resulting 400 blames the header.
if [ -z "${CRUMB:-}" ]; then
  bad "the crumb endpoint never returned a crumb"
  say "last answer: $(jcurl "$JENKINS/crumbIssuer/api/xml" 2>/dev/null | tr -d '\n' | cut -c1-120)"
  say "if that is an error page, the controller is still starting"
  exit 1
fi
ok "crumb issued"

# ---- does the job define the parameters we are about to send? ----------
# Not silenced. The previous version sent this to /dev/null, so a request
# that never ran was indistinguishable from a job with no parameters, and
# the script confidently posted to the wrong endpoint.
params=$(jcurl "$JENKINS/job/$JOB/api/json?tree=property[parameterDefinitions[name]]") || {
  bad "could not read the job's parameters from $JENKINS/job/$JOB/"
  say "does the job exist?  $JENKINS/view/all/"
  exit 1
}
case "$params" in
  *parameterDefinitions*name*) PARAMETERISED=yes ;;
  *) PARAMETERISED=no ;;
esac

# What branch will this actually build? Worth printing before the build
# rather than reading it out of the console afterwards: the job is defined
# by casc.yaml, and a controller that did not reload its configuration goes
# on building the previous branch while looking perfectly healthy.
# Scoped to the BranchSpec. Matching <name> anywhere in config.xml finds
# the first parameter definition instead, which is how this line came to
# announce "configured branch: DEPLOY_ENVIRONMENT".
branch=$(jcurl "$JENKINS/job/$JOB/config.xml" 2>/dev/null | tr -d '\n' \
         | sed -n 's|.*<hudson.plugins.git.BranchSpec>[[:space:]]*<name>\([^<]*\)</name>.*|\1|p')
[ -n "${branch:-}" ] && say "configured branch: $branch"

if [ "$PARAMETERISED" = yes ]; then
  URL="$JENKINS/job/$JOB/buildWithParameters?DEPLOY_ENVIRONMENT=$ENVIRONMENT&APP_PORT=$PORT&RUN_SELENIUM=$SELENIUM"
  say "parameters: environment=$ENVIRONMENT port=$PORT selenium=$SELENIUM"
else
  # The freestyle job takes none. Posting to buildWithParameters there
  # succeeds and quietly ignores everything, which reads as success.
  URL="$JENKINS/job/$JOB/build"
  say "this job takes no parameters; triggering a plain build"
fi

# -H "$CRUMB" is the whole point of the two steps above. Without it the
# POST is refused with 403 no matter how carefully the crumb was fetched
# and checked, and the script that fetched it reports the refusal as if
# something else were wrong.
code=$(jcurl -b "$COOKIE" -H "$CRUMB" -X POST "$URL" -o /dev/null -w '%{http_code}')
if [ "$code" = 201 ] || [ "$code" = 200 ]; then
  ok "queued (HTTP $code)"
else
  bad "the trigger was refused (HTTP $code)"
  case "$code" in
    403) say "403 here is usually /build posted to a job that requires"
         say "parameters, or a crumb from a different session." ;;
    404) say "no such job: $JOB" ;;
    405) say "this job does not accept that trigger; try the other endpoint" ;;
  esac
  say "url was: $URL"
  exit 1
fi

# ---- report where it went ----------------------------------------------
say "watch it:  $JENKINS/job/$JOB/"
printf '  waiting for a build number '
for _ in $(seq 1 20); do
  n=$(jcurl "$JENKINS/job/$JOB/lastBuild/api/json?tree=number,building" 2>/dev/null \
      | sed -n 's/.*"number":\([0-9]*\).*/\1/p')
  if [ -n "${n:-}" ]; then
    echo
    ok "build #$n started"
    say "follow:  curl -sN --noproxy '*' -u $USER_PASS $JENKINS/job/$JOB/lastBuild/logText/progressiveText"
    exit 0
  fi
  printf '.'; sleep 3
done
echo
say "queued, but no build number yet -- it may be waiting for an executor"
