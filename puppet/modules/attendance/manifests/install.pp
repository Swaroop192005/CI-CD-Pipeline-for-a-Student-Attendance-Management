# @summary Packages, service account and the directory layout.
#
# @api private
class attendance::install {
  attendance::assert_private($name, $caller_module_name)

  $app_user     = $attendance::app_user
  $app_group    = $attendance::app_group
  $app_root     = $attendance::app_root
  $releases_dir = $attendance::releases_dir

  # Refresh the package index before the first install. A freshly built node
  # ships with /var/lib/apt/lists emptied, and the resulting failure reads as
  # "Unable to locate package", which points at the package rather than at
  # the missing index.
  #
  # The guard deliberately inspects /var/lib/apt/lists rather than
  # /var/cache/apt/pkgcache.bin: pkgcache.bin is apt's *binary* cache, which
  # any apt invocation regenerates -- a failed install included -- so its
  # mtime says nothing about whether the package lists were ever fetched. A
  # guard on it skips the refresh on exactly the node that needs it most.
  #
  # Two details the obvious guard gets wrong:
  #
  #   - The glob is *_Packages* rather than *_Packages, because this node
  #     sets Acquire::GzipIndexes and the indices land compressed
  #     (*_Packages.lz4).
  #   - Staleness is measured from the mtime of the lists *directory*, not
  #     of the index files. apt preserves the server's Last-Modified on the
  #     files it downloads, so a freshly fetched index can carry a timestamp
  #     weeks old; the directory's mtime is when apt actually wrote into it.
  #     Timing off the files would re-run apt on every single run and cost
  #     the idempotency claim.
  #
  # So: refresh when there is no index at all, or when the last successful
  # fetch was over an hour ago.
  exec { 'attendance apt-get update':
    command     => '/usr/bin/apt-get update',
    refreshonly => false,
    onlyif      => '/bin/sh -c \'! ls /var/lib/apt/lists/*_Packages* >/dev/null 2>&1 || test $(( $(date +%s) - $(stat -c %Y /var/lib/apt/lists) )) -gt 3600\'',
    path        => ['/usr/bin', '/bin', '/usr/sbin', '/sbin'],
    logoutput   => 'on_failure',
  }

  # A JRE, not a JDK: the artefact arrives pre-built from the pipeline, so
  # a compiler on an application server is weight and attack surface for
  # nothing.
  package { $attendance::java_package:
    ensure  => installed,
    require => Exec['attendance apt-get update'],
  }

  package { ['curl', 'unzip', 'ca-certificates']:
    ensure  => installed,
    require => Exec['attendance apt-get update'],
  }

  group { $app_group:
    ensure => present,
    gid    => $attendance::app_gid,
    system => true,
  }

  # A dedicated, non-login system account. The application has no business
  # running as root, and nobody has any business signing in as it.
  user { $app_user:
    ensure     => present,
    uid        => $attendance::app_uid,
    gid        => $app_group,
    system     => true,
    shell      => '/usr/sbin/nologin',
    home       => $app_root,
    managehome => false,
    require    => Group[$app_group],
  }

  file { [$app_root, $releases_dir]:
    ensure  => directory,
    owner   => $app_user,
    group   => $app_group,
    mode    => '0755',
    require => User[$app_user],
  }

  file { [$attendance::app_data_dir, $attendance::app_log_dir]:
    ensure  => directory,
    owner   => $app_user,
    group   => $app_group,
    mode    => '0750',
    require => User[$app_user],
  }

  # Configuration is root-owned and group-readable: the service reads it,
  # and must not be able to rewrite it.
  file { $attendance::app_config_dir:
    ensure  => directory,
    owner   => 'root',
    group   => $app_group,
    mode    => '0750',
    require => Group[$app_group],
  }
}
