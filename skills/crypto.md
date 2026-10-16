---
id: crypto
title: Crypto
triggers: [encrypt, decrypt, chacha, poly1305, curve25519, e2e, key, ratchet, hkdf, sha256, x25519]
packages: [crypto, core]
complexity: hard
---

# Crypto Skill

Work with end-to-end encryption primitives and the Double Ratchet implementation.

## When to Use

- Adding new crypto primitives
- Modifying E2E encryption flow
- Debugging encryption issues
- Understanding the crypto architecture
- Adding RFC test vectors

## Package Overview

All crypto classes are in `midlet/src/com/chatterbox/crypto/` — **pure Java, zero dependencies**.

| Class | Responsibility | Standard |
|-------|---------------|----------|
| `ChaCha20` | Stream cipher | RFC 8439 |
| `Poly1305` | MAC | RFC 8439 |
| `ChaCha20Poly1305` | AEAD cipher | RFC 8439 |
| `HKDF` | Key derivation | RFC 5869 |
| `SHA256` | Hash function | FIPS 180-4 |
| `Curve25519` | ECDH key exchange | RFC 7748 |
| `DoubleRatchet` | Ratcheting protocol | Signal Protocol |
| `E2E` | High-level E2E orchestration | — |

## Architecture

```
┌─────────────────────────────────────────────────┐
│                    E2E                          │
│  (high-level API: encrypt, decrypt, negotiate)  │
├─────────────────────────────────────────────────┤
│              DoubleRatchet                      │
│  (symmetric ratcheting, key management)         │
├──────────────┬──────────────┬───────────────────┤
│ ChaCha20Poly │    HKDF      │    Curve25519     │
│    1305      │              │                   │
├──────────────┴──────────────┼───────────────────┤
│   Poly1305  │   ChaCha20    │     SHA256        │
└─────────────┴───────────────┴───────────────────┘
```

## Adding a New Crypto Primitive

### Step 1: Implement in `crypto/`

```java
package com.chatterbox.crypto;

/**
 * MyNewPrimitive - description.
 * Implements RFC XXXX.
 */
public class MyNewPrimitive {

    public static final int KEY_SIZE = 32;
    public static final int OUTPUT_SIZE = 32;

    public static byte[] compute(byte[] input, byte[] key) {
        // Pure Java implementation
        // No external dependencies
        // No java.security usage
    }
}
```

### Step 2: Add RFC test vectors

In `test/src/com/chatterbox/test/CryptoTest.java`:
```java
public void testMyNewPrimitive() {
    // Test vector 1 from RFC XXXX
    byte[] input = hexToBytes("000102...");
    byte[] key = hexToBytes("aabbcc...");
    byte[] expected = hexToBytes("deadbeef...");

    byte[] result = MyNewPrimitive.compute(input, key);
    assertArrayEquals(expected, result);
}
```

### Step 3: Integrate (if needed)

In `core/Session.java` or `core/CommandProcessor.java`:
```java
// Use the new primitive
byte[] derived = MyNewPrimitive.compute(sharedSecret, salt);
```

## E2E Encryption Flow

### Initial Setup

```java
// Generate identity key pair
byte[] privateKey = Curve25519.generatePrivateKey();
byte[] publicKey = Curve25519.derivePublicKey(privateKey);

// Exchange public keys via DMs
E2E.init(peerPublicKey, myPrivateKey);
```

### Encrypting a Message

```java
// Via CommandProcessor (/e2e command)
commandProcessor.execute("/e2e " + peerNick + " " + message);

// Internally:
// 1. DoubleRatchet encrypts message
// 2. ChaCha20Poly1305 applies AEAD
// 3. Result sent as CTCP E2E message
```

### Decrypting a Message

```java
// Automatic in Session
// 1. Receive CTCP E2E message
// 2. DoubleRatchet decrypts
// 3. ChaCha20Poly1305 verifies AEAD
// 4. Plaintext passed to UI
```

## Double Ratchet

The Double Ratchet provides:
- **Forward secrecy** — past messages can't be decrypted if key is compromised
- **Future secrecy** — future messages can't be decrypted if current key is compromised
- **Self-healing** — ratcheting continues even if messages are lost

### Key Concepts

| Term | Description |
|------|-------------|
| Root Key | Master key derived from X25519 |
| Chain Key | Symmetric key for message encryption |
| Message Key | One-time key for single message |
| Ratchet Step | Advance chain key via HKDF |

## Security Rules

- **Never reuse nonce** with ChaCha20-Poly1305
- **Always verify** Poly1305 tag before decryption
- **Zero out** sensitive byte arrays after use
- **No `java.security`** — use only custom crypto classes
- **Constant-time comparison** for MAC verification (in Poly1305)

## Common Operations

### Derive a key with HKDF

```java
byte[] sharedSecret = Curve25519.deriveSharedSecret(privateKey, peerPublicKey);
byte[] rootKey = HKDF.derive(sharedSecret, salt, info, 32);
```

### Encrypt with ChaCha20-Poly1305

```java
byte[] nonce = new byte[12]; // generate random
byte[] plaintext = "Hello".getBytes("UTF-8");
byte[] ciphertext = ChaCha20Poly1305.encrypt(key, nonce, plaintext, associatedData);
```

### Verify Curve25519 output

```java
// Reject non-canonical points (small subgroup attacks)
if (!Curve25519.isCanonical(publicKey)) {
    throw new SecurityException("Non-canonical public key");
}
```

## Anti-Patterns

```java
// WRONG: Reusing nonce (catastrophic for security)
byte[] nonce = new byte[12];
random.nextBytes(nonce);
// ... encrypt multiple messages with same nonce

// RIGHT: New nonce for every encryption
byte[] nonce = new byte[12];
random.nextBytes(nonce);
byte[] ciphertext = ChaCha20Poly1305.encrypt(key, nonce, plaintext, ad);
```

```java
// WRONG: Not verifying MAC before decryption
byte[] plaintext = ChaCha20.decrypt(key, nonce, ciphertext);

// RIGHT: Always verify Poly1305 tag
byte[] plaintext = ChaCha20Poly1305.decrypt(key, nonce, ciphertext, ad);
// Throws if MAC invalid
```

```java
// WRONG: Leaving sensitive data in memory
byte[] privateKey = Curve25519.generatePrivateKey();
// ... use it ...
// Still in memory!

// RIGHT: Zero out after use
byte[] privateKey = Curve25519.generatePrivateKey();
try {
    // ... use it ...
} finally {
    for (int i = 0; i < privateKey.length; i++) {
        privateKey[i] = 0;
    }
}
```

```java
// WRONG: Using java.security (not available on CLDC)
MessageDigest md = MessageDigest.getInstance("SHA-256");

// RIGHT: Use custom implementation
byte[] hash = SHA256.compute(input);
```

```java
// WRONG: Early return on MAC mismatch (timing attack)
for (int i = 0; i < mac1.length; i++) {
    if (mac1[i] != mac2[i]) return false;
}

// RIGHT: Constant-time comparison
int result = 0;
for (int i = 0; i < mac1.length; i++) {
    result |= mac1[i] ^ mac2[i];
}
return result == 0;
```

## Code Template: Adding a New Crypto Primitive

```java
package com.chatterbox.crypto;

/**
 * MyNewPrimitive - description.
 * Implements RFC XXXX.
 */
public class MyNewPrimitive {

    public static final int KEY_SIZE = 32;
    public static final int OUTPUT_SIZE = 32;

    /**
     * Compute the primitive.
     * @param input Input bytes
     * @param key Key bytes (must be KEY_SIZE long)
     * @return Output bytes (OUTPUT_SIZE long)
     */
    public static byte[] compute(byte[] input, byte[] key) {
        if (key == null || key.length != KEY_SIZE) {
            throw new IllegalArgumentException("Key must be " + KEY_SIZE + " bytes");
        }
        if (input == null) {
            throw new IllegalArgumentException("Input cannot be null");
        }

        byte[] output = new byte[OUTPUT_SIZE];
        // ... implementation ...
        return output;
    }

    // Test helper: convert hex string to bytes
    static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
```

## Testing Crypto

- All test vectors from the relevant RFC must pass
- Test edge cases: empty input, maximum length, zero keys
- Test non-canonical Curve25519 inputs
- Test nonce reuse detection (should fail)
- Run `ant test` — crypto tests are fast and deterministic
