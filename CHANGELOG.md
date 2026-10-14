# Changelog

All notable changes to Chatterbox are documented here.

Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), SemVer.

---

## [Unreleased]

### Added
- Comprehensive documentation in `docs/` (12 guides)
- MkDocs site configuration for static documentation site

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