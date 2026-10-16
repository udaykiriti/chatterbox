---
id: rms
title: RMS
triggers: [rms, recordstore, storage, persist, profile, log, save, load, migrate]
packages: [rms]
complexity: medium
---

# RMS Skill

Work with persistent storage using the Record Management System (RMS).

## When to Use

- Adding new persistent data
- Modifying storage schema
- Debugging data persistence issues
- Understanding the RMS architecture
- Working with profiles, logs, or settings

## Package Overview

All RMS classes are in `midlet/src/com/chatterbox/rms/`.

| Class | Responsibility |
|-------|---------------|
| `RmsStore` | Low-level RMS wrapper (open, read, write, close) |
| `ProfileStore` | IRC server profile storage (nick, server, port, etc.) |
| `Profile` | Data class for a server profile |
| `LogStore` | Chat log persistence |
| `PasswordObfuscator` | Password obfuscation before storage |

## RMS Basics

RMS (RecordStore) is the only persistent storage on Java ME devices:
- **RecordStore** = a named collection of records
- **Record** = a byte array with a numeric ID
- **No SQL** — just byte arrays
- **Limited space** — typically 64KB–512KB per RecordStore
- **Persistent** — survives app restart

## Architecture

```
┌─────────────────────────────────────────┐
│           ProfileStore                  │
│  (high-level: save/load profiles)       │
├─────────────────────────────────────────┤
│            LogStore                     │
│  (high-level: save/load chat logs)      │
├─────────────────────────────────────────┤
│            RmsStore                     │
│  (low-level: open, read, write, close)  │
├─────────────────────────────────────────┤
│      javax.microedition.rms             │
│  (MIDP RMS API)                         │
└─────────────────────────────────────────┘
```

## Adding Persistent Data

### Step 1: Define the data class

```java
package com.chatterbox.rms;

public class MyData {
    public String name;
    public int value;

    // Serialize to byte array
    public byte[] toBytes() {
        // Manual serialization (no ObjectOutputStream on CLDC)
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeUTF(name);
        dos.writeInt(value);
        return baos.toByteArray();
    }

    // Deserialize from byte array
    public static MyData fromBytes(byte[] data) {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);
        MyData result = new MyData();
        result.name = dis.readUTF();
        result.value = dis.readInt();
        return result;
    }
}
```

### Step 2: Create a store class

```java
package com.chatterbox.rms;

public class MyDataStore {
    private static final String STORE_NAME = "MyData";
    private RmsStore store;

    public MyDataStore() {
        store = new RmsStore(STORE_NAME);
    }

    public void save(MyData data) {
        byte[] bytes = data.toBytes();
        store.write(1, bytes);  // Record ID 1
    }

    public MyData load() {
        byte[] bytes = store.read(1);
        if (bytes == null) return null;
        return MyData.fromBytes(bytes);
    }

    public void close() {
        store.close();
    }
}
```

### Step 3: Use in your code

```java
MyDataStore myStore = new MyDataStore();

// Save
MyData data = new MyData();
data.name = "test";
data.value = 42;
myStore.save(data);

// Load
MyData loaded = myStore.load();

// Close when done
myStore.close();
```

## Profile Storage

`ProfileStore` manages IRC server profiles:

```java
// Save a profile
Profile profile = new Profile();
profile.nick = "mynick";
profile.server = "irc.libera.chat";
profile.port = 6697;
profile.useTls = true;
ProfileStore.save(profile);

// Load profiles
Profile[] profiles = ProfileStore.loadAll();

// Delete a profile
ProfileStore.delete(profile.id);
```

## Log Storage

`LogStore` persists chat logs:

```java
// Append a log entry
LogStore.append("#channel", "nick", "Hello, world!");

// Read logs for a channel
String[] logs = LogStore.read("#channel", 100);  // Last 100 lines

// Clear logs
LogStore.clear("#channel");
```

## Password Obfuscation

**Never store passwords in plain text.** Use `PasswordObfuscator`:

```java
// Before storing
String obfuscated = PasswordObfuscator.obfuscate(password);

// When using
String password = PasswordObfuscator.deobfuscate(obfuscated);
```

Note: This is obfuscation, not encryption. It prevents casual inspection but is not cryptographically secure.

## RMS Constraints

| Constraint | Details |
|-----------|---------|
| **RecordStore size** | Typically 64KB–512KB per store |
| **Record size** | Limited by available heap |
| **No concurrent access** | One RecordStore open at a time per name |
| **No transactions** | Write is immediate |
| **No indexing** | Linear scan by record ID |
| **Serialization** | Manual byte array conversion (no `java.io.Serializable`) |

## Common Patterns

### Singleton Store

```java
public class MyStore {
    private static MyStore instance;
    private RmsStore store;

    private MyStore() {
        store = new RmsStore("MyStore");
    }

    public static MyStore getInstance() {
        if (instance == null) {
            instance = new MyStore();
        }
        return instance;
    }
}
```

### Migration

When changing data format, include a version field:

```java
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
    }
    return result;
}
```

## Anti-Patterns

```java
// WRONG: Storing password in plain text
ProfileStore.save("password", password);

// RIGHT: Obfuscate first
ProfileStore.save("password", PasswordObfuscator.obfuscate(password));
```

```java
// WRONG: Not closing RecordStore (resource leak)
RmsStore store = new RmsStore("MyStore");
store.write(1, data);
// Never closed!

// RIGHT: Always close
RmsStore store = new RmsStore("MyStore");
try {
    store.write(1, data);
} finally {
    store.close();
}
```

```java
// WRONG: Using ObjectOutputStream (not on CLDC)
ObjectOutputStream oos = new ObjectOutputStream(baos);
oos.writeObject(myData);

// RIGHT: Manual serialization
DataOutputStream dos = new DataOutputStream(baos);
dos.writeUTF(myData.name);
dos.writeInt(myData.value);
```

```java
// WRONG: Not handling RecordStore full
store.write(largeData);  // May throw RecordStoreException

// RIGHT: Handle gracefully
try {
    store.write(largeData);
} catch (RecordStoreException e) {
    // Delete old records or notify user
    store.delete(1);
    store.write(1, largeData);
}
```

```java
// WRONG: Assuming record exists
byte[] data = store.read(1);  // May return null!

// RIGHT: Check for null
byte[] data = store.read(1);
if (data == null) {
    data = getDefaultData();
}
```

## Code Template: Adding a New RMS Store

```java
package com.chatterbox.rms;

import java.io.*;

/**
 * MyDataStore - persists MyData objects to RMS.
 */
public class MyDataStore {
    private static final String STORE_NAME = "MyDataStore";
    private static final int RECORD_ID = 1;
    private RmsStore store;

    public MyDataStore() {
        store = new RmsStore(STORE_NAME);
    }

    /**
     * Save data to RMS.
     */
    public void save(MyData data) throws RecordStoreException {
        byte[] bytes = data.toBytes();
        store.write(RECORD_ID, bytes);
    }

    /**
     * Load data from RMS.
     * @return Data or null if not found
     */
    public MyData load() throws RecordStoreException {
        byte[] bytes = store.read(RECORD_ID);
        if (bytes == null) return null;
        return MyData.fromBytes(bytes);
    }

    /**
     * Close the store.
     */
    public void close() {
        store.close();
    }
}

/**
 * Data class with serialization.
 */
class MyData {
    public String name;
    public int value;

    public byte[] toBytes() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeUTF(name);
        dos.writeInt(value);
        return baos.toByteArray();
    }

    public static MyData fromBytes(byte[] data) {
        DataInputStream dis = new DataInputStream(
            new ByteArrayInputStream(data));
        MyData result = new MyData();
        result.name = dis.readUTF();
        result.value = dis.readInt();
        return result;
    }
}
```

## Testing RMS

- RMS classes compile against stubs but **cannot run on desktop**
- Test on emulator or physical device
- Use `RmsStore` stub for unit testing logic
- Always test migration paths when changing schema

## Troubleshooting

### RecordStore full
- Delete old records: `store.deleteRecord(id)`
- Compact by rewriting all records
- Reduce data size (shorter strings, fewer fields)

### RecordStore not found
- Check store name is valid (max 32 chars, no special chars)
- Ensure `RecordStore.openRecordStore(name, true)` creates if missing

### Data corruption
- Always include version field in serialized data
- Validate data after reading
- Handle `RecordStoreException` gracefully
