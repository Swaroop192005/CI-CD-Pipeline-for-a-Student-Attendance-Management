# Puppet implementation — Stage 13

Stage 13 asks for *"either a Puppet manifest/modules or an Ansible inventory
and YAML playbook"*. This directory is the Puppet half; `../ansible/` is the
other. Both implement the same specification — `docs/stage-13-configuration-management.md` §2 —
and §7.6 of that document compares what the two produce.

## What is what

```
hiera.yaml                  Hiera 5 configuration
data/common.yaml            the 20 class parameters -- all the data lives here
manifests/site.pp           node default { include attendance }
modules/attendance/
  manifests/init.pp         the public class: parameters, derived paths, ordering
  manifests/install.pp      packages, group, user, directory layout      (private)
  manifests/config.pp       env file, systemd unit, logrotate, nginx     (private)
  manifests/deploy.pp       release directory, artefact, current symlink (private)
  manifests/service.pp      the systemd service                          (private)
  functions/assert_private.pp   local replacement for stdlib's assert_private()
  types/absolutepath.pp         local replacement for Stdlib_absolutepath
  templates/*.epp           four EPP templates
lab/                        NOT part of the deliverable -- see below
```

`modules/attendance/` is the deliverable. It has **no external dependencies**:
the Puppet Forge is unreachable from this network and not fixably so, because
Puppet's bundled Ruby ignores both `SSL_CERT_FILE` and `--ssl_trust_store`, so
the two `stdlib` features the module needed are reimplemented locally in about
twenty lines. The reasoning is in §7.2 of the stage document.

## `lab/` is scaffolding, not the deliverable

The three scripts in `lab/` exist so the evidence can be reproduced, and they
encode things that are true of *this* lab and nothing else: a target node that
is a container, and an egress proxy on the session's loopback address that the
node cannot reach without a relay. None of that belongs in the manifest, and
none of it is in the manifest.

| Script | Does |
|---|---|
| `build-puppet-node.sh` | Brings up a bare node and installs Puppet 7 on it |
| `capture-proofs.sh` | Runs the whole evidence sequence into `proofs/stage-13/puppet/` |
| `browse-node.sh` | Drives the portal with a real browser and saves screenshots |

On real infrastructure none of these is needed: you install Puppet from
`apt.puppet.com`, and the node reaches the network by itself.

`browse-node.sh` is worth keeping for a different reason. The reverse-proxy
defect in §7.7 — `proxy_set_header Host $host` dropping the port, which broke
every redirect and so the entire login flow — passed every `curl` health check
in Stages 13 and 14, because the health endpoint never redirects. The script
asserts it reaches `/dashboard`, so it fails loudly if that regression returns.

## Running it

Puppet is applied **masterless**: `puppet apply`, no agent and no master. The
agent/master split is a fleet-management concern that would add a certificate
authority, a daemon and a server to a one-node lab without changing a thing
that lands on the node, and `puppet apply` exercises the same catalogue
compiler and the same resource providers.

```bash
# bare node + Puppet 7 + the module tree copied to /puppet
bash puppet/lab/build-puppet-node.sh

# converge it
docker exec attendance-node-puppet bash -c 'cd /puppet && puppet apply \
    --modulepath=modules --hiera_config=hiera.yaml \
    --detailed-exitcodes manifests/site.pp'

# the whole evidence sequence, into proofs/stage-13/puppet/
bash puppet/lab/capture-proofs.sh
```

`--detailed-exitcodes` is what makes the idempotency claim checkable rather
than rhetorical: **0** means no changes and no failures, **2** means changes
were applied, **4** means failures. A second run of an idempotent manifest
must exit 0, and it does.

## Changing what gets deployed

Everything configurable is data. Nothing in `manifests/` needs editing to
change a value:

```yaml
# data/common.yaml
attendance::app_version: 1.0.0
attendance::app_environment: production
attendance::app_eligibility_threshold: 75
```

`proofs/stage-13/puppet/09-puppet-hiera-data-change.log` shows the threshold
moved 75 → 80: the apply rewrote one file, refreshed the service through the
notify chain, and touched nothing else — and reverting the value converged the
file back to its original digest byte for byte.
