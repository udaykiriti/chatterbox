#!/usr/bin/env python3
"""Functional test for the Chatterbox TLS gateway (stdlib only).

Spins up a fake TLS IRC server (self-signed cert via openssl), the real
gateway/server.py pointed at it, then drives a client through the whole
path: wrong-password reject, login, server-notice relay, PING/PONG both
ways, PRIVMSG echo, and the /healthz endpoint.

Run:  python3 gateway/test_gateway.py
Exit 0 when every check passes, 1 otherwise.
"""
import asyncio
import json
import os
import socket
import ssl
import subprocess
import sys
import tempfile
import time
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
SERVER_PY = os.path.join(HERE, "server.py")

FAKE_IRC_PORT = 16993
GW_PORT = 16667
GW_HEALTH_PORT = 18888
GW_PASSWORD = "functional-test-password-0123456789"


def run(cmd, **kw):
    return subprocess.run(cmd, capture_output=True, text=True, **kw)


class FakeIrc:
    """Minimal TLS IRC server: greeting NOTICE, PINGs, PRIVMSG echo."""

    def __init__(self, cert, key):
        self.cert = cert
        self.key = key
        self.received = []
        self.server = None

    async def start(self):
        ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        ctx.load_cert_chain(self.cert, self.key)
        self.server = await asyncio.start_server(
            self.handle, "127.0.0.1", FAKE_IRC_PORT, ssl=ctx)
        return self

    async def handle(self, reader, writer):
        writer.write(b":fake.irc NOTICE * :*** fake server ready\r\n")
        writer.write(b"PING :fake123\r\n")
        await writer.drain()
        try:
            while True:
                line = await asyncio.wait_for(reader.readline(), timeout=15)
                if not line:
                    break
                text = line.decode("utf-8", "replace").strip()
                self.received.append(text)
                upper = text.upper()
                if upper.startswith("PING"):
                    writer.write(b"PONG " + line.split(b" ", 1)[1])
                    await writer.drain()
                elif upper.startswith("PRIVMSG"):
                    # Echo back as if another user replied.
                    parts = text.split(" ", 2)
                    target = parts[1] if len(parts) > 1 else "#test"
                    body = parts[2] if len(parts) > 2 else ""
                    if body.startswith(":"):
                        body = body[1:]
                    writer.write((":echo!e@h PRIVMSG %s :echo: %s\r\n"
                                  % (target, body)).encode())
                    await writer.drain()
        except (asyncio.TimeoutError, ConnectionError):
            pass
        finally:
            writer.close()

    async def stop(self):
        self.server.close()
        await self.server.wait_closed()


def recv_until(sock, marker, timeout=15, limit=65536):
    sock.settimeout(timeout)
    buf = b""
    end = time.time() + timeout
    while marker not in buf and len(buf) < limit:
        chunk = sock.recv(4096)
        if not chunk:
            break
        buf += chunk
        if time.time() > end:
            break
    return buf


def main():
    failures = []

    def check(name, cond, detail=""):
        print(("PASS " if cond else "FAIL ") + name
              + (" | " + str(detail) if detail and not cond else ""))
        if not cond:
            failures.append(name)

    tmp = tempfile.mkdtemp(prefix="cb-gwtest-")
    cert = os.path.join(tmp, "cert.pem")
    key = os.path.join(tmp, "key.pem")
    r = run(["openssl", "req", "-x509", "-newkey", "rsa:2048",
             "-keyout", key, "-out", cert, "-days", "1", "-nodes",
             "-subj", "/CN=127.0.0.1", "-addext",
             "subjectAltName=IP:127.0.0.1"])
    if r.returncode != 0:
        print("FAIL openssl cert generation\n" + r.stderr)
        return 1

    async def scenario():
        fake = await FakeIrc(cert, key).start()
        env = dict(os.environ,
                   GATEWAY_PASSWORD=GW_PASSWORD,
                   GATEWAY_LISTEN_HOST="127.0.0.1",
                   GATEWAY_LISTEN_PORT=str(GW_PORT),
                   GATEWAY_IRC_HOST="127.0.0.1",
                   GATEWAY_IRC_PORT=str(FAKE_IRC_PORT),
                   GATEWAY_TLS_VERIFY="false",
                   GATEWAY_HEALTH_HOST="127.0.0.1",
                   GATEWAY_HEALTH_PORT=str(GW_HEALTH_PORT),
                   GATEWAY_LOG_LEVEL="WARNING")
        gw = subprocess.Popen([sys.executable, SERVER_PY], env=env,
                              stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        try:
            # Wait for health.
            health = None
            for _ in range(100):
                try:
                    with urllib.request.urlopen(
                            "http://127.0.0.1:%d/healthz" % GW_HEALTH_PORT,
                            timeout=2) as resp:
                        health = json.loads(resp.read().decode())
                    break
                except Exception:
                    await asyncio.sleep(0.2)
            check("gateway boots, health ok",
                  health is not None and health.get("status") == "ok", health)

            loop = asyncio.get_running_loop()

            # 1. Wrong password is rejected.
            def wrong_pw():
                s = socket.create_connection(("127.0.0.1", GW_PORT), timeout=10)
                s.sendall(b"nope-not-the-password\n")
                return s.recv(1024)
            data = await loop.run_in_executor(None, wrong_pw)
            check("wrong password rejected", b"ERROR" in data, data[:60])

            # 2. Full relay: login, greeting, PING/PONG, PRIVMSG echo.
            def session():
                s = socket.create_connection(("127.0.0.1", GW_PORT), timeout=10)
                s.sendall((GW_PASSWORD + "\n").encode())
                s.sendall(b"NICK gwfunc\n")
                s.sendall(b"USER gwfunc 0 * :gw functional test\n")
                buf = recv_until(s, b"PING")
                if b"PING" in buf:
                    tok = buf.split(b"PING", 1)[1].split(b"\r\n")[0]
                    s.sendall(b"PONG" + tok + b"\r\n")
                s.sendall(b"PRIVMSG #test :hello back\r\n")
                echo = recv_until(s, b"echo: hello back")
                s.close()
                return buf, echo
            greeting, echo = await loop.run_in_executor(None, session)
            check("server greeting relayed", b"fake server ready" in greeting,
                  greeting[:100])
            check("client PONG relayed upstream",
                  any(l.startswith("PONG") for l in fake.received),
                  fake.received)
            check("NICK/USER relayed upstream",
                  any("NICK gwfunc" in l for l in fake.received)
                  and any(l.startswith("USER ") for l in fake.received),
                  fake.received)
            check("PRIVMSG echo round-trips", b"echo: hello back" in echo,
                  echo[:120])
        finally:
            gw.terminate()
            try:
                gw.wait(timeout=10)
            except subprocess.TimeoutExpired:
                gw.kill()
            await fake.stop()
        return

    asyncio.run(scenario())
    print("---")
    if failures:
        print("FAILURES: %d (%s)" % (len(failures), ", ".join(failures)))
        return 1
    print("gateway functional test: all green")
    return 0


if __name__ == "__main__":
    sys.exit(main())
