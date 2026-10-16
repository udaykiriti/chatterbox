---
id: stub
title: Stub
triggers: [stub, mock, desktop, compile, shim, emulate]
packages: [stub, test]
complexity: easy
---

# Stub Skill

Work with the MIDP/CLDC API stubs used for desktop compilation and testing.

## When to Use

- Adding new stub classes for testing
- Modifying existing stub behavior
- Understanding the stub architecture
- Debugging desktop compilation issues
- Running tests without a WTK/emulator

## Overview

The `stub/` directory contains minimal implementations of the MIDP/CLDC APIs, enabling the project to compile and run tests on a desktop JDK without a real WTK or device SDK.

```
stub/
├── javax/microedition/midlet/    # MIDlet lifecycle stubs
│   ├── MIDlet.java
│   ├── MIDletStateChangeException.java
│   └── Display.java
├── javax/microedition/lcdui/     # UI stubs
│   ├── Command.java
│   ├── CommandListener.java
│   ├── Displayable.java
│   ├── Form.java
│   ├── List.java
│   ├── TextField.java
│   ├── Alert.java
│   ├── AlertType.java
│   ├── Canvas.java
│   ├── Graphics.java
│   └── Font.java
├── javax/microedition/io/        # I/O stubs
│   ├── SocketConnection.java
│   ├── Connection.java
│   ├── Connector.java
│   └── ConnectionNotFoundException.java
├── javax/microedition/rms/       # RecordStore stubs
│   ├── RecordStore.java
│   ├── RecordStoreException.java
│   └── RecordEnumeration.java
└── javax/media/                  # Media stubs
    └── Manager.java
```

## How Stubs Work

Stubs provide **compile-time compatibility** but no runtime behavior:

```java
// Real MIDP:
Display.getDisplay(midlet).setCurrent(screen);

// Stub version:
public static Display getDisplay(MIDlet m) {
    return new Display();  // No-op, allows compilation
}
```

## Adding a New Stub

### Step 1: Determine the package

| API | Package |
|-----|---------|
| MIDlet lifecycle | `javax.microedition.midlet` |
| LCDUI (screens, canvas) | `javax.microedition.lcdui` |
| Networking | `javax.microedition.io` |
| RMS (storage) | `javax.microedition.rms` |
| Media | `javax.media` |

### Step 2: Create the stub class

```java
package javax.microedition.lcdui;

/**
 * Stub implementation of Alert for desktop compilation.
 * Does not display anything on desktop.
 */
public class Alert extends Displayable {

    public static final int FOREVER = -1;

    public Alert(String title) {
        this(title, null, null, null);
    }

    public Alert(String title, String text, AlertType type, Image img) {
        // No-op stub
    }

    public void setTimeout(int time) {
        // No-op stub
    }

    public void addCommand(Command cmd) {
        // No-op stub
    }
}
```

### Step 3: Compile and verify

```bash
# Compile stubs
ant stubs

# Compile midlet against stubs
ant compile

# Run tests
ant test
```

## Stub Constraints

- **Compile-only**: Stubs must compile but don't need to function
- **Minimal implementation**: Just enough to satisfy the compiler
- **No external dependencies**: Stubs must be self-contained
- **Match real API signatures**: Use the same method signatures as the real MIDP API
- **No business logic**: Stubs should not implement real behavior

## Testing with Stubs

The test suite uses stubs to run on desktop:

```bash
# Compile stubs first
ant stubs

# Compile midlet against stubs
ant compile

# Compile tests
javac -encoding UTF-8 -cp build/classes -d build/tests \
  $(find test/shim test/src -name '*.java')

# Run tests
java -cp build/classes:build/tests com.chatterbox.test.TestMain
```

## Common Stub Patterns

### Collection stub

```java
package javax.microedition.lcdui;

public class Form extends Displayable {
    private Vector items = new Vector();  // CLDC has Vector

    public void append(Item item) {
        items.addElement(item);
    }

    public void append(String text) {
        items.addElement(text);
    }

    public int size() {
        return items.size();
    }
}
```

### Exception stub

```java
package javax.microedition.rms;

public class RecordStoreException extends Exception {
    public RecordStoreException() {
        super();
    }

    public RecordStoreException(String message) {
        super(message);
    }
}
```

### Factory stub

```java
package javax.microedition.io;

public class Connector {
    public static final int READ = 1;
    public static final int WRITE = 2;
    public static final int READ_WRITE = 3;

    public static Connection open(String url) throws IOException {
        throw new ConnectionNotFoundException("Stub: cannot open " + url);
    }
}
```

## Debugging Stub Issues

### Compile error: "cannot find symbol"
- Check the stub class exists in the right package
- Verify the method signature matches the real API
- Ensure stubs are compiled before midlet code

### Runtime error: "Stub does nothing"
- This is expected — stubs are compile-only
- Use a real WTK or emulator for runtime testing
- Or add logging to the stub for debugging

### Inconsistent behavior
- Stubs may not match real device behavior exactly
- Always test on real device or emulator before release
- Document known stub limitations

## Anti-Patterns

```java
// WRONG: Stub implements real behavior (wastes time, may hide bugs)
public static void setCurrent(Displayable d) {
    // Actually renders the displayable?? No!
}

// RIGHT: Stub is a no-op
public static void setCurrent(Displayable d) {
    // No-op for desktop compilation
}
```

```java
// WRONG: Stub throws exception for everything
public static void setCurrent(Displayable d) {
    throw new UnsupportedOperationException("Stub!");
}

// RIGHT: Stub silently succeeds (allows tests to run)
public static void setCurrent(Displayable d) {
    // No-op
}
```

## Code Template: Adding a New Stub

```java
package javax.microedition.lcdui;

/**
 * Stub implementation of MyClass for desktop compilation.
 * Does not implement real behavior.
 */
public class MyClass {

    public MyClass() {
        // No-op stub
    }

    public void doSomething() {
        // No-op stub
    }

    public String getSomething() {
        return "";  // Return default value
    }
}
```

## Adding Stub for New MIDP API

When using a new MIDP API not yet stubbed:

1. Check if the API is available in your JDK (some overlap)
2. If not, create a stub in the appropriate package
3. Match the real MIDP API signature
4. Make it a no-op or throw `UnsupportedOperationException`
5. Compile and test
6. Document the stub in this file
