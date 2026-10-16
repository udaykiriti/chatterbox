---
id: dependency-audit
title: Dependency Audit
triggers: [dependency, deps, external, library, import, require, package]
packages: []
complexity: easy
---

# Dependency Audit Skill

Tracking and managing external dependencies.

## When to Use

- Adding new imports
- Reviewing dependencies
- Auditing for bloat
- Checking for security issues
- Preparing for release

## Current Dependencies

### Runtime Dependencies

**NONE** — The project has zero external runtime dependencies.

| Component | Implementation |
|-----------|---------------|
| IRC Protocol | Custom (`proto/`) |
| Crypto | Custom (`crypto/`) |
| UI | LCDUI (MIDP) |
| Storage | RMS (MIDP) |
| Networking | SocketConnection (MIDP) |

### Build Dependencies

| Tool | Version | Purpose |
|------|---------|---------|
| Apache Ant | 1.10+ | Build automation |
| ProGuard | 7.x+ | Obfuscation |
| Sun WTK | 2.5.2 | Preverification |
| JDK | 8–21 | Compilation |

### Documentation Dependencies

| Tool | Version | Purpose |
|------|---------|---------|
| MkDocs | Latest | Doc generation |
| Material Theme | Latest | Doc styling |
| Python | 3.11+ | Doc build |

### Python Gateway Dependencies

**NONE** — Uses only Python standard library.

| Module | Purpose |
|--------|---------|
| `asyncio` | Async I/O |
| `ssl` | TLS |
| `hmac` | HMAC |
| `hashlib` | Hashing |
| `logging` | Logging |

## Dependency Audit Checklist

- [ ] No new external dependencies added
- [ ] All imports are from `javax.microedition.*` or `java.*`
- [ ] No `java.util.*` collections (use arrays/Vector)
- [ ] No `java.security.*` (use custom crypto)
- [ ] No third-party libraries
- [ ] JAR size under 300 KB
- [ ] All dependencies documented

## Adding a New Dependency

### Decision Tree

```
Need new functionality?
├── Can it be implemented with existing code?
│   ├── YES → Implement it
│   └── NO → Continue
├── Is it available in MIDP/CLDC?
│   ├── YES → Use it
│   └── NO → Continue
├── Can it be stubbed?
│   ├── YES → Create stub
│   └── NO → Continue
├── Is it small enough to vendor?
│   ├── YES → Vendor with attribution
│   └── NO → Reconsider
└── Is it absolutely necessary?
    ├── YES → Document and justify
    └── NO → Don't add
```

### If You Must Add a Dependency

1. **Justify** — Why is it needed?
2. **Minimize** — Can you vendor just the needed parts?
3. **Document** — Add to this file
4. **Test** — Ensure it works on device
5. **Attribute** — Include license and attribution

## Import Audit

### Allowed Imports

```java
// Java ME (MIDP/CLDC)
import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import javax.microedition.io.*;
import javax.microedition.rms.*;
import javax.media.*;

// Java standard (CLDC subset)
import java.io.*;
import java.util.Vector;  // Only Vector from java.util
import java.util.Timer;
import java.util.TimerTask;
```

### Forbidden Imports

```java
// WRONG: java.util collections (except Vector)
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

// WRONG: java.security
import java.security.*;
import java.security.MessageDigest;

// WRONG: Third-party libraries
import org.apache.commons.*;
import com.google.gson.*;
```

## Dependency Audit Template

```markdown
## Dependency Audit — [Date]

### Runtime Dependencies
- None

### Build Dependencies
- Apache Ant 1.10+
- ProGuard 7.x+
- Sun WTK 2.5.2

### New Dependencies Added
- None

### Removed Dependencies
- None

### JAR Size
- Current: XXX KB
- Target: < 300 KB
- Status: ✅ Within target

### Issues
- None
```

## Anti-Patterns

```java
// WRONG: Adding dependency for one method
// Added entire Apache Commons for StringUtils.isEmpty()

// RIGHT: Implement it yourself
public static boolean isEmpty(String s) {
    return s == null || s.length() == 0;
}
```

```java
// WRONG: Not checking license compatibility
// Added GPL library to MIT project

// RIGHT: Check license compatibility
// Use MIT/Apache/BSD licensed libraries only
```
