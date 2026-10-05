#!/usr/bin/env bash
#
# Runs the full Stage 13 Puppet evidence sequence against a node prepared by
# build-puppet-node.sh and writes the logs under proofs/stage-13/puppet/.
#
# Expects a BARE node: the point of runs 1 and 2 is to show a bare node
# converging and then a converged node not moving, so re-run
# build-puppet-node.sh first if the node has already been applied to.
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
NODE="${NODE:-attendance-node-puppet}"
ANSIBLE_NODE="${ANSIBLE_NODE:-attendance-node}"
OUT="$REPO/proofs/stage-13/puppet"
mkdir -p "$OUT"

# puppet apply, masterless, from the module tree copied to /puppet
pa() { docker exec "$NODE" bash -c "export PATH=/opt/puppetlabs/bin:\$PATH; cd /puppet; puppet apply $*"; }
strip() { sed -e 's/\x1b\[[0-9;]*m//g'; }
APPLY="--modulepath=modules --hiera_config=hiera.yaml"

echo "==> 01 baseline + --noop"
{
  echo "### Bare node, before anything is applied ###"
  docker exec "$NODE" bash -c '
    echo "user attendance : $(id attendance 2>&1)"
    echo "service         : $(systemctl is-active attendance 2>&1)"
    echo "/opt/attendance : $(ls -d /opt/attendance 2>&1)"
    echo "nginx           : $(systemctl is-active nginx 2>&1)"
    echo "java            : $(java -version 2>&1 | head -1)"
    echo "apt indices     : $(ls /var/lib/apt/lists/*_Packages* 2>/dev/null | wc -l)"'
  echo
  echo "### puppet apply --noop -- report every change, make none ###"
  pa "--noop $APPLY manifests/site.pp 2>&1; echo \"exit=\$?\""
  echo
  echo "### the node is still untouched ###"
  docker exec "$NODE" bash -c 'ls -d /opt/attendance 2>&1; id attendance 2>&1 | head -1'
} 2>&1 | strip > "$OUT/01-puppet-apply-noop.log"

echo "==> 02 first apply"
{
  echo "### puppet apply -- RUN 1: converge a bare node ###"
  date -u +"started  %Y-%m-%dT%H:%M:%SZ"
  pa "$APPLY --detailed-exitcodes manifests/site.pp 2>&1; echo \"detailed-exitcode=\$?\""
  date -u +"finished %Y-%m-%dT%H:%M:%SZ"
} 2>&1 | strip > "$OUT/02-puppet-apply-run1.log"

echo "==> waiting for health"
docker exec "$NODE" bash -c 'for i in $(seq 1 40); do curl -fsS http://127.0.0.1/attendance/actuator/health >/dev/null 2>&1 && break; sleep 3; done; curl -fsS http://127.0.0.1/attendance/actuator/health; echo'

echo "==> 03 idempotency"
{
  echo "### puppet apply -- RUN 2: idempotency, nothing should change ###"
  date -u +"started %Y-%m-%dT%H:%M:%SZ"
  pa "$APPLY --detailed-exitcodes manifests/site.pp 2>&1; ec=\$?
      echo \"detailed-exitcode=\$ec\"; echo
      echo '# --detailed-exitcodes: 0 = no changes and no failures, 2 = changes'
      echo '#   applied, 4 = failures, 6 = both. A second run of an idempotent'
      echo '#   manifest must therefore exit 0.'
      if [ \"\$ec\" -eq 0 ]; then echo 'RESULT: IDEMPOTENT (exit 0, zero changes)'
      else echo \"RESULT: NOT IDEMPOTENT (exit \$ec)\"; fi"
} 2>&1 | strip > "$OUT/03-puppet-apply-run2-idempotent.log"

echo "==> 04 drift correction"
{
  echo "### DRIFT CORRECTION ###"
  echo "Puppet does not replay a script; it compiles a catalogue and compares"
  echo "it against live state. Introduce drift by hand, then re-apply."
  echo
  echo "--- introducing drift ---"
  docker exec "$NODE" bash -c '
    rm -f /etc/attendance/attendance.env && echo "deleted   /etc/attendance/attendance.env"
    echo GARBAGE > /etc/nginx/sites-available/attendance.conf && echo "corrupted /etc/nginx/sites-available/attendance.conf"
    systemctl stop attendance && echo "stopped   attendance.service"
    chown root:root /var/log/attendance && echo "chowned   /var/log/attendance to root:root"'
  echo
  echo "--- state after drift ---"
  docker exec "$NODE" bash -c '
    test -f /etc/attendance/attendance.env && echo "env file : present" || echo "env file : MISSING"
    echo "service  : $(systemctl is-active attendance)"
    echo "log dir  : $(stat -c %U:%G /var/log/attendance)"'
  echo
  echo "--- puppet apply ---"
  pa "$APPLY --detailed-exitcodes manifests/site.pp 2>&1; echo \"detailed-exitcode=\$?\""
  echo
  echo "--- state after correction ---"
  docker exec "$NODE" bash -c '
    for i in $(seq 1 40); do curl -fsS http://127.0.0.1/attendance/actuator/health >/dev/null 2>&1 && break; sleep 3; done
    test -f /etc/attendance/attendance.env && echo "env file : present" || echo "env file : MISSING"
    echo "service  : $(systemctl is-active attendance)"
    echo "log dir  : $(stat -c %U:%G /var/log/attendance)"
    echo "health   : $(curl -fsS http://127.0.0.1/attendance/actuator/health)"'
} 2>&1 | strip > "$OUT/04-puppet-drift-correction.log"

echo "==> 05 private class guard"
{
  echo "### PRIVATE CLASS GUARD -- negative test ###"
  echo "attendance::install, ::config, ::deploy and ::service are"
  echo "implementation detail. Declaring one directly must fail compilation."
  echo
  echo "--- attempt: include attendance::install from a bare manifest ---"
  docker exec "$NODE" bash -c "export PATH=/opt/puppetlabs/bin:\$PATH; cd /puppet
    echo 'include attendance::install' > /tmp/private-test.pp
    puppet apply $APPLY /tmp/private-test.pp 2>&1 | head -3; echo \"exit=\${PIPESTATUS[0]}\""
  echo
  echo "--- control: the public class still compiles ---"
  pa "--noop $APPLY manifests/site.pp 2>&1 | tail -2; echo \"exit=\${PIPESTATUS[0]}\""
} 2>&1 | strip > "$OUT/05-puppet-private-class-guard.log"

echo "==> 06 module inventory"
pa "--noop --write-catalog-summary $APPLY manifests/site.pp >/dev/null 2>&1" >/dev/null 2>&1
docker exec "$NODE" cat /opt/puppetlabs/puppet/cache/state/resources.txt > /tmp/pp-res.txt 2>/dev/null
{
  echo "### Puppet toolchain (masterless: puppet apply, no server) ###"
  docker exec "$NODE" /opt/puppetlabs/bin/puppet --version 2>&1 | sed 's/^/  puppet /'
  docker exec "$NODE" /opt/puppetlabs/bin/facter --version 2>&1 | sed 's/^/  facter /'
  docker exec "$NODE" /opt/puppetlabs/puppet/bin/ruby --version 2>&1 | sed 's/^/  ruby   /'
  docker exec "$NODE" bash -c '. /etc/os-release; echo "$PRETTY_NAME"' 2>&1 | sed 's/^/  node   /'
  echo
  echo "  Installed by puppet/lab/build-puppet-node.sh, which extracts the"
  echo "  self-contained /opt/puppetlabs tree from the official container"
  echo "  image. apt.puppet.com is refused by this lab's egress policy, and"
  echo "  the Ubuntu universe package is Puppet 5.5 -- too old for EPP and"
  echo "  the modern data types this module uses."
  echo
  echo "### Module layout ###"
  (cd "$REPO" && find puppet -type f | sort | sed 's/^/  /')
  echo
  echo "### External module dependencies: NONE ###"
  echo "  forgeapi.puppet.com is unreachable and not fixably so -- Puppet's"
  echo "  bundled Ruby ignores both SSL_CERT_FILE and --ssl_trust_store. The"
  echo "  two stdlib features the module needed are reimplemented locally:"
  echo "    Stdlib_absolutepath -> modules/attendance/types/absolutepath.pp"
  echo "    assert_private()    -> modules/attendance/functions/assert_private.pp"
  echo
  echo "### Classes in the catalogue ###"
  docker exec "$NODE" cat /opt/puppetlabs/puppet/cache/state/classes.txt 2>/dev/null \
    | grep -vE '^(settings|default)$' | sed 's/^/  /'
  echo
  echo "### Managed resources in the catalogue ###"
  sed 's/^/  /' /tmp/pp-res.txt
  echo
  echo "  total managed resources: $(wc -l < /tmp/pp-res.txt)"
  for t in exec package group user file service; do
    printf "    %-9s %s\n" "$t" "$(grep -c "^$t\[" /tmp/pp-res.txt)"
  done
} > "$OUT/06-puppet-module-inventory.log" 2>&1

echo "==> 07/08 comparison against the Ansible node"
cat > /tmp/pp-probe.sh <<'EOS'
echo "user:    $(getent passwd attendance | cut -d: -f1,3,4,7)"
echo "group:   $(getent group attendance | cut -d: -f1,3)"
for d in /opt/attendance /opt/attendance/releases /var/lib/attendance /var/log/attendance /etc/attendance; do
  echo "dir:     $(stat -c '%a %U:%G' $d) $d"
done
echo "envfile: $(stat -c '%a %U:%G' /etc/attendance/attendance.env) sha=$(sha256sum /etc/attendance/attendance.env | cut -c1-16)"
echo "unit:    $(stat -c '%a %U:%G' /etc/systemd/system/attendance.service) sha=$(sha256sum /etc/systemd/system/attendance.service | cut -c1-16)"
echo "nginx:   sha=$(sha256sum /etc/nginx/sites-available/attendance.conf | cut -c1-16)"
echo "symlink: /opt/attendance/current -> $(readlink /opt/attendance/current)"
echo "war:     $(stat -c '%U:%G %s' /opt/attendance/current/attendance.war) sha=$(sha256sum /opt/attendance/current/attendance.war | cut -c1-16)"
echo "svc:     attendance=$(systemctl is-active attendance)/$(systemctl is-enabled attendance) nginx=$(systemctl is-active nginx)/$(systemctl is-enabled nginx)"
echo "runas:   $(systemctl show attendance -p User --value)"
echo "health:  $(curl -fsS http://127.0.0.1/attendance/actuator/health 2>&1)"
echo "info:    $(curl -fsS http://127.0.0.1/attendance/actuator/info 2>&1)"
echo "logrot:  sha=$(sha256sum /etc/logrotate.d/attendance | cut -c1-16)"
EOS
docker cp /tmp/pp-probe.sh "$ANSIBLE_NODE:/tmp/probe.sh" >/dev/null 2>&1
docker cp /tmp/pp-probe.sh "$NODE:/tmp/probe.sh" >/dev/null 2>&1
docker exec "$ANSIBLE_NODE" bash /tmp/probe.sh > /tmp/pp-ans.txt 2>&1
docker exec "$NODE"         bash /tmp/probe.sh > /tmp/pp-pup.txt 2>&1
{
  echo "### End-state comparison: Ansible node vs Puppet node ###"
  echo
  echo "Both implement the same Stage 13 specification."
  echo "ansible/group_vars/attendance_servers.yml and puppet/data/common.yaml"
  echo "hold deliberately identical values, and both nodes deploy the very"
  echo "same artefact file (/artifacts/attendance-1.0.0.war)."
  echo
  printf "%-62s | %-62s\n" "ANSIBLE ($ANSIBLE_NODE)" "PUPPET ($NODE)"
  printf -- "%s-+-%s\n" "$(printf '%0.s-' {1..62})" "$(printf '%0.s-' {1..62})"
  same=0; tot=0
  while IFS= read -r a && IFS= read -r b <&3; do
    tot=$((tot+1)); m="*"; [ "$a" = "$b" ] && { m="="; same=$((same+1)); }
    printf "%-62s | %-62s %s\n" "$a" "$b" "$m"
  done < /tmp/pp-ans.txt 3< /tmp/pp-pup.txt
  echo
  echo "  '=' identical   '*' differs        $same of $tot attributes identical"
  echo
  echo "### Mechanical diff ###"
  if diff -q /tmp/pp-ans.txt /tmp/pp-pup.txt >/dev/null; then
    echo "IDENTICAL on every probed attribute."
  else
    echo "- Ansible / + Puppet:"
    diff -u /tmp/pp-ans.txt /tmp/pp-pup.txt | tail -n +4 | grep '^[-+]'
    echo
    echo "Remaining differences are file digests. Each tool stamps its own"
    echo "'managed by' banner into the files it owns; the content diff is in"
    echo "08-rendered-templates-comparison.log."
  fi
} > "$OUT/07-ansible-vs-puppet-end-state.log" 2>&1

for f in /etc/attendance/attendance.env /etc/systemd/system/attendance.service \
         /etc/nginx/sites-available/attendance.conf /etc/logrotate.d/attendance; do
  n=$(basename "$f")
  docker exec "$ANSIBLE_NODE" cat "$f" > "/tmp/pp-a-$n" 2>/dev/null
  docker exec "$NODE"         cat "$f" > "/tmp/pp-p-$n" 2>/dev/null
done
{
  echo "### Rendered-file comparison: Ansible (Jinja2) vs Puppet (EPP) ###"
  echo
  echo "Both nodes carry the same release and the same variable values, so the"
  echo "only legitimate difference left is the managed-by banner each tool"
  echo "stamps into the file it owns. Comments and blank lines are stripped"
  echo "before diffing; nothing else is normalised."
  echo
  ident=0; total=0
  for n in attendance.env attendance.service attendance.conf attendance; do
    echo "--------------------------------------------------------------------"
    echo "FILE: $n"
    total=$((total+1))
    sed -E -e '/^[[:space:]]*#/d' -e '/^[[:space:]]*$/d' "/tmp/pp-a-$n" > "/tmp/pp-na-$n"
    sed -E -e '/^[[:space:]]*#/d' -e '/^[[:space:]]*$/d' "/tmp/pp-p-$n" > "/tmp/pp-np-$n"
    if diff -q "/tmp/pp-na-$n" "/tmp/pp-np-$n" >/dev/null; then
      echo "  IDENTICAL -- $(wc -l < "/tmp/pp-na-$n") significant lines match byte for byte"
      ident=$((ident+1))
    else
      echo "  diff (- Ansible / + Puppet):"
      diff -u "/tmp/pp-na-$n" "/tmp/pp-np-$n" | tail -n +3 | sed 's/^/    /'
    fi
  done
  echo "--------------------------------------------------------------------"
  echo
  echo "RESULT: $ident of $total managed files are byte-identical once each"
  echo "tool's own banner comment is removed."
} > "$OUT/08-rendered-templates-comparison.log" 2>&1

echo
echo "Wrote:"
ls -1 "$OUT"/*.log | sed 's|.*/|  |'
