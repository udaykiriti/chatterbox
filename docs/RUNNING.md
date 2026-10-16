# Running Chatterbox — Detailed Guide

This guide covers building, testing, and running both the MIDlet (Java ME IRC client) and the Gateway Server (Python TLS relay).

---

## Prerequisites

### Required Tools

| Tool | Version | Install Command / Download |
|------|---------|---------------------------|
| **JDK** | 8–21 | `sudo apt install openjdk-17-jdk` (Ubuntu/Debian) |
| **Apache Ant** | 1.10+ | `sudo apt install ant` |
| **Sun WTK 2.5.2** | 2.5.2 | [Oracle Archive](https://www.oracle.com/java/technologies/java-me-sdk-downloads.html) or `sudo apt install sun-java-wtk` |
| **ProGuard** | 7.x+ | [SourceForge](https://sourceforge.net/projects/proguard/files/proguard/) |
| **Python** | 3.11+ | `sudo apt install python3.11 python3.11-venv` |

### Environment Variables

Set these in your shell profile (`~/.bashrc`, `~/.zshrc`) or export before building:

```bash
# Required for preverify + emulator
export WTK_HOME=/opt/WTK2.5.2

# Optional but recommended for smaller JAR
export PROGUARD_HOME=/opt/proguard7.4

# Optional: for signing JAR (real device install)
export KEYSTORE=~/my.keystore
export KEY_ALIAS=myalias
export KEYSTORE_PASS=storepass
export KEY_PASS=keypass
```

Verify they're set:
```bash
echo $WTK_HOME
echo $PROGUARD_HOME
```

---

## Project Layout

```
chatterbox/
├── midlet/              # Java ME MIDlet source
│   └── src/com/chatterbox/
│       ├── proto/       # IRC protocol (parser, codec, CTCP, SASL, etc.)
│       ├── core/        # Session, channels, commands, flood control
│       ├── net/         # Connection, reconnect logic
│       ├── rms/         # RecordStore persistence (profiles, settings, logs)
│       └── ui/          # LCDUI screens + Canvas chat view
├── test/                # Unit tests (desktop JDK)
│   ├── shim/            # Minimal JUnit 3 shim
│   └── src/com/chatterbox/test/
├── gateway/             # Python TLS relay server
│   ├── server.py
│   ├── Dockerfile
│   └── deploy/
├── stub/                # MIDP/CLDC API stubs for desktop compilation
├── build.xml            # Ant build script
└── docs/
    └── RUNNING.md       # This file
```

---

## Building the MIDlet

### Full Release Build (Recommended)

```bash
cd /home/uday/chatterbox
ant dist
```

This runs: `clean → compile → preverify → obfuscate → jar → jad`

**Output:**
- `dist/chatterbox.jar` — The MIDlet JAR (< 300 KB)
- `dist/chatterbox.jad` — JAD descriptor for OTA install

### Individual Build Targets

```bash
# Clean all build artifacts
ant clean

# Compile only (no WTK needed)
ant compile

# Preverify classes (requires WTK_HOME)
ant preverify

# ProGuard shrink/optimize/obfuscate (requires PROGUARD_HOME)
ant obfuscate

# Package JAR only
ant jar

# Generate JAD only
ant jad
```

### Signing the JAR (for real devices)

```bash
# Requires KEYSTORE, KEY_ALIAS, KEYSTORE_PASS, KEY_PASS env vars
ant sign
```

**Note:** Nokia S60 requires a Trusted/Manufacturer certificate for `ssl://` permission. Self-signed JARs will fail TLS on device. Use the gateway (see below) as a workaround.

---

## Running Unit Tests (Desktop)

Tests run on the desktop JDK — no WTK or ProGuard needed.

```bash
ant test
```

**Expected output:** `238 tests, all green` (or similar count)

Test classes:
- `ProtocolParserTest` — IRC line parsing, CTCP, numerics
- `CoreTest` — Commands, string utils, session logic
- `SessionTest` — Channel buffers, nick lists, highlights

---

## Running the Gateway Server

The gateway accepts plain TCP from the MIDlet and establishes TLS to the real IRC server.

### Option 1: Docker (Quickest)

```bash
cd /home/uday/chatterbox/gateway

# Build image
docker build -t chatterbox-gateway .

# Run container
docker run -d \
  --name chatterbox-gw \
  -p 6667:6667 \
  -e GATEWAY_PASSWORD="your-shared-secret" \
  -e GATEWAY_IRC_HOST=irc.libera.chat \
  -e GATEWAY_IRC_PORT=6697 \
  chatterbox-gateway
```

**Verify it's running:**
```bash
docker logs chatterbox-gw
# Should show: "Gateway listening on 0.0.0.0:6667"
```

### Option 2: Bare Metal (systemd)

```bash
cd /home/uday/chatterbox/gateway

# 1. Create service user
sudo useradd -r -s /bin/false gateway

# 2. Create install directory
sudo mkdir -p /opt/chatterbox-gateway
sudo cp server.py /opt/chatterbox-gateway/

# 3. Create venv and install deps
sudo python3 -m venv /opt/chatterbox-gateway/.venv
sudo /opt/chatterbox-gateway/.venv/bin/pip install --upgrade pip

# 4. Install systemd service
sudo cp deploy/chatterbox-gateway.service /etc/systemd/system/
# Edit the service file to set GATEWAY_PASSWORD
sudo vim /etc/systemd/system/chatterbox-gateway.service

# 5. Start service
sudo systemctl daemon-reload
sudo systemctl enable --now chatterbox-gateway

# Check status
sudo systemctl status chatterbox-gateway
journalctl -u chatterbox-gateway -f
```

### Gateway Configuration (Environment Variables)

| Variable | Default | Required | Description |
|----------|---------|----------|-------------|
| `GATEWAY_PASSWORD` | — | **Yes** | Shared secret; client sends as first line |
| `GATEWAY_LISTEN_HOST` | `0.0.0.0` | No | Bind address |
| `GATEWAY_LISTEN_PORT` | `6667` | No | Plain TCP port for clients |
| `GATEWAY_IRC_HOST` | `irc.libera.chat` | No | Target IRC server |
| `GATEWAY_IRC_PORT` | `6697` | No | Target IRC port (TLS) |
| `GATEWAY_IRC_PASSWORD` | (empty) | No | PASS sent to IRC server |
| `GATEWAY_RATE_LIMIT` | `10` | No | Client→IRC chunks/sec (flood cap; replies not throttled) |
| `GATEWAY_LOG_LEVEL` | `INFO` | No | `DEBUG`, `INFO`, `WARNING`, `ERROR` |
| `GATEWAY_TLS_VERIFY` | `true` | No | Set `false` for self-signed IRC certs |

---

## Running on the Emulator (WTK)

```bash
# Requires WTK_HOME set
ant install-emu
```

This launches the WTK emulator and installs the JAD. In the emulator:
1. Open **Chatterbox** from the app list
2. Configure settings (see below)
3. Press **Options → Connect**

---

## Configuring the MIDlet (First Run)

On first launch, the app opens the **Settings** screen. Fill in:

| Field | Example Value | Notes |
|-------|---------------|-------|
| **Server** | `irc.libera.chat` | IRC network hostname |
| **Port** | `6697` | TLS port (or 6667 for plain) |
| **Use TLS** |  checked | Tries `ssl://` first |
| **Nick** | `yournick` | Primary nickname |
| **Alt Nick** | `yournick_` | Fallback if taken |
| **Channels** | `#channel1,#channel2 key` | Comma-separated; `key` optional |
| **Gateway Host** | `your.server.ip` | If using gateway |
| **Gateway Port** | `6667` | Gateway plain port |
| **Gateway Pass** | `your-shared-secret` | Must match `GATEWAY_PASSWORD` |

Press **Options → Save**, then **Options → Connect**.

---

## Connection Flow

```
┌─────────────┐     ssl://6697     ┌──────────────┐
│  MIDlet     │ ───────────────── │  IRC Server  │
│  (Phone)    │                    │  (TLS)       │
└─────────────┘                    └──────────────┘
       │
       │ (if TLS fails or disabled)
       ▼
┌─────────────┐     plain TCP      ┌──────────────┐     TLS      ┌──────────────┐
│  MIDlet     │ ───────────────── │   Gateway    │ ──────────  │  IRC Server  │
│  (Phone)    │  (sends password)  │  (your VPS)  │              │  (TLS)       │
└─────────────┘                    └──────────────┘              └──────────────┘
```

**Key point:** Enable **Use TLS** in settings. The MIDlet tries `ssl://irc.host:6697` first. Only if that fails does it fall back to the gateway.

---

## Key Map (Nokia E63/E71/E72/C3)

| Key | Action |
|-----|--------|
| **↑ / ↓** | Scroll chat history |
| **9 / 8** | Page up / down |
| **\* (Star) / Green Call** | Send message |
| **# (Hash)** | Toggle input  scroll focus |
| **1** | Nick list |
| **2** | Command menu |
| **3** | Cycle (part + rejoin) |
| **0** | Toggle topic header |
| **7** | Toggle timestamps |
| **← / →** | Move caret in input |
| **Tab** | Nick completion (cycle) |
| **C / Clear** | Backspace |
| **Left Softkey** | Menu (context-sensitive) |
| **Right Softkey** | Back / Close |

---

## Installing on a Real Device

### OTA (WAP Push) — Recommended

1. Host `dist/chatterbox.jad` and `dist/chatterbox.jar` on a web server
2. Set MIME types:
   - `.jad` → `text/vnd.sun.j2me.app-descriptor`
   - `.jar` → `application/java-archive`
3. Open the JAD URL in the phone's browser

### Bluetooth

1. Pair phone  PC
2. Send both files via OBEX
3. Open from Inbox / Received files

### Memory Card

1. Copy both files to `E:/Others/` (or `mass_memory/`)
2. Open with File Manager

### PC Suite / Nokia Suite

Use "Install Applications" → select the JAD.

---

## Troubleshooting

### Build Issues

| Error | Fix |
|-------|-----|
| `WTK_HOME not set` | `export WTK_HOME=/opt/WTK2.5.2` |
| `preverify not found` | Ensure `$WTK_HOME/bin/preverify` exists |
| `ProGuard not found` | Set `PROGUARD_HOME` or install ProGuard 7+ |
| `class file version 55.0` | Use JDK 8–21; source/target 1.3/1.1 in build.xml |

### Runtime Issues

| Symptom | Cause | Fix |
|---------|-------|-----|
| "Security error" on connect | Self-signed JAR, no ssl permission | Use gateway; disable TLS in settings |
| Connection times out | Firewall / wrong port | Check gateway port 6667 open |
| "Invalid password" | Gateway password mismatch | Ensure `GATEWAY_PASSWORD` = MIDlet Gateway Pass |
| Nick already in use | Nick taken on network | Set Alt Nick; `/nick newnick` after connect |
| No channels joined | Channels field empty | Fill Settings → Channels (comma-separated) |

### Gateway Issues

```bash
# Check gateway logs
docker logs chatterbox-gw

# Or for systemd
journalctl -u chatterbox-gateway -f

# Test gateway manually
telnet localhost 6667
# Type: your-shared-secret
# Should see: :gateway NOTICE * :Connected to irc.libera.chat:6697
```

---

## Development Workflow

```bash
# 1. Make code changes
vim midlet/src/com/chatterbox/ui/ChatCanvas.java

# 2. Run tests
ant test

# 3. Build and test on emulator
ant dist && ant install-emu

# 4. For gateway changes
cd gateway
# Edit server.py
docker build -t chatterbox-gateway .
docker stop chatterbox-gw && docker rm chatterbox-gw
docker run -d --name chatterbox-gw -p 6667:6667 -e GATEWAY_PASSWORD=secret -e GATEWAY_IRC_HOST=irc.libera.chat chatterbox-gateway
```

---

## Useful Commands Reference

```bash
# Build everything
ant dist

# Run tests only
ant test

# Clean build
ant clean

# Install to emulator
ant install-emu

# Sign JAR (if keystore configured)
ant sign

# Check JAR size
ls -lh dist/chatterbox.jar

# View JAD
cat dist/chatterbox.jad

# Gateway logs (Docker)
docker logs -f chatterbox-gw

# Gateway logs (systemd)
journalctl -u chatterbox-gateway -f
```

---

## Next Steps

- Read `midlet/src/com/chatterbox/ui/ChatterboxMIDlet.java` for entry point
- Explore `midlet/src/com/chatterbox/proto/IrcParser.java` for protocol handling
- Check `gateway/server.py` for gateway internals
- Run `ant test` and review test output for expected behavior