# @summary An absolute filesystem path.
#
# Defined here rather than using stdlib's `Stdlib_absolutepath`, so the
# module has no external dependency. That is worth having on its own
# merits for a module this size, and it is also necessary here: the Puppet
# Forge cannot be reached from this lab, because outbound HTTPS is
# re-terminated by a proxy whose CA is not in the CA store bundled with
# Puppet's own Ruby, and the module tool does not honour SSL_CERT_FILE or
# --ssl_trust_store.
type Attendance::AbsolutePath = Pattern[/\A\/[^\n]*\z/]
