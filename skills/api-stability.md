---
id: api-stability
title: API Stability
triggers: [api, stability, contract, public, private, frozen, stable, breaking]
packages: []
complexity: medium
---

# API Stability Contract

What can change vs what's frozen between versions.

## When to Use

- Planning changes to public APIs
- Reviewing PRs for breaking changes
- Deciding version bumps
- Communicating changes to users

## Stability Levels

### Level 1: Frozen (Never Changes)

These APIs are guaranteed stable across all versions:

| API | Location | Guarantee |
|-----|----------|-----------|
| `IrcParser.parse()` | `proto/IrcParser.java` | Signature frozen |
| `IrcLine` fields | `proto/IrcLine.java` | Structure frozen |
| `Numeric` constants | `proto/Numeric.java` | Values frozen |
| `IrcSanitizer.sanitize()` | `proto/IrcSanitizer.java` | Signature frozen |
| `SHA256.compute()` | `crypto/SHA256.java` | Signature frozen |
| `ChaCha20Poly1305.encrypt()` | `crypto/ChaCha20Poly1305.java` | Signature frozen |
| `Curve25519.generatePrivateKey()` | `crypto/Curve25519.java` | Signature frozen |

### Level 2: Stable (Backward Compatible)

These APIs can add new methods but not remove or change existing:

| API | Location | Policy |
|-----|----------|--------|
| `Session` public methods | `core/Session.java` | Additive only |
| `CommandProcessor.execute()` | `core/CommandProcessor.java` | Signature frozen |
| `ProfileStore` methods | `rms/ProfileStore.java` | Additive only |
| `E2E` public methods | `crypto/E2E.java` | Additive only |
| `IrcEventListener` | `core/IrcEventListener.java` | Additive only |

### Level 3: Internal (Can Change)

These APIs can change between versions:

| API | Location | Policy |
|-----|----------|--------|
| All package-private classes | `*/` | Can change |
| `ui/` internal classes | `ui/` | Can change |
| `net/` internal classes | `net/` | Can change |
| `rms/` internal classes | `rms/` | Can change |
| Gateway internal functions | `gateway/server.py` | Can change |

## Breaking Changes

### What Constitutes a Breaking Change

| Change | Level | Version Bump |
|--------|-------|--------------|
| Remove public method | 1 or 2 | Major |
| Change method signature | 1 or 2 | Major |
| Change return type | 1 or 2 | Major |
| Add required parameter | 1 or 2 | Major |
| Remove class | 1 or 2 | Major |
| Change RMS format | 1 | Major |
| Add optional parameter | 2 | Minor |
| Add new method | 2 | Minor |
| Deprecate method | 2 | Minor |
| Change internal API | 3 | Patch |
| Fix bug | Any | Patch |

### Examples

```java
// BREAKING: Remove method
// Before:
public void sendMessage(String target, String message) { ... }
// After: Method removed!

// BREAKING: Change signature
// Before:
public void connect(String host, int port) { ... }
// After:
public void connect(String host, int port, boolean tls) { ... }

// NON-BREAKING: Add method
// Before:
public void connect(String host, int port) { ... }
// After:
public void connect(String host, int port) { ... }
public void connect(String host, int port, boolean tls) { ... }  // New overload
```

## RMS Record Format

### Frozen Formats

| Record | Format | Version |
|--------|--------|---------|
| Profile | `version(4) + nick + server + port + useTls + ...` | 2 |
| Log | `version(4) + channel + nick + text + timestamp` | 1 |

### Migration Path

When changing RMS format:
1. Increment version number
2. Write migration code in `fromBytes()`
3. Test with old data
4. Document in CHANGELOG

## Config File Format

### Frozen Format

| File | Format | Version |
|------|--------|---------|
| `chatterbox.properties` | Key-value pairs | 1 |

### Adding New Config Options

```properties
# Old format
nick=testnick
server=irc.libera.chat

# New format (backward compatible)
nick=testnick
server=irc.libera.chat
useTls=true  # New option with default
```

## Deprecation Process

### Step 1: Deprecate

```java
/**
 * @deprecated Use {@link #newMethod()} instead.
 * Will be removed in version 2.0.0.
 */
public void oldMethod() {
    newMethod();  // Delegate to new
}
```

### Step 2: Document

```markdown
## Deprecation Notice

The following APIs are deprecated and will be removed in v2.0.0:

| Old API | New API | Removal Version |
|---------|---------|-----------------|
| `oldMethod()` | `newMethod()` | 2.0.0 |
```

### Step 3: Remove (Major Version)

```java
// In major version bump, remove deprecated method
// public void oldMethod() { ... }  // REMOVED
```

## API Stability Checklist

- [ ] New API added to correct stability level
- [ ] Breaking changes documented
- [ ] Version bump appropriate
- [ ] Migration path provided
- [ ] Tests updated
- [ ] Documentation updated
- [ ] CHANGELOG updated

## Anti-Patterns

```java
// WRONG: Changing frozen API
// Before:
public static final int RPL_TOPIC = 332;
// After:
public static final int RPL_TOPIC = 333;  // BREAKING!

// RIGHT: Never change constants
public static final int RPL_TOPIC = 332;  // Frozen
```

```java
// WRONG: Removing method without deprecation
// Before:
public void oldMethod() { ... }
// After: Method removed!

// RIGHT: Deprecate first
/** @deprecated Use newMethod() */
public void oldMethod() { ... }
// Remove in next major version
```
