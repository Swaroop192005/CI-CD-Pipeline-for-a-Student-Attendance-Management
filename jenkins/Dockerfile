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

# The docker CLI and Maven are installed into the image rather than bind
# mounted from the host. The original compose mounted /usr/bin/docker and
# /opt/maven, which ties the controller to a host that has both at those
# paths and as Linux builds -- a macOS host has neither, and its docker
# binary is a Mach-O executable that cannot run inside a Linux container.
#
# Only the docker *client* is installed; it talks to the host's daemon over
# the mounted socket, which is what the pipeline's image builds and
# container deployments need.
ARG MAVEN_VERSION=3.9.9
# APT_PROXY is empty on an ordinary machine, where apt reaches the Debian
# mirrors directly. It exists for networks that refuse plain HTTP and serve
# only HTTPS through a proxy -- the same arrangement ansible/targetnode
# needs, and the reason the apt steps below are written to be rewritten.
ARG APT_PROXY=""
RUN set -eu; \
    if [ -n "$APT_PROXY" ]; then \
      sed -i 's|http://deb.debian.org|https://deb.debian.org|g; s|http://security.debian.org|https://security.debian.org|g' \
        /etc/apt/sources.list.d/debian.sources 2>/dev/null || true; \
      sed -i 's|http://deb.debian.org|https://deb.debian.org|g; s|http://security.debian.org|https://security.debian.org|g' \
        /etc/apt/sources.list 2>/dev/null || true; \
      printf 'Acquire::https::Proxy "%s";\nAcquire::http::Proxy "%s";\n' "$APT_PROXY" "$APT_PROXY" \
        > /etc/apt/apt.conf.d/01proxy; \
    fi; \
    apt-get update; \
    apt-get install -y --no-install-recommends ca-certificates curl gnupg; \
    install -m 0755 -d /etc/apt/keyrings; \
    if [ -n "$APT_PROXY" ]; then export https_proxy="$APT_PROXY" http_proxy="$APT_PROXY"; fi; \
    curl -fsSL https://download.docker.com/linux/debian/gpg \
      | gpg --dearmor -o /etc/apt/keyrings/docker.gpg; \
    chmod a+r /etc/apt/keyrings/docker.gpg; \
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/debian $(. /etc/os-release && echo "$VERSION_CODENAME") stable" > /etc/apt/sources.list.d/docker.list; \
    apt-get update; \
    apt-get install -y --no-install-recommends docker-ce-cli; \
    rm -rf /var/lib/apt/lists/*; \
    curl -fsSL -o /tmp/maven.tar.gz "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz"; \
    mkdir -p /opt/maven; \
    tar -xzf /tmp/maven.tar.gz -C /opt/maven --strip-components=1; \
    rm -f /tmp/maven.tar.gz; \
    /opt/maven/bin/mvn -v
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
