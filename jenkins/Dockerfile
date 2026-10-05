# Jenkins controller for the Student Attendance Management Portal.
#
# Plugins are baked in at image build time rather than downloaded at first
# start, so the controller comes up in a known state every time the image
# is rebuilt - no boot-time download, and no window in which the
# controller is running with a partial plugin set.
#
# Nothing is installed with apt. The lab's egress policy refuses
# deb.debian.org, and apt speaks plain HTTP which the proxy does not
# serve. Maven and the Docker CLI are bind-mounted from the host instead
# (see jenkins/docker-compose.yml); the base image already provides
# JDK 21, git and curl, which is everything else the build needs.
FROM jenkins/jenkins:lts-jdk21

USER root

# Trust any CA dropped into jenkins/certs. Needed where outbound HTTPS is
# re-terminated by a proxy, as it is in this lab: without it the plugin
# manager cannot verify updates.jenkins.io. The directory is empty in
# version control, and the step is a no-op when it stays empty, so the
# image still builds normally on an ordinary network.
COPY certs/ /tmp/extra-certs/
RUN set -eu; \
    found=0; \
    for bundle in /tmp/extra-certs/*.crt; do \
      [ -e "$bundle" ] || continue; \
      name="$(basename "$bundle" .crt)"; \
      mkdir -p /tmp/split && rm -f /tmp/split/*; \
      # A PEM file may hold a whole chain, and keytool -importcert reads
      # only the first certificate in one. Split the file and import each
      # certificate separately, or an intermediate is silently dropped and
      # verification still fails - which is exactly how this was found.
      awk 'BEGIN { n = 0 } \
           /BEGIN CERTIFICATE/ { n++ } \
           { print > ("/tmp/split/cert-" n ".pem") }' "$bundle"; \
      for cert in /tmp/split/cert-*.pem; do \
        [ -e "$cert" ] || continue; \
        grep -q "BEGIN CERTIFICATE" "$cert" || continue; \
        alias="extra-${name}-$(basename "$cert" .pem)"; \
        echo "Importing $alias into the JVM truststore"; \
        keytool -importcert -noprompt -alias "$alias" -file "$cert" \
                -keystore "$JAVA_HOME/lib/security/cacerts" \
                -storepass changeit; \
        found=1; \
      done; \
    done; \
    [ "$found" -eq 1 ] || echo "No extra CA certificates supplied; skipping truststore import"; \
    rm -rf /tmp/extra-certs /tmp/split

USER jenkins

COPY plugins.txt /usr/share/jenkins/ref/plugins.txt

# JVM_PROXY_OPTS carries -Dhttps.proxyHost/-Dhttps.proxyPort when the
# build runs behind a proxy, and is empty otherwise.
#
# The JVM does not read the HTTPS_PROXY environment variable, unlike curl
# and apt. Setting HTTPS_PROXY alone leaves the plugin manager connecting
# directly, which fails with a PKIX error that looks like a truststore
# problem and is not one - the certificate it cannot verify is simply not
# the proxy's. Both the truststore import above and these properties are
# needed.
ARG JVM_PROXY_OPTS=""
RUN JAVA_OPTS="${JVM_PROXY_OPTS}" \
    jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt --latest true

# This controller is configured from casc.yaml, never by clicking through
# the setup wizard.
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false"
ENV CASC_JENKINS_CONFIG=/var/jenkins_conf/casc.yaml
