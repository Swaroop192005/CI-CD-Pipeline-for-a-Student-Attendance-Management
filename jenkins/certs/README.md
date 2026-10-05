# Extra CA certificates for the Jenkins controller

Drop any `*.crt` files here that the controller must trust, and they are
imported into the image's JVM truststore at build time.

This exists because the lab's outbound HTTPS is re-terminated by a
policy-enforcing proxy, so the Jenkins plugin manager cannot verify
`updates.jenkins.io` without the proxy's CA. The directory is empty in
version control on purpose — a CA certificate is environment-specific and
does not belong in the repository. `jenkins/build-controller.sh` copies
the local one in before building and removes it afterwards.

On a network without TLS interception, leave this directory empty: the
build skips the import and behaves normally.
