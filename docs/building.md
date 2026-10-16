# Building Chatterbox from Source

Complete build instructions for compiling, testing, and packaging Chatterbox.

---

## ️ Build Requirements

### Required Tools

| Tool | Version | Purpose | Install Hint |
|------|---------|---------|--------------|
| **JDK** | 8–21 | Desktop compilation & unit tests | `apt install openjdk-17-jdk` / `brew install openjdk@17` |
| **Apache Ant** | 1.10+ | Build orchestration | `apt install ant` / `brew install ant` |
| **Sun WTK 2.5.2** | 2.5.2 | `preverify`, MIDP/CLDC APIs, emulator | [Download](https://web.archive.org/web/20100115071457/http://java.sun.com/products/sjwtoolkit/download-2_5_2.html) |
| **ProGuard** | 7.x+ | Shrink/optimize/obfuscate | [ProGuard releases](https://sourceforge.net/projects/proguard/files/proguard/) |

### Environment Variables

Set these in your shell profile (`~/.bashrc`, `~/.zshrc`, etc.):

```bash
# Required for device builds (preverify + emulator)
export WTK_HOME=/opt/WTK2.5.2

# Optional: ProGuard for release builds
export PROGUARD_HOME=/opt/proguard7.4

# Optional: Signing (for real device installs)
export KEYSTORE=/path/to/my.keystore
export KEY_ALIAS=myalias
export KEYSTORE_PASS=storepass
export KEY_PASS=keypass
```

> **Note:** WTK 2.5.2 is the last version with `preverify` and the MIDP 2.0/CLDC 1.1 APIs. Newer Oracle Java ME SDKs changed the structure. If WTK is unavailable, the Nokia S60 3rd Ed. SDK also works.

---

## Ant Targets

Run `ant -p` to see all targets:

```bash
Buildfile: /home/uday/chatterbox/build.xml

Main targets:

 clean              Delete all build artifacts
 compile            Compile Java sources to build/
 compile-stubs      Compile MIDP/CLDC stub classes
 compile-tests      Compile unit tests
 preverify          Preverify classes for MIDP (requires WTK)
 proguard           Run ProGuard optimization (requires ProGuard)
 jar                Create JAR from preverified classes
 jad                Create JAD descriptor
 dist               Full release build (clean → compile → preverify → ProGuard → JAR + JAD)
 sign               Sign JAR with keystore (requires env vars)
 install-emu        Install to WTK default emulator
 test               Run unit tests on desktop JDK
 test-report        Generate HTML test report

Default target: dist
```

---

## ️ Step-by-Step Build

### 1. Quick Test Build (No WTK/ProGuard Needed)

```bash
# Compile and run unit tests on desktop JDK
ant test
```

Expected output:
```
test:
    [junit] Running com.chatterbox.test.ProtocolParserTest
    [junit] Tests run: 42, Failures: 0, Errors: 0, Time elapsed: 0.3 sec
    [junit] Running com.chatterbox.test.CoreTest
    [junit] Tests run: 31, Failures: 0, Errors: 0, Time elapsed: 0.2 sec
    [junit] Running com.chatterbox.test.SessionTest
    [junit] Tests run: 83, Failures: 0, Errors: 0, Time elapsed: 0.4 sec
    [junit] Tests run: 233, Failures: 0, Errors: 0
```

### 2. Full Release Build (Requires WTK)

```bash
# Ensure WTK_HOME is set
echo $WTK_HOME
# /opt/WTK2.5.2

# Full build
ant dist
```

Build stages:
```
clean
compile-stubs      # Compile MIDP/CLDC stubs for desktop compilation
compile            # Compile midlet sources
compile-tests      # Compile test sources
test               # Run unit tests
preverify          # Preverify for MIDP (WTK required)
proguard           # Shrink/optimize (ProGuard optional)
jar                # Create chatterbox.jar
jad                # Create chatterbox.jad
dist               # Done!
```

### 3. Output Verification

```bash
ls -lh dist/
# chatterbox.jar    ~250-280 KB (with ProGuard)
# chatterbox.jad    ~2 KB
```

If JAR > 300 KB, ProGuard didn't run. Check `PROGUARD_HOME` and build output for warnings.

### 4. Sign for Real Devices (Optional)

```bash
# Requires KEYSTORE, KEY_ALIAS, KEYSTORE_PASS, KEY_PASS env vars
ant sign

# Output: dist/chatterbox-signed.jar + chatterbox-signed.jad
```

### 5. Install to Emulator

```bash
# Requires WTK_HOME
ant install-emu

# Then launch WTK emulator: $WTK_HOME/bin/emulator
```

---

## Build Configuration

### `build.xml` Key Properties

```xml
<!-- Source/target compatibility -->
<property name="javac.source" value="1.5"/>
<property name="javac.target" value="1.5"/>

<!-- WTK paths (auto-detected from WTK_HOME) -->
<property name="wtk.lib" value="${env.WTK_HOME}/lib"/>
<property name="midp.api" value="${wtk.lib}/midpapi20.jar"/>
<property name="cldc.api" value="${wtk.lib}/cldcapi11.jar"/>

<!-- ProGuard -->
<property name="proguard.jar" value="${env.PROGUARD_HOME}/lib/proguard.jar"/>

<!-- Output -->
<property name="dist.dir" value="dist"/>
<property name="jar.name" value="chatterbox.jar"/>
<property name="jad.name" value="chatterbox.jad"/>
```

### Customizing Build

Create `local.properties` (not in git) to override:

```properties
# local.properties
WTK_HOME=/custom/path/to/WTK2.5.2
PROGUARD_HOME=/custom/path/to/proguard
javac.debug=on
proguard.optimize=off
```

---

## Running Tests

### Desktop Unit Tests

```bash
# All tests
ant test

# Specific test class
ant test -Dtest.class=com.chatterbox.test.ProtocolParserTest

# With verbose output
ant test -Dtest.verbose=true
```

### Test Coverage

| Test Class | Tests | Coverage |
|------------|-------|----------|
| `ProtocolParserTest` | 32 | RFC 2812 parsing, numerics, CTCP |
| `CoreTest` | 31 | Commands, string utils, IRC case mapping |
| `SessionTest` | 83 | Ring buffer, nick list, highlights, scrollback |
| `CryptoTest` | 21 | HKDF, SHA-256, RFC 8439 vectors (ChaCha20, Poly1305, AEAD) |
| `X25519Test` | 9 | RFC 7748 vectors |
| `SecurityTest` | 29 | Sanitizer, password obfuscation |
| `E2ETest` | 24 | Pre-shared-key E2E codec + integration |

Total: Total: Total: **233 tests**, all must pass.

### Test Shim

Tests run on desktop JDK using a minimal JUnit 3 shim in `test/shim/`:
- `TestCase.java` — base test class
- `TestRunner.java` — text-based runner
- `Assert.java` — assertions

No external JUnit JAR needed.

---

## Emulator Testing

### WTK Default Emulator

```bash
ant install-emu
$WTK_HOME/bin/emulator
```

### Nokia S60 SDK Emulator (Better Device Fidelity)

```bash
# After ant dist, manually install:
# 1. Start S60 SDK emulator
# 2. Tools → Install → Select dist/chatterbox.jad
# 3. Run "Chatterbox" from Applications menu
```

### Emulator Networking

- **WTK:** Uses host machine's network directly
- **S60 SDK:** May need "Loopback" or "Ethernet" networking mode in SDK preferences

---

## Debug Builds

### Enable Debug Symbols

```bash
ant clean compile -Djavac.debug=on
```

### Skip ProGuard (Larger JAR, Readable Stack Traces)

```bash
ant clean compile preverify jar jad -Dproguard.skip=true
```

Output: `dist/chatterbox.jar` (~500-600 KB)

### Remote Debugging (WTK)

```bash
# Start emulator with JDWP
$WTK_HOME/bin/emulator -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=8000

# Attach from IDE (IntelliJ/Eclipse/VS Code) to localhost:8000
```

---

## Release Checklist

Before releasing:

- [ ] `ant clean dist` completes without warnings
- [ ] `ant test` → 233 tests pass
- [ ] `dist/chatterbox.jar` < 300 KB
- [ ] `dist/chatterbox.jad` has correct `MIDlet-Jar-Size` and `MIDlet-Jar-URL`
- [ ] Tested on WTK emulator: connects, joins, chats
- [ ] Tested on S60 emulator (if available)
- [ ] Signed JAR installs on real device (if cert available)

---

## CI/CD Example (GitHub Actions)

```yaml
# .github/workflows/build.yml
name: Build

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
      
      - name: Install Ant
        run: sudo apt-get update && sudo apt-get install -y ant
      
      - name: Run tests
        run: ant test
      
      - name: Build (no WTK - compile only)
        run: ant clean compile compile-tests
```

> **Note:** Full device build (preverify + ProGuard) requires WTK which isn't easily available in CI. The desktop compile + test validates code correctness.

---

## Troubleshooting Build Issues

| Error | Cause | Fix |
|-------|-------|-----|
| `WTK_HOME not set` | Env var missing | `export WTK_HOME=/path/to/WTK2.5.2` |
| `preverify not found` | WTK path wrong | Check `$WTK_HOME/bin/preverify` exists |
| `midpapi20.jar not found` | WTK version mismatch | Use WTK 2.5.2 exactly |
| `ProGuard: Can't find referenced class` | Missing library | Ensure WTK libs in classpath |
| `JAR > 300 KB` | ProGuard skipped | Check `PROGUARD_HOME`, look for "proguard" in build output |
| `Sign failed` | Keystore env vars missing | Set all 4: KEYSTORE, KEY_ALIAS, KEYSTORE_PASS, KEY_PASS |

---

## Related Docs

- [Getting Started](getting-started.md) — Quick start for users
- [Installation](installation.md) — Device installation methods
- [Development](development.md) — Code structure, debugging, contributing
- [Architecture](architecture.md) — Module breakdown and design patterns