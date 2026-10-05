# @summary Configuration files: service environment, systemd unit, nginx
#   and log rotation.
#
# Every file here notifies something. That is what makes the run
# idempotent and still correct: nothing is restarted unless its
# configuration actually changed, and anything whose configuration changed
# is restarted.
#
# @api private
class attendance::config {
  attendance::assert_private($name, $caller_module_name)

  $app_user     = $attendance::app_user
  $app_group    = $attendance::app_group
  $current_link = $attendance::current_link

  # Every setting the application reads, in one place. The WAR is identical
  # across environments; this file is what makes an environment what it is
  # (SRS NFR-08).
  file { "${attendance::app_config_dir}/attendance.env":
    ensure  => file,
    owner   => 'root',
    group   => $app_group,
    mode    => '0640',
    content => epp('attendance/attendance.env.epp', {
      'app_port'              => $attendance::app_port,
      'app_context_path'      => $attendance::app_context_path,
      'app_data_dir'          => $attendance::app_data_dir,
      'app_environment'       => $attendance::app_environment,
      'eligibility_threshold' => $attendance::app_eligibility_threshold,
      'seed_data'             => $attendance::app_seed_data,
      'java_opts'             => $attendance::app_java_opts,
      'app_version'           => $attendance::app_version,
    }),
    notify  => Service['attendance'],
  }

  file { '/etc/systemd/system/attendance.service':
    ensure  => file,
    owner   => 'root',
    group   => 'root',
    mode    => '0644',
    content => epp('attendance/attendance.service.epp', {
      'app_user'        => $app_user,
      'app_group'       => $app_group,
      'app_config_dir'  => $attendance::app_config_dir,
      'app_data_dir'    => $attendance::app_data_dir,
      'app_log_dir'     => $attendance::app_log_dir,
      'current_link'    => $current_link,
    }),
    notify  => [Exec['attendance systemd daemon-reload'], Service['attendance']],
  }

  exec { 'attendance systemd daemon-reload':
    command     => '/usr/bin/systemctl daemon-reload',
    refreshonly => true,
  }

  # Without rotation the application log grows until the disk fills, which
  # is a slow outage that presents as a sudden one.
  file { '/etc/logrotate.d/attendance':
    ensure  => file,
    owner   => 'root',
    group   => 'root',
    mode    => '0644',
    content => epp('attendance/logrotate.epp', {
      'app_log_dir' => $attendance::app_log_dir,
      'app_user'    => $app_user,
      'app_group'   => $app_group,
    }),
  }

  # ---- Edge ---------------------------------------------------------------
  #
  # nginx in front of the application even in a lab: it is where TLS
  # termination, rate limiting and a maintenance page belong, and
  # retro-fitting a proxy to a system that never had one is more disruptive
  # than having it from the start.
  package { 'nginx':
    ensure => installed,
  }

  # The distribution's default site answers on port 80 ahead of ours
  # depending on which server block matches first.
  file { '/etc/nginx/sites-enabled/default':
    ensure  => absent,
    require => Package['nginx'],
    notify  => Service['nginx'],
  }

  file { '/etc/nginx/sites-available/attendance.conf':
    ensure  => file,
    owner   => 'root',
    group   => 'root',
    mode    => '0644',
    content => epp('attendance/nginx-attendance.conf.epp', {
      'nginx_port'        => $attendance::nginx_port,
      'nginx_server_name' => $attendance::nginx_server_name,
      'app_port'          => $attendance::app_port,
      'app_context_path'  => $attendance::app_context_path,
      'health_path'       => $attendance::health_path,
    }),
    require => Package['nginx'],
    notify  => Exec['attendance nginx config test'],
  }

  file { '/etc/nginx/sites-enabled/attendance.conf':
    ensure  => link,
    target  => '/etc/nginx/sites-available/attendance.conf',
    require => File['/etc/nginx/sites-available/attendance.conf'],
    notify  => Exec['attendance nginx config test'],
  }

  # Validated before the service is told to reload. An invalid
  # configuration that is already enabled takes the edge down at the next
  # restart, which may be hours later and look unrelated.
  exec { 'attendance nginx config test':
    command     => '/usr/sbin/nginx -t',
    refreshonly => true,
    notify      => Service['nginx'],
  }

  service { 'nginx':
    ensure  => running,
    enable  => true,
    require => [Package['nginx'], File['/etc/nginx/sites-enabled/attendance.conf']],
  }
}
