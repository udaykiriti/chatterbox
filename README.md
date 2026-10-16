# Chatterbox — IRC Client for Nokia S60 / Series 40 (MIDP 2.0)

![Chatterbox](https://img.shields.io/badge/platform-Java_ME-orange) ![MIDP](https://img.shields.io/badge/MIDP-2.0-blue) ![CLDC](https://img.shields.io/badge/CLDC-1.1-green) ![Build](https://img.shields.io/badge/build-Ant-yellow) ![Tests](https://img.shields.io/badge/tests-229_passing-brightgreen)

A production-ready IRC client for classic Nokia QWERTY phones (E63, E71, E72, C3, etc.) running Symbian S60 3rd/5th Edition or Series 40 3rd/5th Edition. Includes a companion TLS gateway server for networks that require modern TLS.

---

##  Features

| Area | Details |
|------|---------|
| **Network** | Multiple server profiles (RMS), TLS-first with plain fallback via gateway, exponential backoff reconnect (2 s → 60 s), 90 s keepalive PING, lag display |
| **Protocol** | Full RFC 2812 parser, PING/PONG, PRIVMSG/NOTICE, JOIN/PART/QUIT/NICK/KICK/MODE/TOPIC, numerics 001/332/353/366/433/401-404/ERROR, CTCP ACTION + VERSION reply |
| **Session** | Per-channel ring buffer (200 lines), nick lists with modes (@ + % & ~ !), unread/highlight badges, private message tabs |
| **UI (LCDUI + Canvas)** | Server/channel list with badges, word-wrapped chat canvas with scrollback (↑/↓), nick colours, timestamps toggle, highlight on own nick, inline input with Tab nick completion, softkey menus, vibrate+beep on mention/PM |
| **Commands** | `/join` `/part` `/msg` `/me` `/nick` `/topic` `/quit` `/query` `/whois` `/clear` `/away` `/back` `/cycle` `/names` `/kick` `/mode` `/who` `/raw` `/e2e` `/help` `/close` |
| **E2E (pre-shared-key)** | ChaCha20-Poly1305 (RFC 8439) from a per-profile passphrase set in Settings, `/e2e` status display, AAD bound to sender+target, long texts auto-chunked, unreadable payloads render as placeholders |
| **Robustness** | All I/O in try/catch, malformed lines never crash, backgrounding via pauseApp/startApp keeps socket alive, clean QUIT on exit |
| **Extras** | Local RMS scrollback log, URL detection, dark/light themes, < 300 KB JAR |

---

##  Project Structure

```
chatterbox/
├── midlet/
│   └── src/com/chatterbox/
│       ├── proto/     # Protocol layer (no UI, no MicroEdition) — unit-testable
│       ├── core/      # Session state, events, string helpers, commands
│       ├── net/       # Connection, flood control, reconnect logic
│       ├── rms/       # Profiles, settings, local log (RecordStore)
│       └── ui/        # LCDUI screens + custom Canvas chat view
├── test/
│   ├── shim/          # Minimal JUnit 3 shim (no external deps)
│   └── src/com/chatterbox/test/
│       ├── ProtocolParserTest.java
│       ├── CoreTest.java
│       ├── SessionTest.java
│       ├── CryptoTest.java
│       ├── X25519Test.java
│       ├── SecurityTest.java
│       └── E2ETest.java
├── gateway/
│   ├── server.py      # Python 3.11 asyncio TLS relay
│   ├── Dockerfile
│   └── deploy/chatterbox-gateway.service  # systemd unit
├── stub/              # MIDP/CLDC API stubs for desktop compilation
├── build.xml          # Ant build (compile → preverify → ProGuard → JAR/JAD)
└── README.md          # This file
```

---

## ️ Build Requirements

| Tool | Version | Purpose |
|------|---------|---------|
| **JDK** | 8–21 | Desktop compilation & tests |
| **Apache Ant** | 1.10+ | Build orchestration |
| **Sun WTK 2.5.2** | (or Nokia S60 SDK) | `preverify`, `midpapi20.jar`, `cldcapi11.jar`, emulator |
| **ProGuard** | 7.x+ | Shrink/optimize/obfuscate (optional but recommended) |
| **Python** | 3.11+ | Gateway server |

Set these as environment variables or pass to Ant:
```bash
export WTK_HOME=/opt/WTK2.5.2           # required for preverify + emulator
export PROGUARD_HOME=/opt/proguard7.4   # optional
export KEYSTORE=my.keystore             # for signing
export KEY_ALIAS=myalias
export KEYSTORE_PASS=storepass
export KEY_PASS=keypass
```

---

## ️ Build Commands

```bash
# Full release build (clean → compile → preverify → ProGuard → JAR + JAD)
ant dist

# Run unit tests on desktop JDK (no WTK/ProGuard needed)
ant test

# Sign the JAR (requires keystore env vars)
ant sign

# Install to WTK emulator
ant install-emu

# Clean everything
ant clean
```

**Output:** `dist/chatterbox.jar` + `dist/chatterbox.jad` (ready for OTA install or Bluetooth transfer).

---

##  Signing for Real Devices

Nokia S60 requires a **Trusted/Manufacturer** certificate for `javax.microedition.io.Connector.ssl` permission. Options:

1. **Self-signed (untrusted)** — Works for socket:// but **ssl:// will be blocked** with "Security error". Good for testing plain fallback via gateway.
2. **Thawte/VeriSign/GlobalSign MIDlet certificate** — Expensive, deprecated.
3. **Open-signed online** (Symbian Signed) — Service discontinued.
4. **Developer certificate** — Create with `makecert` + `signtool` from Nokia SDK, install `.cer` on phone via Bluetooth/email, then sign JAR with that cert.

> **Practical workaround:** Use the gateway (plain TCP) and skip device-side TLS. The gateway handles TLS to the IRC network.

---

##  Installation on Phone

| Method | Steps |
|--------|-------|
| **OTA (WAP Push)** | Host `chatterbox.jad` + `chatterbox.jar` on a web server with correct MIME types (`text/vnd.sun.j2me.app-descriptor`, `application/java-archive`). Open JAD URL in phone browser. |
| **Bluetooth** | Pair phone → PC, send `chatterbox.jad` + `chatterbox.jar` via OBEX. Open from Inbox / Received files. |
| **Memory Card** | Copy both files to `E:/Others/` (or `mass_memory/`), open with File Manager. |
| **PC Suite / Nokia Suite** | Use "Install Applications" → select JAD. |

**First run:** The app opens the **Settings** screen. Fill in:
- **Server:** `irc.libera.chat` (or your network)
- **Port:** `6697` (TLS) or `6667` (plain)
- **Use TLS:**  (tries ssl:// first)
- **Nick / Alt nick:** your choice
- **Channels:** `#channel1,#channel2 key` (comma-separated)
- **Gateway host/port/pass:** if using the relay (see below)

Press **Options → Save**, then **Options → Connect**.

---

##  Gateway Server (TLS Relay)

The MIDlet can connect to a **plain TCP gateway** that terminates TLS to the real IRC server. This avoids device-side TLS cert issues.

### Quick Start (Docker)

```bash
cd gateway
docker build -t chatterbox-gateway .
docker run -d \
  --name chatterbox-gw \
  -p 6667:6667 \
  -e GATEWAY_PASSWORD="your-shared-secret" \
  -e GATEWAY_IRC_HOST=irc.libera.chat \
  -e GATEWAY_IRC_PORT=6697 \
  chatterbox-gateway
```

### Bare Metal (systemd)

```bash
# 1. Create user & venv
sudo useradd -r -s /bin/false gateway
sudo mkdir -p /opt/chatterbox-gateway
sudo cp server.py /opt/chatterbox-gateway/
sudo python3 -m venv /opt/chatterbox-gateway/.venv
sudo /opt/chatterbox-gateway/.venv/bin/pip install --upgrade pip

# 2. Install service
sudo cp deploy/chatterbox-gateway.service /etc/systemd/system/
# Edit /etc/systemd/system/chatterbox-gateway.service and set GATEWAY_PASSWORD
sudo systemctl daemon-reload
sudo systemctl enable --now chatterbox-gateway
```

### Gateway Configuration (env vars)

| Variable | Default | Description |
|----------|---------|-------------|
| `GATEWAY_PASSWORD` | *(required)* | Shared secret — client sends this as first line |
| `GATEWAY_LISTEN_HOST` | `0.0.0.0` | Bind address |
| `GATEWAY_LISTEN_PORT` | `6667` | Plain TCP port for clients |
| `GATEWAY_IRC_HOST` | `irc.libera.chat` | Target IRC server |
| `GATEWAY_IRC_PORT` | `6697` | Target IRC port (TLS) |
| `GATEWAY_IRC_PASSWORD` | *(optional)* | PASS sent to IRC server |
| `GATEWAY_RATE_LIMIT` | `10` | Client→IRC chunks/sec (flood cap; gateway→client is never throttled) |
| `GATEWAY_LOG_LEVEL` | `INFO` | DEBUG/INFO/WARNING/ERROR |
| `GATEWAY_TLS_VERIFY` | `true` | Set `false` for self-signed IRC certs |

### Phone Side Gateway Config

In the MIDlet **Settings** screen:
```
Gateway host:  your.server.ip
Gateway port:  6667
Gateway pass:  your-shared-secret
```
Enable **Use TLS** — the MIDlet will try `ssl://irc.host:6697` first, and **only** fall back to the gateway if that fails.

### ️ Security Note: Gateway vs Direct TLS
- **Direct TLS (ssl://)**: End-to-end encrypted. Passwords sent via SASL PLAIN are base64-encoded (not encrypted) but protected by TLS.
- **Gateway (plain TCP → TLS relay)**: Your client sends the gateway password in plaintext over TCP. The gateway then opens a TLS connection to the IRC server. Use **only on trusted networks** (your own VPS, home LAN). Enable "Require TLS" in Settings to disable fallback entirely.

---

## ️ Key Map (Nokia E63/E71/E72/C3)

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

##  Manual Test Checklist

### Emulator (WTK / Nokia S60 SDK)
- [ ] `ant dist` produces `dist/chatterbox.jar` < 300 KB
- [ ] `ant test` → **229 tests, all green**
- [ ] Launch in emulator: `ant install-emu`
- [ ] Create profile → Save → Connect → registers (001) → auto-joins channels
- [ ] Send/receive channel messages, private messages, /me actions
- [ ] Nick completion (Tab) cycles correctly
- [ ] Scroll history with ↑/↓, page with 9/8, snap to bottom on new msg
- [ ] Highlight badge appears on mention/PM, vibrate+beep triggers
- [ ] /nick changes nick, /topic sets topic, /whois shows info
- [ ] /part leaves channel, window marked "parted", auto-rejoin on /cycle
- [ ] Background app (red key / end call) → resume → connection alive
- [ ] Quit → clean QUIT sent, RMS settings persisted

### Real Device (Nokia E63/E71/E72/C3/C5/C6/E7)
- [ ] Install signed JAR (or self-signed + gateway)
- [ ] Connect via Wi-Fi → TLS succeeds or falls back to gateway
- [ ] Connect via 2G/3G → reconnect backoff works on signal loss
- [ ] Battery pull during chat → on restart, settings & log intact
- [ ] Long nick list (>50) renders without lag
- [ ] Non-Latin nicks (Cyrillic, Greek, CJK) display correctly
- [ ] Vibrate+beep on mention works in background (profile not Silent)

### Gateway
- [ ] Docker container starts, logs "Gateway listening on ..."
- [ ] Client connects, sends password, relay established
- [ ] Rate limiting kicks in at ~10 msg/s (burst handled)
- [ ] Idle timeout (5 min) closes stale connections
- [ ] systemd service restarts on crash, logs to journal

---

##  Security

### Threat Model
Chatterbox runs on legacy Java ME devices (Nokia S60/Series 40) with CLDC 1.1 / MIDP 2.0.
These platforms lack modern crypto APIs, secure enclaves, and certificate management.
The threat model assumes:
- **Device is trusted** (user's own phone, physically secured)
- **Network is hostile** (public Wi-Fi, carrier networks, MITM possible)
- **RMS storage is readable** by any MIDlet with same suite UID (no app sandbox on S60)

### Protections Implemented

| Layer | Protection |
|-------|------------|
| **Transport** | TLS 1.2+ to IRC server (direct) or TLS relay via gateway. Hostname verification enforced by platform. |
| **TLS Fallback** | Optional "Require TLS" setting (`requireTls=true`) disables plaintext/gateway fallback entirely. |
| **Gateway Auth** | Shared secret (min 16 chars) sent as first line. Per-IP connection limits (10) and rate limits (10 msg/s). Auth failures logged and tarpitted after 5 attempts/5 min. |
| **Credential Storage** | Passwords obfuscated in RMS using per-device XOR key (not encryption — CLDC 1.1 has no crypto). Prevents casual reading, not determined extraction. |
| **Input Sanitization** | All user-supplied IRC parameters (nicks, channels, messages) sanitized to prevent protocol injection (spaces, colons, CRLF, control chars stripped). |
| **ProGuard** | Release builds obfuscated, shrunk, and optimized. Class/method names renamed; line numbers retained for crash logs. |
| **Signed JAR** | Production JARs should be signed with a trusted certificate (`ant sign`) to enable `ssl://` permission on device. |

### Known Limitations
- **SASL PLAIN**: Password sent as base64 (essentially plaintext) inside TLS. Use only on trusted networks or with TLS.
- **Gateway Password**: Sent in plaintext over TCP to gateway. Never use gateway on untrusted Wi-Fi without "Require TLS" enabled.
- **RMS Obfuscation**: XOR with device properties is reversible. Do not store high-value passwords.
- **No Certificate Pinning**: MIDP `ssl://` uses platform trust store (often outdated on S60). Pinning not possible without native TLS stack.
- **DCC**: Disabled by default. File transfers expose IP and are unencrypted.

### Deployment Hardening (Gateway)
- **Docker**: Run with `--read-only --cap-drop=ALL --security-opt=no-new-privileges`
- **systemd**: Uses `ProtectSystem=strict`, `PrivateTmp=yes`, `NoNewPrivileges=yes`, `CapabilityBoundingSet=`
- **Environment**: `GATEWAY_PASSWORD` **must** be set via `/etc/chatterbox-gateway.env` (640, root:gateway) or Docker `.env` (600). No defaults.
- **TLS Verification**: `GATEWAY_TLS_VERIFY=true` enforced (no override in production).

### Generating Secure Passwords
```bash
# 32-char base64 (256 bits entropy)
openssl rand -base64 32
# Or diceware for memorability
```

---

##  License

MIT License — see `LICENSE` file.

---

##  Acknowledgements

- **MIDP/CLDC** — The platform that made mobile Java possible
- **ProGuard** — For keeping the JAR under 300 KB
- **Python asyncio** — For a concise, correct TLS relay
- **Nokia E-series keyboards** — The best mobile keyboards ever made

---

*Built with  for the love of physical keyboards and IRC.*