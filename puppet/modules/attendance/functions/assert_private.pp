# @summary Fails catalogue compilation when a private class is declared from
#   outside the `attendance` module.
#
# A module-local replacement for stdlib's `assert_private()`. The Puppet Forge
# is unreachable from this lab network -- Puppet's bundled Ruby ignores both
# `SSL_CERT_FILE` and `--ssl_trust_store`, so `puppet module install
# puppetlabs-stdlib` cannot verify the Forge certificate -- so this module
# carries no external dependencies at all. See
# docs/stage-13-configuration-management.md for the full account.
#
# Unlike the stdlib version, the caller is passed in explicitly. stdlib
# implements this with the Ruby 3.x function API, where `self` is the calling
# scope; the modern API exposes only the *definition* scope, so a pure-Puppet
# implementation has to be told who is calling. Both values are automatically
# in scope inside any class body.
#
# @param class_name
#   The class guarding itself, i.e. `$name` in the class body.
# @param caller_module
#   The module that declared this class, i.e. `$caller_module_name`. This is
#   `undef` when the class was declared from a bare manifest such as site.pp.
#
# @example Guarding a private class
#   class attendance::install {
#     attendance::assert_private($name, $caller_module_name)
#   }
function attendance::assert_private(
  String[1]        $class_name,
  Optional[String] $caller_module = undef,
) >> Undef {
  if $caller_module != 'attendance' {
    fail("Class ${class_name} is private to the attendance module and may not \
be declared directly; declare the `attendance` class instead.")
  }
}
