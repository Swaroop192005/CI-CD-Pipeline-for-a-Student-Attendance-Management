# A bare target node for the Ansible playbook to provision.
#
# This is deliberately *minimal*: Ubuntu, an SSH daemon, Python (which
# Ansible needs on the managed node) and systemd. Nothing the application
# needs is installed here. The JRE, the service user, the directories, the
# configuration, the reverse proxy and the service unit are all the
# playbook's job - which is the point of Stage 14, where a clean node is
# provisioned from scratch.
#
# systemd runs as PID 1 so the playbook can manage real service units with
# the systemd module rather than a shell script pretending to be one.
FROM ubuntu:22.04

ARG DEBIAN_FRONTEND=noninteractive

# apt over HTTPS through the lab proxy: the default http mirrors are
# refused by the egress policy, and apt's plain-HTTP requests are not
# something the proxy serves.
ARG APT_PROXY=""
RUN if [ -n "$APT_PROXY" ]; then \
      sed -i 's|http://archive.ubuntu.com|https://archive.ubuntu.com|g; s|http://security.ubuntu.com|https://security.ubuntu.com|g' /etc/apt/sources.list; \
      echo "Acquire::https::Proxy \"$APT_PROXY\";" > /etc/apt/apt.conf.d/01proxy; \
    fi

# Trusting the proxy CA is a chicken-and-egg problem: apt now speaks HTTPS,
# so it needs a trust store before it can fetch the ca-certificates package
# that would create one. The base image ships none at all.
#
# It is bootstrapped with a complete bundle (bootstrap-bundle.crt). A file
# holding only the proxy CA is not enough: apt's GnuTLS backend rejects it,
# because it validates against the system store as a whole rather than
# against a supplied certificate. Once the ca-certificates package is
# installed, update-ca-certificates regenerates the store properly from
# /usr/local/share/ca-certificates, where the proxy CA also sits.
COPY certs/ /tmp/node-certs/
RUN set -eu; \
    mkdir -p /etc/ssl/certs /usr/local/share/ca-certificates; \
    if [ -f /tmp/node-certs/bootstrap-bundle.crt ]; then \
      cp /tmp/node-certs/bootstrap-bundle.crt /etc/ssl/certs/ca-certificates.crt; \
      echo "Bootstrapped the trust store with $(grep -c 'BEGIN CERTIFICATE' /etc/ssl/certs/ca-certificates.crt) certificates"; \
    fi; \
    for cert in /tmp/node-certs/*.crt; do \
      [ -e "$cert" ] || continue; \
      case "$(basename "$cert")" in bootstrap-bundle.crt) continue ;; esac; \
      cp "$cert" /usr/local/share/ca-certificates/; \
    done; \
    rm -rf /tmp/node-certs

RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates \
 && update-ca-certificates \
 && apt-get install -y --no-install-recommends \
      systemd systemd-sysv \
      openssh-server \
      python3 python3-apt \
      sudo curl \
 && rm -rf /var/lib/apt/lists/*

# SSH for Ansible. Key-based only: a password-authenticated node is not
# something to demonstrate as good practice even in a lab.
RUN mkdir -p /var/run/sshd /root/.ssh \
 && chmod 700 /root/.ssh \
 && sed -i 's/^#*PermitRootLogin.*/PermitRootLogin prohibit-password/' /etc/ssh/sshd_config \
 && sed -i 's/^#*PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config \
 && systemctl enable ssh

COPY authorized_keys /root/.ssh/authorized_keys
RUN chmod 600 /root/.ssh/authorized_keys

# Containers inherit no proxy environment; give systemd services the same
# egress configuration the host uses.
ARG HTTPS_PROXY_ENV=""
RUN if [ -n "$HTTPS_PROXY_ENV" ]; then \
      mkdir -p /etc/systemd/system.conf.d; \
      printf '[Manager]\nDefaultEnvironment="HTTPS_PROXY=%s" "https_proxy=%s" "NO_PROXY=localhost,127.0.0.1"\n' \
        "$HTTPS_PROXY_ENV" "$HTTPS_PROXY_ENV" > /etc/systemd/system.conf.d/proxy.conf; \
      printf 'Acquire::https::Proxy "%s";\n' "$HTTPS_PROXY_ENV" > /etc/apt/apt.conf.d/01proxy; \
    fi

EXPOSE 22 80 8080
STOPSIGNAL SIGRTMIN+3
CMD ["/sbin/init"]
