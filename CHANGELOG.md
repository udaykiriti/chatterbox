# Changelog

All notable changes to Chatterbox are documented here.

Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), SemVer.

---

## [Unreleased]

### Added
- Comprehensive documentation in `docs/` (12 guides)
- MkDocs site configuration for static documentation site
- Gateway TLS pinning: `GATEWAY_TLS_CA` (trust only the given root) and
  `GATEWAY_TLS_SHA256` (refuse mismatched IRC certificates)
- Gateway health endpoint: optional HTTP `/healthz` with uptime and live
  connection count (`GATEWAY_HEALTH_PORT`, localhost by default)
- Python gateway test suite: relay no-loss, auth (non-ASCII), pinning, health
- Pre-shared-key end-to-end encryption: per-profile passphrase → HKDF-SHA256 →
  ChaCha20-Poly1305 (RFC 8439); wire format `E2E:1:<base64(nonce ‖ ct ‖ tag)>`,
  nonce derived from SHA-256(key‖nick‖counter), AAD bound to sender + target,
  long texts auto-chunked; Settings toggle and `/e2e` status command
- `E2ETest` suite (24 tests): codec, session + command integration
- RFC 8439 test vectors in `CryptoTest` (ChaCha20 §2.3.2/§2.4.2, Poly1305
  §2.5.2/§2.5.2 A.3, AEAD §2.8.2 encrypt/decrypt/tamper/wrong-AAD)
- Strict base64 decoder in `Sasl` (whitespace-tolerant, padded/unpadded input)
- Gateway health endpoint: optional HTTP `/healthz` with uptime and live
  connection count (`GATEWAY_HEALTH_PORT`, localhost by default)
- Python gateway test suite: relay no-loss, auth (non-ASCII), pinning, health
- E2E key fingerprint (`/e2e`): SHA-256 of the traffic key displayed as 4 hex
  groups so peers can verify they share the same passphrase out of band
- CI workflow: test suite + gateway syntax check run on every push/PR

### Fixed
- `build.xml` was unusable (#1): an illegal `--` inside the header XML comment
  aborted every Ant invocation; desktop compiles used MIDP levels 1.3/1.1
  (rejected by any JDK ≥ 12), and the test target never compiled the JUnit
  shim. Now: desktop/device compile split — `ant test` runs without WTK on
  modern JDKs, device builds keep the WTK bootclasspath and 1.3/1.1 levels
- **Poly1305 rewritten** (donna-32 style): the RFC 8439 vectors exposed broken
  limb loading, an over-permissive top-limb clamp mask (0x00ffffff → 0x000fffff),
  a tail-block pad bit written to the wrong position, and missing inter-word
  carries when adding `s`; the MAC now reproduces every RFC vector byte-exactly
- Gateway relay no longer drops the chunk being relayed when the rate limit is
  exhausted — it waits for a token instead (data preserved, ~8 KiB in flight,
  TCP backpressure on the sender); throttling now applies only to the
  client → IRC direction, gateway → client is unthrottled
- Half-closed TCP sessions now end promptly: EOF on either relay leg closes
  both sides instead of idling until the watchdog
- Gateway password comparison: `hmac.compare_digest` on UTF-8 bytes (non-ASCII
  passwords previously raised `TypeError` on str input)
- `GATEWAY_RATE_LIMIT=0` disables the flood cap instead of stalling the relay
- Documentation test counts corrected everywhere (README badge, guides, changelog):
  229 tests passing

### Fixed
- Startup crash in the emulator (`NoSuchMethodError: Form.<init>(String,
  String[])`): the fatal-error screen, command menu, and all List/ChoiceGroup
  screens used constructors that exist only in the desktop stubs, not in
  MIDP 2.0 — fatal on real devices too. All screens migrated to spec
  constructors; `Notifier` uses `Display.vibrate()` (no `Vibrator` class in
  MIDP); `SocketFactory` uses `SecureConnection` + `setSocketOption` +
  `openInput/OutputStream`
- `RmsStore` rewritten against the spec `RecordStore` API
  (`openRecordStore`/`enumerateRecords`, sequential ids); logical ids
  preserved via a 4-byte id prefix per record. The old code called a
  fictional `RecordStore.open()` and could never persist on a device
- Desktop stubs aligned with JSR-118 signatures (incl. a working in-memory
  `RecordStore`); emulator `Choice` constants documented as inverted vs spec
- Repaint pump thread never restarted after `pause()` (frozen screen after
  minimise/restore); `ChatView` wrap-cache token could collide on
  same-second bursts (stale scrollback); `layout()` measured through a stale
  graphics font

### Changed
- Render path: cached fonts/metrics/header/prompt, snapshot only on ring
  advance, single coalesced repaint per event (was two full paints per
  keystroke/scroll), 500 ms pump while backgrounded
- Writer thread: adaptive 250 ms/1 s idle sleep, keepalive PING throttled to
  ≥ 30 s (was re-pinged every tick), one clock read per loop; 4 KB socket
  reads; integer token-bucket ceil (no FPU); allocation-free
  `indexOfIgnoreCase`, cached message timestamps, nick-colour memo,
  pre-folded nick lookup, forward `dumpTo`, lazy `LineReader` vectors
- Full suite (233 tests) verified green after every change; app compiles at
  device language level 1.3 / class level 1.1; gateway verified live
  (auth reject, TLS relay to Libera.Chat, `/healthz`)

### Added
- Double Ratchet (v2) end-to-end encryption for private chats: same
  passphrase bootstrap, no handshake — first speaker uses fresh DH against
  the peer's deterministic key, DH carried in the `E2E:2:` header so replies
  DH-ratchet forward; forward secrecy from the first reply, restart
  self-heals via one PSK retry, out-of-order past 1000 dropped, forged-gap
  DoS capped; channels stay on v1 PSK (no IRC fan-out primitive); v1 still
  decodes; `/theme <dark|light|high|amber>` command; 17 new tests (255 green)

---

## [1.2.0] - 2024-01-15

### Added
- TLS gateway server (Python asyncio) with Docker & systemd support
- Multiple server profiles stored in RMS
- Highlight rules with regex/word-boundary matching
- Ignore list with hostmask wildcards
- Persistent scrollback logs (1000 lines/channel in RMS)
- Auto-away on background, auto-back on foreground
- NickServ/ChanServ identification (ServiceIdentify)
- CTCP VERSION reply, ACTION support
- Dark/Light/Auto themes with timestamp toggle
- Vibrate + beep on highlight/PM (S60)
- URL detection and browser launch
- 129 unit tests (protocol, core, session)

### Changed
- Reconnect backoff: 2s → 60s exponential (was fixed 30s)
- Flood protection: token bucket 2 msg/2s (was simple queue)
- JAR size optimized to < 300 KB with ProGuard
- Protocol parser: RFC 2812 + IRCv3 message tags support

### Fixed
- Nick completion cycling order (most recent first)
- Private message tab close on `/close`
- WHOIS cache TTL expiration
- RMS corruption on abrupt shutdown
- S60 5th Ed touch input handling

---

## [1.1.0] - 2023-06-20

### Added
- Custom Canvas chat view (replaced TextBox)
- Word-wrapped messages with nick colors
- Scrollback with ↑/↓, page 9/8
- Channel tabs with unread/highlight badges
- Softkey menus (context-sensitive)
- Commands: /join, /part, /msg, /me, /nick, /topic, /quit, /query, /whois, /clear, /away, /back, /cycle, /names, /kick, /mode, /who, /raw, /help, /close
- Multiple channels auto-join on connect
- Keepalive PING (90s) with lag display
- Settings screen with gateway config

### Changed
- Migrated from TextBox to Canvas for performance
- Ring buffer message history (200 lines/channel)
- Nick list with mode prefixes (@ + % & ~ !)

---

## [1.0.0] - 2022-11-01

### Added
- Initial release: IRC client for MIDP 2.0 / CLDC 1.1
- Basic connection: server, port, TLS, nick, channels
- RFC 1459/2812 parser (PRIVMSG, JOIN, PART, QUIT, NICK, MODE, numerics)
- LCDUI Forms for settings, service list
- RMS persistence for single profile
- ProGuard build integration
- WTK emulator support
- Ant build script (compile → preverify → ProGuard → JAR/JAD)

---

## [0.9.0] - 2022-08-15

### Added
- Prototype: socket connection, basic IRC parsing
- Minimal UI: server list, channel list, chat
- Desktop compilation with MIDP stubs
- JUnit 3 test shim (no external deps)

---

## Version Scheme

| Version | Meaning |
|---------|---------|
| MAJOR | Breaking changes (protocol, config format, RMS schema) |
| MINOR | New features, backward compatible |
| PATCH | Bug fixes, no new features |

---

## Release Tags

- `v1.2.0` — Gateway, profiles, highlights, logs, themes
- `v1.1.0` — Canvas UI, commands, tabs, keepalive
- `v1.0.0` — Initial stable release