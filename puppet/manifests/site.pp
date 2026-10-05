# Entry point for a masterless Puppet run.
#
#   puppet apply --modulepath=modules --hiera_config=hiera.yaml manifests/site.pp
#
# Masterless rather than agent/master because a single node does not
# justify a Puppet server: `puppet apply` compiles and enforces the
# catalogue locally, which is the same catalogue a master would have sent.

node default {
  include attendance
}
