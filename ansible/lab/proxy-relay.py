#!/usr/bin/env python3
"""Forward a bridge-reachable port to the lab's loopback-only HTTPS proxy.

The session's egress proxy listens on 127.0.0.1 only, so a container on
Docker's bridge network cannot reach it: inside the container 127.0.0.1 is
the container itself. The managed node needs it for apt, so this relay
binds 0.0.0.0 on the host and forwards to the proxy.

This is lab plumbing, not part of the system being built. On a network
where the node can reach its package mirrors directly, nothing here is
needed and `proxy_url` in the inventory is simply left empty.

usage: proxy-relay.py [listen_port] [target_host:target_port]
"""
import socket
import sys
import threading

LISTEN_PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 40388
TARGET = sys.argv[2] if len(sys.argv) > 2 else "127.0.0.1:40387"
TARGET_HOST, TARGET_PORT = TARGET.rsplit(":", 1)
TARGET_PORT = int(TARGET_PORT)


def pump(src, dst):
    try:
        while True:
            data = src.recv(65536)
            if not data:
                break
            dst.sendall(data)
    except OSError:
        pass
    finally:
        for sock in (src, dst):
            try:
                sock.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass


def handle(client):
    try:
        upstream = socket.create_connection((TARGET_HOST, TARGET_PORT), timeout=30)
    except OSError:
        client.close()
        return
    threading.Thread(target=pump, args=(client, upstream), daemon=True).start()
    threading.Thread(target=pump, args=(upstream, client), daemon=True).start()


def main():
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("0.0.0.0", LISTEN_PORT))
    server.listen(128)
    print(f"relaying 0.0.0.0:{LISTEN_PORT} -> {TARGET_HOST}:{TARGET_PORT}", flush=True)
    while True:
        client, _ = server.accept()
        threading.Thread(target=handle, args=(client,), daemon=True).start()


if __name__ == "__main__":
    main()
