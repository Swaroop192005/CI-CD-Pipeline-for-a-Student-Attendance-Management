# @summary Manages the attendance systemd service.
#
# @api private
class attendance::service {
  attendance::assert_private($name, $caller_module_name)

  service { 'attendance':
    ensure => running,
    enable => true,
  }
}
