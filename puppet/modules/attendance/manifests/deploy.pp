# @summary Places a release and points `current` at it.
#
# Releases are versioned directories kept side by side; the symlink is the
# deployment. That is what turns rollback into repointing a symlink and
# restarting, rather than re-fetching an artefact that may no longer be
# available from a registry that may itself be the thing that is broken.
#
# @api private
class attendance::deploy {
  attendance::assert_private($name, $caller_module_name)

  $app_user    = $attendance::app_user
  $app_group   = $attendance::app_group
  $release_dir = $attendance::release_dir
  $version     = $attendance::app_version

  file { $release_dir:
    ensure => directory,
    owner  => $app_user,
    group  => $app_group,
    mode   => '0755',
  }

  # `file` with a source is content-addressed: Puppet compares checksums and
  # copies only when they differ, so a 60 MB artefact is not re-copied on
  # every run. An `exec` with `cp` would report a change forever and destroy
  # the idempotency this is measured on.
  file { "${release_dir}/attendance.war":
    ensure  => file,
    owner   => $app_user,
    group   => $app_group,
    mode    => '0644',
    source  => "file://${attendance::artifact_dir}/attendance-${version}.war",
    require => File[$release_dir],
    notify  => Service['attendance'],
  }

  # The symlink *is* the deployment: everything above prepares a release,
  # and this is the moment it becomes live.
  file { $attendance::current_link:
    ensure  => link,
    target  => $release_dir,
    owner   => $app_user,
    group   => $app_group,
    force   => true,
    require => File["${release_dir}/attendance.war"],
    notify  => Service['attendance'],
  }

  # Keep enough history to roll back more than once without letting 60 MB
  # artefacts accumulate indefinitely. Guarded by `onlyif` so it is a no-op
  # - and reports no change - until there is actually something to prune.
  $keep = $attendance::releases_to_keep
  # `tail -n +N` is 1-based, so the first release to discard is $keep + 1.
  $first_stale = $keep + 1
  exec { 'attendance prune old releases':
    command => "/bin/bash -c 'cd ${attendance::releases_dir} && ls -1dt */ | tail -n +${first_stale} | xargs -r rm -rf'",
    onlyif  => "/bin/bash -c 'test \$(ls -1d ${attendance::releases_dir}/*/ 2>/dev/null | wc -l) -gt ${keep}'",
    path    => ['/usr/bin', '/bin'],
    require => File[$attendance::current_link],
  }
}
