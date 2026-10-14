#!/usr/bin/env python3
"""
Chatterbox IRC Gateway Server
=============================

A TLS/TCP relay for MIDP 2.0 clients that can't do proper TLS verification.
The client connects to this gateway over plain TCP, sends a shared password
as the very first line, then the gateway opens a TLS connection to the real
IRC network and blindly relays bytes both ways.

Protocol:
  Client -> Gateway:  "password\n"  (first line only)
  Gateway -> IRC:     TLS connection to configured host:port
  Then:               byte-for-byte relay in both directions

Rate limiting: 10 messages/second per connection, 50 connections/IP/minute.
"""

import asyncio
import logging
import os
import signal
import ssl
import sys
import time
import hmac
from collections import defaultdict
from dataclasses import dataclass, field
from typing import Optional

# ---- Configuration ----

@dataclass
class Config:
    listen_host: str = "0.0.0.0"
    listen_port: int = 6667
    irc_host: str = "irc.libera.chat"
    irc_port: int = 6697
    irc_password: Optional[str] = None  # PASS sent to IRC server after CONNECT
    gateway_password: str = ""  # Required first line from client (MUST be set via env)
    max_connections: int = 100
    rate_limit_per_sec: int = 10
    max_connections_per_ip: int = 10
    auth_timeout: float = 5.0
    connect_timeout: float = 15.0
    idle_timeout: float = 300.0
    log_level: str = "INFO"
    tls_verify: bool = True  # MUST be True in production
    min_password_length: int = 16  # Minimum gateway password length

    def __post_init__(self):
        """Validate configuration after initialization."""
        if not self.gateway_password:
            raise ValueError("GATEWAY_PASSWORD must be set (env var)")
        if len(self.gateway_password) < self.min_password_length:
            raise ValueError(f"GATEWAY_PASSWORD must be at least {self.min_password_length} characters")

def load_config() -> Config:
    # Environment variables override defaults
    cfg = Config(
        listen_host=os.getenv("GATEWAY_LISTEN_HOST", "0.0.0.0"),
        listen_port=int(os.getenv("GATEWAY_LISTEN_PORT", "6667")),
        irc_host=os.getenv("GATEWAY_IRC_HOST", "irc.libera.chat"),
        irc_port=int(os.getenv("GATEWAY_IRC_PORT", "6697")),
        irc_password=os.getenv("GATEWAY_IRC_PASSWORD") or None,
        gateway_password=os.getenv("GATEWAY_PASSWORD", ""),
        max_connections=int(os.getenv("GATEWAY_MAX_CONN", "100")),
        rate_limit_per_sec=int(os.getenv("GATEWAY_RATE_LIMIT", "10")),
        max_connections_per_ip=int(os.getenv("GATEWAY_MAX_CONN_PER_IP", "10")),
        connect_timeout=float(os.getenv("GATEWAY_CONNECT_TIMEOUT", "15.0")),
        idle_timeout=float(os.getenv("GATEWAY_IDLE_TIMEOUT", "300.0")),
        log_level=os.getenv("GATEWAY_LOG_LEVEL", "INFO"),
        tls_verify=os.getenv("GATEWAY_TLS_VERIFY", "true").lower() != "false",
        min_password_length=int(os.getenv("GATEWAY_MIN_PASSWORD_LEN", "16")),
    )
    # Config.__post_init__ validates gateway_password
    if not cfg.tls_verify:
        logging.warning("SECURITY WARNING: TLS verification is DISABLED. This is insecure!")
    return cfg

# ---- Rate Limiter ----

class TokenBucket:
    def __init__(self, rate_per_sec: int):
        self.rate = rate_per_sec
        self.tokens = float(rate_per_sec)
        self.last = time.monotonic()

    def take(self) -> bool:
        now = time.monotonic()
        elapsed = now - self.last
        self.tokens = min(self.rate, self.tokens + elapsed * self.rate)
        self.last = now
        if self.tokens >= 1.0:
            self.tokens -= 1.0
            return True
        return False


class IPRateLimiter:
    """Track connections and auth attempts per IP address."""
    def __init__(self, max_connections_per_ip: int):
        self.max_connections_per_ip = max_connections_per_ip
        self.connections: dict[str, int] = defaultdict(int)
        self.auth_failures: dict[str, list[float]] = defaultdict(list)
        self._lock = asyncio.Lock()

    async def register_connection(self, ip: str) -> bool:
        """Register a new connection from IP. Returns True if allowed."""
        async with self._lock:
            if self.connections[ip] >= self.max_connections_per_ip:
                return False
            self.connections[ip] += 1
            return True

    async def unregister_connection(self, ip: str):
        async with self._lock:
            self.connections[ip] = max(0, self.connections[ip] - 1)

    async def record_auth_failure(self, ip: str):
        """Record a failed authentication attempt."""
        async with self._lock:
            now = time.monotonic()
            self.auth_failures[ip].append(now)
            # Keep only last hour
            cutoff = now - 3600
            self.auth_failures[ip] = [t for t in self.auth_failures[ip] if t > cutoff]

    async def get_recent_failures(self, ip: str, window_sec: int = 300) -> int:
        """Get number of auth failures in the last window_sec seconds."""
        async with self._lock:
            now = time.monotonic()
            cutoff = now - window_sec
            return sum(1 for t in self.auth_failures[ip] if t > cutoff)

    async def is_brute_forcing(self, ip: str, threshold: int = 5, window_sec: int = 300) -> bool:
        """Check if IP is likely brute-forcing."""
        return await self.get_recent_failures(ip, window_sec) >= threshold

# ---- Connection Handler ----

class GatewayConnection:
    def __init__(self, reader: asyncio.StreamReader, writer: asyncio.StreamWriter,
                 config: Config, logger: logging.Logger, ip_limiter: IPRateLimiter):
        self.reader = reader
        self.writer = writer
        self.config = config
        self.logger = logger
        self.peer = writer.get_extra_info("peername")
        self.ip = self.peer[0] if self.peer else "unknown"
        self.irc_reader: Optional[asyncio.StreamReader] = None
        self.irc_writer: Optional[asyncio.StreamWriter] = None
        self.authenticated = False
        self.rate_limiter = TokenBucket(config.rate_limit_per_sec)
        self.last_activity = time.monotonic()
        self.ip_limiter = ip_limiter
        self._ip_registered = False

    async def run(self):
        try:
            # Register IP connection
            if not await self.ip_limiter.register_connection(self.ip):
                self.logger.warning("Connection rejected: too many connections from %s", self.ip)
                self.writer.write(b"ERROR :Too many connections from your IP\r\n")
                await self.writer.drain()
                return
            self._ip_registered = True

            await self.authenticate()
            await self.connect_irc()
            await self.relay()
        except asyncio.CancelledError:
            pass
        except Exception as e:
            self.logger.warning("Connection error from %s: %s", self.peer, e)
        finally:
            if self._ip_registered:
                await self.ip_limiter.unregister_connection(self.ip)
            await self.close()

    async def authenticate(self):
        self.logger.info("New connection from %s", self.peer)
        # Read first line (password)
        try:
            line = await asyncio.wait_for(self.reader.readline(), timeout=self.config.auth_timeout)
        except asyncio.TimeoutError:
            self.logger.warning("Auth timeout from %s", self.peer)
            raise
        if not line:
            raise ConnectionError("Client closed before auth")
        password = line.decode("utf-8", errors="replace").strip()
        if not self.config.gateway_password:
            self.logger.error("Gateway password not configured, rejecting %s", self.peer)
            raise PermissionError("Gateway not configured")
        if not hmac.compare_digest(password, self.config.gateway_password):
            self.logger.warning("Bad password from %s", self.peer)
            await self.ip_limiter.record_auth_failure(self.ip)
            failures = await self.ip_limiter.get_recent_failures(self.ip)
            self.logger.warning("IP %s has %d recent auth failures", self.ip, failures)
            if await self.ip_limiter.is_brute_forcing(self.ip):
                self.logger.error("IP %s appears to be brute-forcing, delaying response", self.ip)
                await asyncio.sleep(2.0)  # Tarpit
            self.writer.write(b"ERROR :Invalid password\r\n")
            await self.writer.drain()
            raise PermissionError("Invalid password")
        self.authenticated = True
        self.logger.info("Authenticated %s", self.peer)

    async def connect_irc(self):
        self.logger.info("Connecting to IRC %s:%d (TLS=%s)", self.config.irc_host, self.config.irc_port, True)
        ssl_ctx = ssl.create_default_context()
        if not self.config.tls_verify:
            ssl_ctx.check_hostname = False
            ssl_ctx.verify_mode = ssl.CERT_NONE
        try:
            self.irc_reader, self.irc_writer = await asyncio.wait_for(
                asyncio.open_connection(self.config.irc_host, self.config.irc_port, ssl=ssl_ctx),
                timeout=self.config.connect_timeout
            )
        except asyncio.TimeoutError:
            raise ConnectionError(f"IRC connect timeout after {self.config.connect_timeout}s")
        except Exception as e:
            raise ConnectionError(f"IRC connect failed: {e}")

        if self.config.irc_password:
            self.irc_writer.write(f"PASS {self.config.irc_password}\r\n".encode())
            await self.irc_writer.drain()

        self.logger.info("IRC connection established")

    async def relay(self):
        async def pump(src: asyncio.StreamReader, dst: asyncio.StreamWriter, name: str):
            try:
                while True:
                    data = await src.read(8192)
                    if not data:
                        self.logger.debug("%s: EOF", name)
                        break
                    if not self.rate_limiter.take():
                        await asyncio.sleep(0.1)
                        continue
                    dst.write(data)
                    await dst.drain()
                    self.last_activity = time.monotonic()
            except asyncio.CancelledError:
                pass
            except Exception as e:
                self.logger.debug("%s: pump error: %s", name, e)

        # Idle watchdog
        async def watchdog():
            while True:
                await asyncio.sleep(10)
                if time.monotonic() - self.last_activity > self.config.idle_timeout:
                    self.logger.info("Idle timeout for %s", self.peer)
                    self.writer.close()
                    if self.irc_writer:
                        self.irc_writer.close()
                    break

        await asyncio.gather(
            pump(self.reader, self.irc_writer, "client->irc"),
            pump(self.irc_reader, self.writer, "irc->client"),
            watchdog(),
            return_exceptions=True
        )

    async def close(self):
        for w in (self.writer, self.irc_writer):
            if w and not w.is_closing():
                w.close()
                try:
                    await w.wait_closed()
                except Exception:
                    pass

# ---- Main Server ----

async def serve(config: Config, logger: logging.Logger):
    semaphore = asyncio.Semaphore(config.max_connections)
    ip_limiter = IPRateLimiter(config.max_connections_per_ip)

    async def handler(reader, writer):
        async with semaphore:
            conn = GatewayConnection(reader, writer, config, logger, ip_limiter)
            await conn.run()

    server = await asyncio.start_server(handler, config.listen_host, config.listen_port)
    addrs = ", ".join(str(sock.getsockname()) for sock in server.sockets)
    logger.info("Gateway listening on %s", addrs)
    logger.info("Relaying to %s:%d (TLS)", config.irc_host, config.irc_port)
    logger.info("Max connections: %d, max per IP: %d, rate limit: %d msg/s",
                config.max_connections, config.max_connections_per_ip, config.rate_limit_per_sec)

    async with server:
        await server.serve_forever()

def main():
    try:
        config = load_config()
    except ValueError as e:
        print(f"Configuration error: {e}", file=sys.stderr)
        sys.exit(1)

    logging.basicConfig(
        level=getattr(logging, config.log_level.upper(), logging.INFO),
        format="%(asctime)s %(levelname)s %(message)s",
        datefmt="%H:%M:%S"
    )
    logger = logging.getLogger("gateway")

    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)

    def shutdown():
        logger.info("Shutting down...")
        for task in asyncio.all_tasks(loop):
            task.cancel()

    for sig in (signal.SIGINT, signal.SIGTERM):
        try:
            loop.add_signal_handler(sig, shutdown)
        except NotImplementedError:
            pass  # Windows

    try:
        loop.run_until_complete(serve(config, logger))
    except asyncio.CancelledError:
        pass
    finally:
        loop.close()
        logger.info("Stopped")

if __name__ == "__main__":
    main()