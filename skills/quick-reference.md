---
id: quick-reference
title: Quick Reference
triggers: [quick, reference, cheat, sheet, summary, commands, paths, config]
packages: []
complexity: easy
---

# Quick Reference Card

Single-page cheat sheet for common operations.

## Build Commands

```bash
ant clean          # Clean build artifacts
ant stubs          # Compile stubs only
ant compile        # Compile midlet only
ant test           # Compile and run tests
ant dist           # Full distribution build
ant run            # Run in emulator
ant install        # Install on device
ant sign           # Sign JAR
```

## Test Commands

```bash
ant test                                    # All 233 tests
java -cp build/classes:build/tests com.chatterbox.test.TestMain
java -cp build/classes:build/tests com.chatterbox.test.ProtocolParserTest
java -cp build/classes:build/tests com.chatterbox.test.CryptoTest
```

## Documentation Commands

```bash
make serve         # Preview docs locally
make build         # Build static site
make deploy        # Deploy to GitHub Pages
mkdocs build --strict  # Build with warnings as errors
```

## Gateway Commands

```bash
cd gateway
python3 server.py                    # Run gateway
python3 -m py_compile server.py      # Syntax check
docker build -t chatterbox-gateway . # Build container
```

## Key File Paths

| File | Purpose |
|------|---------|
| `build.xml` | Ant build script |
| `proguard.cfg` | ProGuard config |
| `mkdocs.yml` | MkDocs config |
| `midlet/src/com/chatterbox/ui/ChatterboxMIDlet.java` | App entry point |
| `midlet/src/com/chatterbox/core/Session.java` | Central session |
| `midlet/src/com/chatterbox/core/CommandProcessor.java` | IRC commands |
| `midlet/src/com/chatterbox/proto/IrcParser.java` | IRC parsing |
| `midlet/src/com/chatterbox/crypto/E2E.java` | E2E encryption |
| `gateway/server.py` | TLS relay server |
| `test/src/com/chatterbox/test/TestMain.java` | Test runner |

## Environment Variables

| Variable | Purpose |
|----------|---------|
| `WTK_HOME` | Sun WTK installation path |
| `GATEWAY_PASSWORD` | Gateway auth password |
| `GATEWAY_HOST` | Gateway bind address |
| `GATEWAY_PORT` | Gateway listen port |
| `GATEWAY_MAX_CONN` | Max concurrent connections |
| `GATEWAY_RATE_LIMIT` | Messages per minute |
| `GATEWAY_CERT_PINS` | Certificate pins |

## IRC Commands

| Command | Description |
|---------|-------------|
| `/join #channel` | Join a channel |
| `/part #channel` | Leave a channel |
| `/nick newnick` | Change nickname |
| `/msg nick message` | Send private message |
| `/me action` | Send action |
| `/away message` | Set away status |
| `/quit message` | Disconnect |
| `/whois nick` | Get user info |
| `/ignore nick` | Ignore user |
| `/highlight pattern` | Add highlight rule |

## Java ME Constraints

| Constraint | Alternative |
|-----------|-------------|
| No generics | Use raw types |
| No lambdas | Use anonymous classes |
| No `String.split()` | Use `Str.split()` |
| No `java.security` | Use `crypto/` package |
| No floating point | Use integer math |
| No reflection | Direct instantiation |
| No `ObjectOutputStream` | Use `DataOutputStream` |
| ~1 MB heap | Minimize allocations |

## Test Suites

| Suite | Tests | Coverage |
|-------|-------|----------|
| `ProtocolParserTest` | 32 | IRC parsing |
| `CoreTest` | 31 | Core utilities |
| `SessionTest` | 83 | Protocol flow |
| `CryptoTest` | 21 | Crypto primitives |
| `X25519Test` | 9 | Curve25519 |
| `SecurityTest` | 29 | Sanitization |
| `E2ETest` | 28 | E2E encryption |
| **Total** | **233** | |

## Package Dependencies

```
proto/ ← core/ ← net/
  ↑       ↑       ↑
crypto/ ← rms/   ui/
```

## Common Patterns

### Send IRC Message
```java
session.send("PRIVMSG " + target + " :" + message);
```

### Sanitize Input
```java
String clean = IrcSanitizer.sanitize(rawInput);
```

### Obfuscate Password
```java
String obfuscated = PasswordObfuscator.obfuscate(password);
```

### Encrypt Message
```java
byte[] ciphertext = E2E.encrypt(peerPublicKey, plaintext);
```

### Write Test
```java
public void testSomething() {
    // Arrange
    // Act
    // Assert
    assertEquals(expected, actual);
}
```
