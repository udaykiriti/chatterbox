---
id: gateway
title: Gateway
triggers: [gateway, tls, relay, python, asyncio, ssl, docker, systemd, deploy]
packages: [gateway]
complexity: medium
---

# Gateway Skill

Work with the Python TLS relay server that enables secure IRC connections for legacy phones.

## When to Use

- Modifying gateway server behavior
- Adding new gateway configuration options
- Debugging gateway connection issues
- Deploying the gateway
- Understanding the gateway protocol

## Overview

The gateway is a **Python 3.11+ asyncio TLS relay** that sits between the phone and the IRC network. It handles TLS verification (which many legacy phones can't do) and forwards raw IRC traffic.

```
Phone (plaintext)  ──→  Gateway (TLS)  ──→  IRC Server
         ↑                                              │
         └──────────────────────────────────────────────┘
```

## File Structure

```
gateway/
├── server.py                      # Main gateway (469 lines)
├── Dockerfile                     # Container build (python:3.11-slim)
└── deploy/
    ├── chatterbox-gateway.service # systemd unit
    └── chatterbox-gateway.env.example  # Environment template
```

## Running the Gateway

### Direct

```bash
cd gateway
python3 server.py
```

### Docker

```bash
cd gateway
docker build -t chatterbox-gateway .
docker run -p 6697:6697 chatterbox-gateway
```

### systemd

```bash
sudo cp gateway/deploy/chatterbox-gateway.service /etc/systemd/system/
sudo cp gateway/deploy/chatterbox-gateway.env.example /etc/chatterbox-gateway.env
sudo systemctl enable --now chatterbox-gateway
```

## Configuration

All configuration via environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `GATEWAY_HOST` | `0.0.0.0` | Bind address |
| `GATEWAY_PORT` | `6697` | Listen port |
| `GATEWAY_PASSWORD` | — | **Required.** Auth password |
| `GATEWAY_MAX_CONN` | `10` | Max concurrent connections |
| `GATEWAY_RATE_LIMIT` | `30` | Messages per minute per client |
| `GATEWAY_TARPIT_DELAY` | `5` | Brute-force delay (seconds) |
| `GATEWAY_CERT_PINS` | — | Comma-separated SHA-256 cert pins |
| `GATEWAY_LOG_LEVEL` | `INFO` | Logging level |

## Protocol

The gateway uses a simple line-based protocol:

```
# Client connects
< GATEWAY_HELLO Chatterbox Gateway v1.0

# Client authenticates
> AUTH <password>
< AUTH_OK

# Client sends IRC command
> :nick!user@host PRIVMSG #channel :Hello
< :server 353 nick = #channel :@nick

# Client disconnects
> QUIT :Bye
< BYE
```

## Security Features

| Feature | Implementation |
|---------|---------------|
| TLS 1.2+ | `ssl.PROTOCOL_TLS_SERVER` with minimum version |
| Certificate pinning | SHA-256 pin verification |
| Rate limiting | Token bucket per client |
| Brute-force protection | Exponential backoff tarpit |
| Connection limits | Per-IP and global max |
| Input validation | Line length limits, character filtering |

## Modifying the Gateway

### Adding a new command

In `gateway/server.py`, find the command handler:

```python
async def handle_client(self, reader, writer):
    # ...
    if line.startswith("AUTH "):
        await self.handle_auth(line, writer)
    elif line.startswith("QUIT"):
        await self.handle_quit(writer)
    # Add your command here:
    elif line.startswith("MYCMD "):
        await self.handle_mycmd(line, writer)
```

### Adding a new config option

1. Add to `gateway/deploy/chatterbox-gateway.env.example`
2. Read in `gateway/server.py`:
```python
my_config = os.environ.get("GATEWAY_MY_CONFIG", "default")
```
3. Update `docs/gateway.md`

### Adding rate limiting

```python
# In Connection class
self.rate_limiter = RateLimiter(
    max_tokens=config.rate_limit,
    refill_rate=config.rate_limit / 60.0
)
```

## Anti-Patterns

```python
# WRONG: Not using await for async operations
def handle_client(self, reader, writer):
    data = reader.read(1024)  # Blocks event loop!

# RIGHT: Always use await
async def handle_client(self, reader, writer):
    data = await reader.read(1024)
```

```python
# WRONG: Not handling connection errors
async def handle_client(self, reader, writer):
    while True:
        data = await reader.read(1024)
        # Process data...

# RIGHT: Handle disconnections gracefully
async def handle_client(self, reader, writer):
    try:
        while True:
            data = await reader.read(1024)
            if not data:
                break
            # Process data...
    except (ConnectionResetError, BrokenPipeError):
        pass
    finally:
        writer.close()
```

```python
# WRONG: Logging sensitive data
logger.info(f"Client connected with password: {password}")

# RIGHT: Never log passwords
logger.info(f"Client authenticated from {addr}")
```

```python
# WRONG: No input validation
line = await reader.readline()
self.process(line)  # Could be huge!

# RIGHT: Validate input
line = await reader.readline()
if len(line) > 512:
    await self.send_error("Line too long")
    continue
self.process(line)
```

## Code Template: Adding a New Gateway Command

```python
# In gateway/server.py

async def handle_mycmd(self, line: str, writer: StreamWriter) -> None:
    """Handle MYCMD command."""
    # Parse command
    parts = line.split(" ", 1)
    if len(parts) < 2:
        await self.send_error("MYCMD requires an argument")
        return

    arg = parts[1]

    # Validate
    if len(arg) > 100:
        await self.send_error("Argument too long")
        return

    # Process
    result = self.process_mycmd(arg)

    # Send response
    await self.send(f"MYCMD_OK {result}")

# In handle_client, add routing:
elif line.startswith("MYCMD "):
    await self.handle_mycmd(line, writer)
```

## Testing the Gateway

```bash
# Syntax check
python3 -m py_compile gateway/server.py

# Start server
GATEWAY_PASSWORD=test python3 gateway/server.py &

# Connect with openssl
openssl s_client -connect localhost:6697

# Or with netcat (plaintext auth)
nc localhost 6697
```

## Deployment Checklist

- [ ] Set strong `GATEWAY_PASSWORD`
- [ ] Configure `GATEWAY_CERT_PINS` for IRC server
- [ ] Set `GATEWAY_MAX_CONN` appropriately
- [ ] Enable `GATEWAY_RATE_LIMIT`
- [ ] Configure firewall (allow only port 6697)
- [ ] Set up log rotation
- [ ] Enable systemd auto-restart
- [ ] Monitor with health endpoint

## Troubleshooting

### Clients can't connect
- Check `GATEWAY_PASSWORD` is set
- Verify port is open: `ss -tlnp | grep 6697`
- Check logs: `journalctl -u chatterbox-gateway`

### TLS handshake fails
- Verify certificate is valid and not expired
- Check `GATEWAY_CERT_PINS` matches server cert
- Test with `openssl s_client -connect host:6697`

### Rate limiting too aggressive
- Increase `GATEWAY_RATE_LIMIT`
- Check for misconfigured clients flooding
- Monitor logs for rate limit hits
