---
id: migration
title: Migration
triggers: [migration, breaking, change, upgrade, deprecate, migrate, transition]
packages: []
complexity: medium
---

# Migration Guide

How to handle breaking changes and version transitions.

## When to Use

- Changing RMS schema
- Modifying config format
- Changing API signatures
- Upgrading dependencies
- Transitioning between versions

## Migration Types

### 1. RMS Schema Migration

When changing persistent data format:

```java
// In your data class
public byte[] toBytes() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);
    dos.writeInt(2);  // Version 2
    dos.writeUTF(name);
    dos.writeInt(value);
    // New in v2:
    dos.writeBoolean(extraField);
    return baos.toByteArray();
}

public static MyData fromBytes(byte[] data) {
    DataInputStream dis = new DataInputStream(
        new ByteArrayInputStream(data));
    int version = dis.readInt();
    MyData result = new MyData();
    result.name = dis.readUTF();
    result.value = dis.readInt();
    if (version >= 2) {
        result.extraField = dis.readBoolean();
    } else {
        result.extraField = false;  // Default for old data
    }
    return result;
}
```

### 2. Config Format Migration

When changing configuration options:

```java
// In ProfileStore or config class
public static Profile migrateProfile(byte[] oldData) {
    Profile profile = new Profile();
    DataInputStream dis = new DataInputStream(
        new ByteArrayInputStream(oldData));
    int version = dis.readInt();

    if (version == 1) {
        // Migrate from v1 to v2
        profile.nick = dis.readUTF();
        profile.server = dis.readUTF();
        profile.port = dis.readInt();
        // New in v2:
        profile.useTls = true;  // Default for migrated profiles
    } else if (version == 2) {
        // Current version
        profile.nick = dis.readUTF();
        profile.server = dis.readUTF();
        profile.port = dis.readInt();
        profile.useTls = dis.readBoolean();
    }
    return profile;
}
```

### 3. API Migration

When changing public API:

```java
// OLD API (deprecated)
public void sendMessage(String target, String message) {
    send("PRIVMSG " + target + " :" + message);
}

// NEW API
public void sendMessage(Message message) {
    send(message.toIrcLine());
}

// DEPRECATION BRIDGE
public void sendMessage(String target, String message) {
    sendMessage(new Message(target, message));
}
```

## Migration Checklist

- [ ] Identify all usages of old format/API
- [ ] Write migration code
- [ ] Add version field to serialized data
- [ ] Test migration with old data
- [ ] Update documentation
- [ ] Add deprecation notice
- [ ] Remove old code in next major version

## Migration Decision Tree

```
Breaking change needed?
├── Can it be backward compatible?
│   ├── YES → Add new method, deprecate old
│   └── NO → Major version bump
├── Does it affect persistent data?
│   ├── YES → Write migration code
│   └── NO → Just update code
├── Does it affect config files?
│   ├── YES → Write config migration
│   └── NO → Just update code
└── Does it affect API?
    ├── YES → Deprecate old, add new
    └── NO → Just update code
```

## Version Compatibility

| Old Version | New Version | Migration Path |
|-------------|-------------|----------------|
| 1.0.x | 1.1.0 | Auto-migrate RMS |
| 1.1.x | 1.2.0 | Auto-migrate RMS |
| 1.x.x | 2.0.0 | Manual migration required |

## Anti-Patterns

```java
// WRONG: Breaking change without migration
// Old code:
public void save(String key, String value) { ... }

// New code (breaking):
public void save(String key, byte[] value) { ... }

// RIGHT: Backward compatible
public void save(String key, String value) {
    save(key, value.getBytes());
}

public void save(String key, byte[] value) {
    // New implementation
}
```

```java
// WRONG: No version field in serialized data
public byte[] toBytes() {
    // Can't tell old from new format!
}

// RIGHT: Always include version
public byte[] toBytes() {
    dos.writeInt(VERSION);  // Always first
    // ... rest of data
}
```

## Migration Template

```java
/**
 * Migrate data from old version to current.
 * @param oldData Data in old format
 * @return Data in current format
 */
public static byte[] migrate(byte[] oldData) {
    DataInputStream dis = new DataInputStream(
        new ByteArrayInputStream(oldData));
    int version = dis.readInt();

    if (version < CURRENT_VERSION) {
        // Migrate step by step
        while (version < CURRENT_VERSION) {
            oldData = migrateStep(oldData, version);
            version++;
        }
    }
    return oldData;
}

private static byte[] migrateStep(byte[] data, int fromVersion) {
    if (fromVersion == 1) {
        return migrateV1ToV2(data);
    }
    // ...
}
```
