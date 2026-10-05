# @summary Provisions an application server for the Student Attendance
#   Management Portal.
#
# Turns a bare node into a running, health-checked service: packages, a
# service account, the directory layout, configuration, a systemd unit,
# nginx as the edge, and a versioned release with a `current` symlink.
#
# The class is split into four stages that run in a fixed order, because
# the order is part of the correctness: nothing can be configured before
# the directories exist, nothing can be deployed before it is configured,
# and the service must not be started before there is anything to start.
#
# @param app_user                 Service account name.
# @param app_group                Service account group.
# @param app_uid                  Service account uid, fixed so that file
#                                 ownership is stable across rebuilds.
# @param app_gid                  Service account gid.
# @param app_root                 Application root.
# @param app_data_dir             H2 datastore location.
# @param app_log_dir              Application log directory.
# @param app_config_dir           Configuration directory, root-owned.
# @param app_port                 Port the application listens on.
# @param app_context_path         Servlet context path.
# @param app_environment          Environment label the application reports.
# @param app_eligibility_threshold Examination-eligibility percentage.
# @param app_seed_data            Seed fixtures on an empty datastore.
# @param java_package             JRE package to install.
# @param app_java_opts            JVM options for the service.
# @param app_version              The release to make live.
# @param artifact_dir             Where the build artefact is staged.
# @param releases_to_keep         How many releases to retain for rollback.
# @param nginx_port               Port nginx listens on.
# @param nginx_server_name        nginx server_name.
#
# @example Provision with the defaults from Hiera
#   include attendance
#
# @example Deploy a specific release
#   class { 'attendance': app_version => '1.0.2' }
class attendance (
  String[1]                $app_user,
  String[1]                $app_group,
  Integer                  $app_uid,
  Integer                  $app_gid,
  Attendance::AbsolutePath $app_root,
  Attendance::AbsolutePath $app_data_dir,
  Attendance::AbsolutePath $app_log_dir,
  Attendance::AbsolutePath $app_config_dir,
  Integer[1, 65535]        $app_port,
  String[1]                $app_context_path,
  String[1]                $app_environment,
  Integer[1, 100]          $app_eligibility_threshold,
  Boolean                  $app_seed_data,
  String[1]                $java_package,
  String                   $app_java_opts,
  String[1]                $app_version,
  Attendance::AbsolutePath $artifact_dir,
  Integer[1]               $releases_to_keep,
  Integer[1, 65535]        $nginx_port,
  String[1]                $nginx_server_name,
) {

  # Derived paths, in one place so no manifest recomputes them.
  $releases_dir = "${app_root}/releases"
  $current_link = "${app_root}/current"
  $release_dir  = "${releases_dir}/${app_version}"
  $health_path  = "${app_context_path}/actuator/health"

  # `contain` rather than `include`, so the ordering below applies to the
  # resources *inside* each class and not merely to the class declarations.
  contain attendance::install
  contain attendance::config
  contain attendance::deploy
  contain attendance::service

  # The order is the contract, declared explicitly rather than left to
  # declaration order: Puppet builds a dependency graph, so a catalogue that
  # happened to work today would be free to reorder itself tomorrow.
  #
  # The last arrow is `~>` rather than `->`: a change anywhere in the deploy
  # phase refreshes the service, while an unchanged deploy leaves it running.
  Class['attendance::install']
  -> Class['attendance::config']
  -> Class['attendance::deploy']
  ~> Class['attendance::service']
}
