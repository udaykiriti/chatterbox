# AGENTS.md

Guidance for AI agents working in the Chatterbox codebase.

## Project Overview

Chatterbox is a production-ready IRC client for classic Nokia QWERTY phones (E63, E71, E72, C3, etc.) running Symbian S60 or Series 40. It targets Java ME (MIDP 2.0 / CLDC 1.1) — a constrained environment with no generics, no lambdas, and ~1 MB heap target.

The project also includes a Python TLS gateway server that relays IRC connections for phones that cannot do proper TLS verification.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Device app | Java ME (MIDP 2.0, CLDC 1.1), source level 1.3 |
| Build | Apache Ant 1.10+ |
| Obfuscation | ProGuard 7.x+ |
| Desktop tests | Java 8+ with custom JUnit 3 shim |
| Gateway | Python 3.11+ (stdlib only, asyncio) |
| Docs | MkDocs with Material theme |
| CI | GitHub Actions (JDK 21, Python 3.12) |

## Directory Structure

```
chatterbox/
├── midlet/src/com/chatterbox/    # Main MIDlet source (61 files, ~13.9k lines)
│   ├── proto/                    # IRC protocol layer (pure Java, zero MIDP deps)
│   ├── core/                     # Session state, commands, events
│   ├── net/                      # Connection, flood control, reconnect
│   ├── rms/                      # Persistent storage (RecordStore)
│   ├── crypto/                   # E2E encryption primitives
│   └── ui/                       # LCDUI screens + custom Canvas
├── stub/                         # MIDP/CLDC API stubs for desktop compile
├── test/                         # Test suite (233 tests, 7 suites)
│   ├── shim/junit/framework/     # Minimal JUnit 3 shim
│   └── src/com/chatterbox/test/  # Test classes
├── gateway/                      # Python TLS relay server
│   ├── server.py                 # Main gateway (469 lines)
│   ├── Dockerfile
│   └── deploy/                   # systemd unit + env template
├── docs/                         # MkDocs documentation (12 guides)
├── build.xml                     # Ant build script
├── proguard.cfg                  # ProGuard configuration
└── mkdocs.yml                    # MkDocs configuration
```

## Build Commands

```bash
# Full build (compile, preverify, obfuscate, JAR/JAD)
ant dist

# Run tests
ant test

# Clean build artifacts
ant clean

# Build documentation
make build          # or: mkdocs build

# Serve docs locally
make serve          # or: mkdocs serve

# Gateway
cd gateway && python3 server.py
```

## Architecture Rules

### Java ME Constraints (MIDP 2.0 / CLDC 1.1)

- **No generics, no lambdas, no streams** — source level 1.3 for device code
- **No `java.util` collections** — use arrays and custom data structures
- **No reflection** — not available on CLDC
- **No `String.split()`** — use `IrcParser` or manual parsing
- **No `java.security`** — use custom crypto in `crypto/` package
- **~1 MB heap target** — be memory-conscious, avoid object churn
- **Keep JAR under 300 KB** — ProGuard obfuscation is mandatory

### Package Boundaries

- `proto/` — Pure Java, zero MIDP dependencies, fully unit-testable
- `core/` — Depends on `proto/` and `crypto/`, no UI dependencies
- `net/` — Depends on `core/`, handles all socket I/O
- `rms/` — Depends on `core/`, persistent storage only
- `crypto/` — Pure Java, zero dependencies, RFC-compliant primitives
- `ui/` — Depends on everything, LCDUI + Canvas only

### Testing Strategy

- All `proto/` and `crypto/` classes must have unit tests
- `core/` classes should have unit tests where possible
- `ui/` classes are tested via emulator/manual testing only
- Tests run on desktop JDK via custom stubs — no WTK required
- Run `ant test` before any commit

## Code Style

- **Java**: 4-space indent, braces on new line, no tabs
- **Naming**: `PascalCase` classes, `camelCase` methods/variables, `UPPER_SNAKE` constants
- **Comments**: Javadoc for public APIs, inline comments for non-obvious logic
- **Python**: PEP 8, 4-space indent, type hints where helpful

## Decision Trees

Use these to quickly identify which skill to load and where to look.

### "I need to add a new IRC command"
→ `skills/protocol.md` → "Adding a New IRC Command" (4 steps)
→ Then `skills/session.md` → "Adding a New Command" in CommandProcessor
→ Then `skills/test.md` → Add test in SessionTest

### "I need to add a new crypto primitive"
→ `skills/crypto.md` → "Adding a New Crypto Primitive" (3 steps)
→ Then `skills/test.md` → Add RFC test vectors in CryptoTest

### "I need to modify the gateway"
→ `skills/gateway.md` → "Modifying the Gateway"
→ Then `skills/docs.md` → Update docs/gateway.md

### "I need to add a new screen"
→ `skills/ui.md` → "Adding a New Screen" (3 steps)
→ Then `skills/emulator.md` → Test in emulator

### "I need to add persistent data"
→ `skills/rms.md` → "Adding Persistent Data" (3 steps)
→ Then `skills/rms.md` → "Migration" for schema changes

### "I need to fix a connection issue"
→ `skills/net.md` → "Troubleshooting"
→ Then `skills/emulator.md` → Test with network conditions

### "I need to update documentation"
→ `skills/docs.md` → "Updating Existing Pages"
→ Then `skills/docs.md` → "Spanish Documentation" if needed

### "I need to add a stub"
→ `skills/stub.md` → "Adding a New Stub" (3 steps)
→ Then `skills/stub.md` → "Common Stub Patterns"

### "I need to do a security review"
→ `skills/security.md` → "Security Checklist for New Features"
→ Then `skills/security.md` → "Common Vulnerabilities"

### "I need to test on emulator"
→ `skills/emulator.md` → "Running in the Emulator"
→ Then `skills/emulator.md` → "Testing Checklist"

### "I need to contribute"
→ `skills/contributing.md` → "Contribution Workflow" (4 steps)
→ Then `skills/contributing.md` → "Code Review Checklist"

## Cross-Skill Dependency Map

```
protocol.md ──→ session.md ──→ ui.md
     │              │
     ↓              ↓
crypto.md ──→ session.md ──→ net.md
     │              │
     ↓              ↓
security.md ←── rms.md ←── session.md
     ↑              ↑
     │              │
   stub.md ←── test.md ←── emulator.md
     ↑
     │
  build.md ──→ docs.md ──→ contributing.md
```

## Anti-Patterns

These are things AI agents commonly get wrong. Avoid them.

### Java ME Anti-Patterns

```java
// WRONG: Generics not available on CLDC 1.1
List<String> list = new ArrayList<String>();

// RIGHT: Use arrays or Vector
String[] array = new String[10];
Vector vector = new Vector();
```

```java
// WRONG: String.split() not available
String[] parts = input.split(" ");

// RIGHT: Use IrcParser or manual parsing
String[] parts = Str.split(input, " ");
```

```java
// WRONG: java.security not available
MessageDigest md = MessageDigest.getInstance("SHA-256");

// RIGHT: Use custom crypto
byte[] hash = SHA256.compute(input);
```

```java
// WRONG: Reflection not available
Class.forName("com.chatterbox.MyClass");

// RIGHT: Direct instantiation
MyClass obj = new MyClass();
```

```java
// WRONG: Floating point not available on CLDC 1.1
double value = 3.14;

// RIGHT: Use integer math
int value = 314;  // Scale by 100
```

```java
// WRONG: Object serialization not available
ObjectOutputStream oos = new ObjectOutputStream(baos);

// RIGHT: Manual serialization
DataOutputStream dos = new DataOutputStream(baos);
dos.writeUTF(name);
dos.writeInt(value);
```

### Security Anti-Patterns

```java
// WRONG: Storing password in plain text
ProfileStore.save("password", password);

// RIGHT: Obfuscate first
ProfileStore.save("password", PasswordObfuscator.obfuscate(password));
```

```java
// WRONG: Not sanitizing IRC input
display.showMessage(rawInput);

// RIGHT: Always sanitize
display.showMessage(IrcSanitizer.sanitize(rawInput));
```

```java
// WRONG: Reusing nonce with ChaCha20-Poly1305
byte[] nonce = new byte[12];  // Same nonce every time!

// RIGHT: Generate new nonce for each encryption
byte[] nonce = new byte[12];
random.nextBytes(nonce);
```

```java
// WRONG: Early return on MAC mismatch (timing attack)
for (int i = 0; i < a.length; i++) {
    if (a[i] != b[i]) return false;
}

// RIGHT: Constant-time comparison
int result = 0;
for (int i = 0; i < a.length; i++) {
    result |= a[i] ^ b[i];
}
return result == 0;
```

### Build Anti-Patterns

```bash
# WRONG: Forgetting to run tests before committing
git commit -m "feat: add feature"

# RIGHT: Always test first
ant test && git commit -m "feat: add feature"
```

```bash
# WRONG: Building without ProGuard
ant compile

# RIGHT: Full build with obfuscation
ant dist
```

## Error Message → Fix Mapping

| Error | Cause | Fix |
|-------|-------|-----|
| `cannot find symbol: List` | Using java.util on CLDC | Use arrays or Vector |
| `cannot find symbol: split` | String.split() not on CLDC | Use `Str.split()` or `IrcParser` |
| `cannot find symbol: MessageDigest` | java.security not on CLDC | Use `crypto/SHA256.java` |
| `cannot find symbol: for-each` | Enhanced for not on CLDC 1.1 | Use indexed for loop |
| `cannot find symbol: generics` | Generics not on CLDC 1.1 | Use raw types |
| `cannot find symbol: ObjectOutputStream` | Not on CLDC | Use `DataOutputStream` |
| `ClassNotFoundException: javax.microedition.*` | Stubs not compiled | Run `ant stubs` |
| `RecordStoreException: RecordStore not found` | Store doesn't exist | Use `openRecordStore(name, true)` |
| `SecurityException: Certificate pin mismatch` | Wrong server certificate | Update `GATEWAY_CERT_PINS` |
| `IOException: Connection refused` | Server unreachable | Check host/port, retry with backoff |
| `OutOfMemoryError` | Heap exhausted | Reduce object churn, use object pools |
| `Preverify failed` | WTK not configured | Set `WTK_HOME` environment variable |
| `ProGuard: can't find class` | Missing libraryjars | Check `proguard.cfg` paths |

## Common Tasks

### Adding a new IRC command

1. Add parsing in `proto/IrcParser.java` if needed
2. Add handling in `core/CommandProcessor.java`
3. Add UI trigger in `ui/` if user-facing
4. Add tests in `test/src/com/chatterbox/test/SessionTest.java`

### Adding a new crypto primitive

1. Implement in `crypto/` as pure Java (no dependencies)
2. Add RFC test vectors in `test/src/com/chatterbox/test/CryptoTest.java`
3. Integrate into `core/Session.java` if needed

### Modifying the gateway

1. Edit `gateway/server.py`
2. Syntax check: `python3 -m py_compile gateway/server.py`
3. Update `gateway/deploy/chatterbox-gateway.env.example` for new config
4. Update `docs/gateway.md`

### Updating documentation

1. Edit files in `docs/` (Markdown)
2. Run `make serve` to preview locally
3. Run `make build` to generate site
4. Spanish docs in `docs_es/` (keep in sync)

## CI Pipeline

GitHub Actions (`.github/workflows/ci.yml`):
- **Java tests**: Compiles stubs + midlet + tests, runs 233 tests on JDK 21
- **Gateway**: Syntax check on Python 3.12
- Triggers: every push to `main`, every PR to `main`

## Security Considerations

- All IRC input must pass through `IrcSanitizer`
- E2E encryption uses ChaCha20-Poly1305 (RFC 8439) with Double Ratchet
- Gateway uses TLS 1.2+, certificate pinning, rate limiting
- Passwords obfuscated via `PasswordObfuscator` before RMS storage
- ProGuard obfuscation required for release builds

## Key Files to Know

| File | Why It Matters |
|------|---------------|
| `midlet/src/com/chatterbox/ui/ChatterboxMIDlet.java` | App entry point, lifecycle |
| `midlet/src/com/chatterbox/core/Session.java` | Central session state |
| `midlet/src/com/chatterbox/core/CommandProcessor.java` | All IRC commands |
| `midlet/src/com/chatterbox/proto/IrcParser.java` | IRC line parsing |
| `midlet/src/com/chatterbox/crypto/E2E.java` | E2E encryption orchestration |
| `gateway/server.py` | TLS relay server |
| `build.xml` | Build orchestration |
| `test/src/com/chatterbox/test/TestMain.java` | Test runner entry point |

## File-Level Annotations

### `midlet/src/com/chatterbox/proto/` (10 files)

| File | Annotations |
|------|-------------|
| `IrcParser.java` | Parses raw IRC lines into IrcLine objects. Zero MIDP deps. |
| `IrcCodec.java` | Encodes/decodes IRC messages to/from bytes. Zero MIDP deps. |
| `IrcLine.java` | Immutable representation of a parsed IRC line. Zero MIDP deps. |
| `LineReader.java` | Buffered line reader for socket input. Zero MIDP deps. |
| `Ctcp.java` | CTCP message parsing and construction. Zero MIDP deps. |
| `Cap.java` | IRCv3 capability negotiation. Zero MIDP deps. |
| `Sasl.java` | SASL authentication (PLAIN, EXTERNAL). Zero MIDP deps. |
| `Dcc.java` | DCC file transfer protocol. Zero MIDP deps. |
| `Format.java` | IRC formatting codes (bold, color, etc.). Zero MIDP deps. |
| `Numeric.java` | IRC numeric reply constants. Zero MIDP deps. |
| `Utf8.java` | UTF-8 encoding/decoding helpers. Zero MIDP deps. |
| `IrcSanitizer.java` | Input sanitization (security-critical). Zero MIDP deps. |

### `midlet/src/com/chatterbox/core/` (18 files)

| File | Annotations |
|------|-------------|
| `Session.java` | Central session state (connection, channels, nicks). |
| `SessionManager.java` | Manages multiple server sessions. |
| `ChannelState.java` | Per-channel state (topic, nicks, modes). |
| `MessageRing.java` | Circular buffer for recent messages. |
| `Message.java` | Single message data class. |
| `CommandProcessor.java` | Parses and executes IRC commands. |
| `Sender.java` | Sends messages to IRC server. |
| `IrcEventListener.java` | Interface for session event callbacks. |
| `ServerState.java` | Server connection state. |
| `UserEntry.java` | User data (nick, ident, host, modes). |
| `IrcCase.java` | Case-insensitive string comparison. |
| `Str.java` | String utilities. |
| `Theme.java` | Color and appearance management. |
| `HighlightRules.java` | Highlight matching rules. |
| `IgnoreList.java` | Ignore list management. |
| `AutoAway.java` | Auto-away on idle. |
| `ServiceIdentify.java` | NickServ identification. |
| `WhoisCache.java` | WHOIS response caching. |
| `MessageHistory.java` | Message history buffer. |

### `midlet/src/com/chatterbox/net/` (4 files)

| File | Annotations |
|------|-------------|
| `IrcConnection.java` | Main connection class (connect, send, receive, reconnect). |
| `SocketFactory.java` | Socket creation and configuration. |
| `FloodQueue.java` | Rate limiting for outgoing messages. |
| `ConnectionConfig.java` | Connection parameters (host, port, TLS, timeout). |

### `midlet/src/com/chatterbox/rms/` (5 files)

| File | Annotations |
|------|-------------|
| `RmsStore.java` | Low-level RMS wrapper (open, read, write, close). |
| `ProfileStore.java` | IRC server profile storage (nick, server, port, etc.). |
| `Profile.java` | Data class for a server profile. |
| `LogStore.java` | Chat log persistence. |
| `PasswordObfuscator.java` | Password obfuscation before storage. |

### `midlet/src/com/chatterbox/crypto/` (8 files)

| File | Annotations |
|------|-------------|
| `E2E.java` | High-level E2E orchestration. |
| `ChaCha20.java` | Stream cipher (RFC 8439). |
| `Poly1305.java` | MAC (RFC 8439). |
| `ChaCha20Poly1305.java` | AEAD cipher (RFC 8439). |
| `HKDF.java` | Key derivation (RFC 5869). |
| `SHA256.java` | Hash function (FIPS 180-4). |
| `Curve25519.java` | ECDH key exchange (RFC 7748). |
| `DoubleRatchet.java` | Ratcheting protocol (Signal Protocol). |

### `midlet/src/com/chatterbox/ui/` (16 files)

| File | Annotations |
|------|-------------|
| `ChatterboxMIDlet.java` | App entry point, lifecycle, display. |
| `ChatCanvas.java` | Custom chat rendering surface. |
| `ChatView.java` | Chat view logic (scrolling, rendering). |
| `ServerListScreen.java` | Server selection screen. |
| `SettingsScreen.java` | Settings configuration. |
| `NickListScreen.java` | Channel nick list. |
| `ChannelListScreen.java` | Channel list screen. |
| `QuickConnect.java` | Quick connect dialog. |
| `LogViewer.java` | Log viewer screen. |
| `Notifier.java` | Notification/alert manager. |
| `InputLine.java` | Message input handling. |
| `NickComplete.java` | Tab-completion for nicks. |
| `ServiceScreen.java` | Service/nickserv screen. |
| `HighlightScreen.java` | Highlight rules management. |
| `IgnoreScreen.java` | Ignore list management. |
| `UrlHandler.java` | URL detection and handling. |

### `gateway/` (1 file)

| File | Annotations |
|------|-------------|
| `server.py` | Python asyncio TLS relay with auth, rate limiting, cert pinning. |

### `test/` (7 test classes)

| File | Annotations |
|------|-------------|
| `ProtocolParserTest.java` | 32 tests: IRC line parsing, CTCP, numerics. |
| `CoreTest.java` | 31 tests: Str, IrcCase, MessageRing, FloodQueue, ChannelState. |
| `SessionTest.java` | 83 tests: Full protocol flow, commands, auto-away. |
| `CryptoTest.java` | 21 tests: HKDF, HMAC-SHA256, SHA-256, RFC 8439. |
| `X25519Test.java` | 9 tests: RFC 7748 vectors, DH, non-canonical input. |
| `SecurityTest.java` | 29 tests: IrcSanitizer, PasswordObfuscator. |
| `E2ETest.java` | 28 tests: Pre-shared-key E2E codec, session integration. |
