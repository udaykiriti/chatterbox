# Chatterbox — MIDP 2.0 IRC Client with E2E Encryption

**A production-ready IRC client for Nokia S60/Series 40 (E63/E71/E72/C3) with Signal-style end-to-end encryption, TLS gateway fallback, and 197 passing unit tests.**

---

## [+] Project Structure

```
chatterbox/
├── midlet/src/com/chatterbox/
│   ├── proto/      # Protocol layer (zero UI deps) — IrcParser, IrcCodec, LineReader, Ctcp, Cap, Sasl, Dcc, Format, IrcSanitizer
│   ├── core/       # Session state, commands, crypto integration — Session, SessionManager, ChannelState, MessageRing, CommandProcessor, IgnoreList, HighlightRules, ServiceIdentify, AutoAway, WhoisCache, MessageHistory
│   ├── crypto/     # E2E primitives — Curve25519, ChaCha20, Poly1305, ChaCha20Poly1305, HKDF, SHA256, DoubleRatchet
│   ├── net/        # Network — IrcConnection, SocketFactory, FloodQueue, ConnectionConfig
│   ├── rms/        # Persistence — ProfileStore, Profile, LogStore, RmsStore, PasswordObfuscator
│   └── ui/         # LCDUI screens — ChatterboxMIDlet, ChatCanvas, ChatView, ServerListScreen, SettingsScreen, NickListScreen, ChannelListScreen, QuickConnect, LogViewer, Notifier, InputLine, NickComplete, ServiceScreen, HighlightScreen, IgnoreScreen, UrlHandler
├── gateway/        # Python 3.11 asyncio TLS relay
│   ├── server.py   # Asyncio TLS relay with password auth, rate limiting
│   ├── Dockerfile  # Container build
│   └── deploy/     # systemd unit
├── test/           # 197 unit tests (JUnit 3 shim, no external deps)
│   ├── shim/       # Minimal JUnit 3 clone
│   └── src/
├── build.xml       # Ant: compile → preverify → ProGuard → JAR/JAD
├── proguard.cfg    # Shrink/optimize config
├── .gitignore
├── README.md
└── PROJECT.md      # This file
```

---

## [>] Quick Start

### Prerequisites
- **JDK 8+** (tested with JDK 17)
- **Sun WTK 2.5.2** or **Nokia S60 SDK** (for `preverify`, `midpapi20.jar`, `cldcapi11.jar`, emulator)
- **ProGuard 7+** (optional, for obfuscation)
- **Python 3.11+** (for gateway)

### Environment Setup
```bash
export PATH=/path/to/jdk/bin:$PATH
export WTK_HOME=/opt/WTK2.5.2          # or your WTK/SDK path
export PROGUARD_HOME=/opt/proguard7.4  # optional
export KEYSTORE=my.keystore            # for signing
export KEY_ALIAS=myalias
export KEYSTORE_PASS=storepass
export KEY_PASS=keypass
```

---

## [+] Build

### Using Ant (full release build)
```bash
ant dist
```
Outputs: `dist/chatterbox.jar` + `dist/chatterbox.jad`

### Manual Compile (for desktop testing)
```bash
export PATH=/path/to/jdk/bin:$PATH

# Compile against stub MIDP APIs (desktop testing)
javac -encoding UTF-8 -cp build/stub -d build/classes $(find midlet/src -name '*.java')

# Preverify (requires WTK)
$WTK_HOME/bin/preverify -classpath $WTK_HOME/lib/cldcapi11.jar:$WTK_HOME/lib/midpapi20.jar -d build/preverified build/classes

# Obfuscate with ProGuard
java -jar $PROGUARD_HOME/lib/proguard.jar @proguard.cfg

# Package
jar cfm dist/chatterbox.jar midlet/MANIFEST.MF -C build/obfuscated .
```

---

## [*] Run Tests (Desktop JDK — No Emulator Needed)

```bash
export PATH=/path/to/jdk/bin:$PATH

# Compile tests
javac -encoding UTF-8 -cp build/classes -d build/tests $(find test/shim test/src -name '*.java')

# Run
java -cp build/classes:build/tests com.chatterbox.test.TestMain
```

**Expected:** `✔ 197 tests, all green`

| Suite | Tests | Coverage |
|-------|-------|----------|
| ProtocolParserTest | 35 | IRC line parsing, CTCP, numerics |
| CoreTest | 30 | Str, IrcCase, MessageRing, FloodQueue, ChannelState |
| SessionTest | 81 | Full protocol flow, commands, ratchet, auto-away, service identify |
| CryptoTest | 18 | HKDF, HMAC-SHA256, SHA256 |
| X25519Test | 9 | RFC 7748 vectors, DH, non-canonical input handling |
| SecurityTest | 24 | IrcSanitizer, PasswordObfuscator |

---

## [M] Run on Device

### 1. Build Release
```bash
ant dist          # produces dist/chatterbox.jar + dist/chatterbox.jad
ant sign          # requires keystore env vars
```

### 2. Install
| Method | Steps |
|--------|-------|
| **Bluetooth** | Pair phone → Send `.jad` + `.jar` |
| **Memory Card** | Copy to `E:/Others/` → Open in File Manager |
| **OTA (Web)** | Host `.jad` + `.jar` with MIME types `text/vnd.sun.j2me.app-descriptor` / `application/java-archive` |
| **Nokia Suite** | Tools → Install Applications → Select `.jad` |

### 3. First Run
1. Open **Chatterbox** → **Settings**
2. Add server: `irc.libera.chat:6697` (TLS ✓)
3. Set nick, alt-nick, channels: `#chan1,#chan2 key`
4. **Optional Gateway**: Your server IP `6667` + shared password
4. **Connect** — TLS first, falls back to gateway

---

## [G] Gateway Server (Python 3.11)

### Quick Start (Docker)
```bash
cd gateway
docker build -t chatterbox-gateway .
docker run -d -p 6667:6667 \
  -e GATEWAY_PASSWORD=secret \
  -e GATEWAY_IRC_HOST=irc.libera.chat \
  chatterbox-gateway
```

### Bare Metal (systemd)
```bash
sudo useradd -r -s /bin/false gateway
sudo mkdir -p /opt/chatterbox-gateway
sudo cp gateway/server.py /opt/chatterbox-gateway/
sudo python3 -m venv /opt/chatterbox-gateway/.venv
sudo /opt/chatterbox-gateway/.venv/bin/pip install --upgrade pip
sudo cp gateway/deploy/chatterbox-gateway.service /etc/systemd/system/
# Edit /etc/systemd/system/chatterbox-gateway.service → set GATEWAY_PASSWORD
sudo systemctl daemon-reload
sudo systemctl enable --now chatterbox-gateway
```

### Gateway Config (env vars)
| Variable | Default | Description |
|----------|---------|-------------|
| `GATEWAY_PASSWORD` | *required* | Shared secret (client sends as first line) |
| `GATEWAY_LISTEN_HOST` | `0.0.0.0` | Bind address |
| `GATEWAY_LISTEN_PORT` | `6667` | Client port (plain TCP) |
| `GATEWAY_IRC_HOST` | `irc.libera.chat` | Target IRC server |
| `GATEWAY_IRC_PORT` | `6697` | Target IRC port (TLS) |
| `GATEWAY_IRC_PASSWORD` | *(optional)* | PASS sent to IRC server |
| `GATEWAY_RATE_LIMIT` | `10` | Msgs/sec per connection |
| `GATEWAY_LOG_LEVEL` | `INFO` | DEBUG/INFO/WARNING/ERROR |
| `GATEWAY_TLS_VERIFY` | `true` | Set `false` for self-signed certs |

---

## [K] Key Map (Nokia E63/E71/E72/C3)

| Key | Action |
|-----|--------|
| **↑ / ↓** | Scroll chat history |
| **9 / 8** | Page up / down |
| **\* / Green Call** | Send message |
| **#** | Toggle input ↔ scroll focus |
| **1** | Nick list |
| **2** | Commands menu |
| **3** | Cycle (part + rejoin) |
| **0** | Toggle topic header |
| **7** | Toggle timestamps |
| **← / →** | Move caret |
| **Tab** | Nick completion (cycle) |
| **C / Clear** | Backspace |
| **Left Softkey** | Context menu |

---

## [C] Commands

| Command | Description |
|---------|-------------|
| `/join #ch [key]` | Join channel |
| `/part [#ch]` | Leave channel |
| `/msg nick text` | Private message |
| `/me action` | CTCP ACTION |
| `/nick newnick` | Change nick |
| `/topic [text]` | View/set topic |
| `/query nick` | Open private chat |
| `/whois nick` | User info |
| `/away [msg]` | Set away |
| `/back` | Remove away |
| `/clear` | Clear scrollback |
| `/cycle` | Part + rejoin |
| `/names` | Channel user list |
| `/kick nick [reason]` | Kick user |
| `/raw line` | Send raw IRC |
| `/help` | Show commands |

---

## [E] End-to-End Encryption

| Primitive | Implementation |
|-----------|----------------|
| **Key Exchange** | Curve25519 (RFC 7748) |
| **Cipher** | ChaCha20-Poly1305 (RFC 8439) |
| **Ratchet** | Signal Double Ratchet (forward + future secrecy) |
| **Key Derivation** | HKDF-SHA256 (RFC 5869) |
| **Hash** | SHA-256 (FIPS 180-4) |
| **SASL** | PLAIN / EXTERNAL |

**Flow:**
1. X3DH-style initial handshake (shared secret + DH ratchet keys)
2. Double Ratchet: each message advances symmetric ratchet
3. DH ratchet on new peer key → new root key + chain keys
4. Forward secrecy: compromise of current keys ≠ past messages
5. Future secrecy: compromise heals on next DH ratchet

---

## [S] Security

| Feature | Implementation |
|---------|----------------|
| **Input Sanitization** | `IrcSanitizer` — strips control chars, CRLF, colons, backslashes |
| **Password Storage** | XOR obfuscation with per-device key (`PasswordObfuscator`) |
| **TLS** | `ssl://` first, falls back to gateway plain TCP |
| **Certificate Pinning** | Not implemented (CLDC 1.1 limitation) |
| **Message Auth** | ChaCha20-Poly1305 AEAD (128-bit tag) |
| **Replay Protection** | Double Ratchet message numbers + skipped key cache |

---

## [B] Build Output Sizes

| Artifact | Size (approx) |
|----------|---------------|
| `chatterbox.jar` (obfuscated) | ~180 KB |
| `chatterbox.jad` | < 1 KB |
| ProGuard reduction | ~60% |

---

## [!] Troubleshooting

| Issue | Fix |
|-------|-----|
| `preverify` not found | Set `WTK_HOME` to WTK 2.5.2 path |
| `SecurityException: ssl` | Device cert store missing CA; use gateway |
| `OutOfMemoryError` | Increase heap: `-Xmx128m` for preverify |
| `SecurityException: untrusted` | Sign JAD/JAR with trusted cert; or use gateway |
| Connection drops on 2G/3G | Auto-reconnect with backoff (2s→60s) handles this |
| Nick completion not working | Tab only works at word start (after space/colon) |

---

## [L] License

MIT License — see `LICENSE` file.

---

## [A] Acknowledgements

- **MIDP/CLDC** — The platform that made mobile Java possible
- **ProGuard** — Keeping JAR under 200 KB
- **Python asyncio** — Concise, correct TLS relay
- **Signal Protocol** — Double Ratchet design
- **Nokia E-series keyboards** — Best mobile keyboards ever made

---

*Built with ☕ for the love of physical keyboards and IRC.*