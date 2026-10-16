---
id: security
title: Security
triggers: [security, sanitize, encrypt, decrypt, password, obfuscate, vulnerability, attack]
packages: [proto, crypto, rms, gateway]
complexity: hard
---

# Security Skill

Work with security features — input sanitization, encryption, and secure coding practices.

## When to Use

- Handling user input or IRC data
- Modifying encryption or authentication code
- Reviewing code for security issues
- Understanding the security model
- Adding features that handle sensitive data

## Security Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Security Layers                       │
├─────────────────────────────────────────────────────────┤
│  Layer 1: Input Sanitization (IrcSanitizer)             │
│  Layer 2: E2E Encryption (crypto/)                      │
│  Layer 3: TLS Gateway (gateway/)                        │
│  Layer 4: Password Obfuscation (PasswordObfuscator)      │
│  Layer 5: ProGuard Obfuscation (build)                  │
└─────────────────────────────────────────────────────────┘
```

## Input Sanitization

**All IRC input must pass through `IrcSanitizer`** before display or storage.

### What IrcSanitizer Does

| Threat | Mitigation |
|--------|-----------|
| Control characters | Stripped (0x00-0x1F) |
| Null bytes | Removed |
| ANSI escape sequences | Stripped |
| Overlong UTF-8 | Rejected |
| Formatting abuse | Limited |

### Using IrcSanitizer

```java
// In Session or CommandProcessor
String raw = line.getMessage();
String clean = IrcSanitizer.sanitize(raw);

// Now safe to display or store
display.showMessage(clean);
```

### Adding Sanitization Rules

In `proto/IrcSanitizer.java`:
```java
public static String sanitize(String input) {
    if (input == null) return "";

    StringBuffer sb = new StringBuffer(input.length());
    for (int i = 0; i < input.length(); i++) {
        char c = input.charAt(i);
        // Add your rule:
        if (c == '\u0007') {  // BEL character
            continue;  // Skip
        }
        if (c >= 0x20 && c <= 0x7E) {
            sb.append(c);
        } else if (c == '\n' || c == '\r' || c == '\t') {
            sb.append(c);
        }
        // else: skip non-printable
    }
    return sb.toString();
}
```

## E2E Encryption

End-to-end encryption uses ChaCha20-Poly1305 with Double Ratchet.

### Key Management

```java
// Generate identity key (once)
byte[] privateKey = Curve25519.generatePrivateKey();
byte[] publicKey = Curve25519.derivePublicKey(privateKey);

// Store private key securely (obfuscated in RMS)
String obfuscated = PasswordObfuscator.obfuscate(
    Base64.encode(privateKey));
ProfileStore.save("identityKey", obfuscated);
```

### Encrypting Messages

```java
// Via E2E class
byte[] plaintext = message.getBytes("UTF-8");
byte[] ciphertext = E2E.encrypt(peerPublicKey, plaintext);

// Send as CTCP
String encoded = Base64.encode(ciphertext);
session.sendCtcp(peerNick, "E2E", encoded);
```

### Decrypting Messages

```java
// Automatic in Session
// 1. Receive CTCP E2E message
// 2. Decode Base64
// 3. E2E.decrypt(privateKey, peerPublicKey, ciphertext)
// 4. Verify Poly1305 tag
// 5. Return plaintext
```

## Password Obfuscation

**Never store passwords in plain text.**

```java
// Before storing in RMS
String obfuscated = PasswordObfuscator.obfuscate(password);

// When using
String password = PasswordObfuscator.deobfuscate(obfuscated);
```

### How It Works

`PasswordObfuscator` uses XOR with a fixed key + byte rotation. This is **obfuscation, not encryption** — it prevents casual inspection but is not cryptographically secure.

## TLS Gateway

The gateway provides TLS for phones that can't verify certificates.

### Certificate Pinning

```java
// In ConnectionConfig
config.certificatePins = new String[] {
    "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="
};

// Verify in SocketFactory
if (!verifyPin(serverCert, config.certificatePins)) {
    throw new SecurityException("Certificate pin mismatch");
}
```

## Secure Coding Practices

### Input Validation

```java
// Always validate input
public void setNick(String nick) {
    if (nick == null || nick.length() == 0 || nick.length() > 32) {
        throw new IllegalArgumentException("Invalid nick");
    }
    if (!nick.matches("^[a-zA-Z_\\[\]\\\\^{}|`][a-zA-Z0-9_\\[\]\\\\^{}|`-]*$")) {
        throw new IllegalArgumentException("Invalid nick characters");
    }
    this.nick = nick;
}
```

### Integer Overflow

```java
// Check for overflow
int delay = baseDelay * (1 << (attempts - 1));
if (delay < 0 || delay > MAX_DELAY) {
    delay = MAX_DELAY;
}
```

### Array Bounds

```java
// Always check bounds
if (index >= 0 && index < array.length) {
    return array[index];
}
return null;
```

### Null Checks

```java
// Check for null
if (socket != null && socket.isOpen()) {
    socket.send(data);
}
```

## Security Checklist for New Features

- [ ] All user input sanitized via `IrcSanitizer`
- [ ] No sensitive data in logs
- [ ] Passwords obfuscated before storage
- [ ] No hardcoded keys or credentials
- [ ] Input validation on all public methods
- [ ] Bounds checking on all array access
- [ ] Null checks on all object references
- [ ] No `String concatenation` in SQL (N/A — no SQL)
- [ ] TLS used for all network connections
- [ ] Certificate pinning where applicable

## Common Vulnerabilities

### Buffer Overflow

```java
// BAD: No bounds check
byte[] buffer = new byte[100];
System.arraycopy(input, 0, buffer, 0, input.length);

// GOOD: Check bounds
if (input.length > buffer.length) {
    throw new IllegalArgumentException("Input too large");
}
System.arraycopy(input, 0, buffer, 0, input.length);
```

### Format String

```java
// BAD: User input in format
String.format("Hello %s", userInput);  // Safe in Java

// But be careful with IRC formatting
String message = userInput;  // Don't interpret as format codes
```

### Integer Truncation

```java
// BAD: Silent truncation
int length = (int) longValue;

// GOOD: Check range
if (longValue > Integer.MAX_VALUE) {
    throw new IllegalArgumentException("Value too large");
}
int length = (int) longValue;
```

### Timing Attacks

```java
// BAD: Early return on mismatch
public boolean verifyMAC(byte[] a, byte[] b) {
    for (int i = 0; i < a.length; i++) {
        if (a[i] != b[i]) return false;  // Timing leak!
    }
    return true;
}

// GOOD: Constant-time comparison
public boolean verifyMAC(byte[] a, byte[] b) {
    if (a.length != b.length) return false;
    int result = 0;
    for (int i = 0; i < a.length; i++) {
        result |= a[i] ^ b[i];
    }
    return result == 0;
}
```

## Testing Security

### Security Test Suite

`SecurityTest` has 29 tests covering:
- `IrcSanitizer` — control chars, null bytes, ANSI escapes
- `PasswordObfuscator` — round-trip, different inputs

### Adding Security Tests

```java
public void testSanitizerMyNewAttack() {
    String input = "evil\u0000input";
    String result = IrcSanitizer.sanitize(input);
    assertFalse(result.contains("\u0000"));
}
```

## Reporting Security Issues

If you find a security vulnerability:
1. **Do not** open a public issue
2. Email the maintainer privately
3. Include: description, impact, reproduction steps
4. Allow time for fix before disclosure

## Anti-Patterns

```java
// WRONG: Logging sensitive data
logger.info("User password: " + password);

// RIGHT: Never log passwords
logger.info("User authenticated: " + username);
```

```java
// WRONG: Using weak random for crypto
Random random = new Random();  // Not secure!

// RIGHT: Use crypto-secure random
SecureRandom random = new SecureRandom();
byte[] nonce = new byte[12];
random.nextBytes(nonce);
```

```java
// WRONG: String comparison for secrets
if (inputPassword.equals(storedPassword)) {  // Timing attack!
    // ...
}

// RIGHT: Constant-time comparison
public boolean constantTimeEquals(String a, String b) {
    if (a.length() != b.length()) return false;
    int result = 0;
    for (int i = 0; i < a.length(); i++) {
        result |= a.charAt(i) ^ b.charAt(i);
    }
    return result == 0;
}
```

## Code Template: Adding a Security Test

```java
// In test/src/com/chatterbox/test/SecurityTest.java

public void testMyNewSecurityFeature() {
    // Test that malicious input is handled safely
    String malicious = "\u0000\u0001\u0002<script>alert(1)</script>";
    String result = IrcSanitizer.sanitize(malicious);

    // Verify dangerous content is removed
    assertFalse(result.contains("\u0000"));
    assertFalse(result.contains("<script>"));
    assertFalse(result.contains("\u0001"));
}
```
