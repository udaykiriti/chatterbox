# Development Guide

Guide for developers: building, testing, debugging, and contributing to Chatterbox.

---

## Development Environment Setup

### Prerequisites (Same as Build)
- JDK 8–21
- Apache Ant 1.10+
- Sun WTK 2.5.2 (for device builds)
- ProGuard 7.x+ (optional)

### IDE Setup

#### IntelliJ IDEA
1. **Open project** → Select `build.xml` → "Open as Project"
2. **Project Structure → Modules** → Mark `midlet/src` as Sources, `test/src` as Tests
3. **SDK:** Add WTK libraries as module dependency:
   - `$WTK_HOME/lib/midpapi20.jar`
   - `$WTK_HOME/lib/cldcapi11.jar`
4. **Run Configuration:** Application → Main class: `com.chatterbox.test.TestMain` (for tests)

#### VS Code
```json
// .vscode/settings.json
{
    "java.project.referencedLibraries": [
        "stub/**/*.jar",
        "${env.WTK_HOME}/lib/midpapi20.jar",
        "${env.WTK_HOME}/lib/cldcapi11.jar"
    ],
    "java.configuration.runtimes": [
        { "name": "JavaSE-1.8", "path": "/usr/lib/jvm/java-8-openjdk" }
    ]
}
```

#### Eclipse
1. File → Import → Existing Ant Buildfile → Select `build.xml`
2. Right-click project → Properties → Java Build Path → Add External JARs (WTK libs)
3. Run → External Tools → Ant Build → Select targets

---

## Testing

### Run All Tests (Desktop)
```bash
ant test
# Output: 238 tests, 0 failures
```

### Run Single Test Class
```bash
ant test -Dtest.class=com.chatterbox.test.ProtocolParserTest
```

### Run Single Test Method
```bash
ant test -Dtest.class=com.chatterbox.test.ProtocolParserTest -Dtest.method=testPrivmsgParsing
```

### Test Output Format
```
test:
    [junit] Running com.chatterbox.test.ProtocolParserTest
    [junit] testPrivmsgParsing: PASSED
    [junit] testCtcpAction: PASSED
    [junit] ...
    [junit] Tests run: 42, Failures: 0, Errors: 0, Time elapsed: 0.3 sec
```

### Writing Tests

#### Test Structure
```java
// test/src/com/chatterbox/test/ProtocolParserTest.java
public class ProtocolParserTest extends TestCase {

    public void testPrivmsgParsing() {
        String line = ":nick!user@host PRIVMSG #chan :Hello world";
        IrcMessage msg = ProtocolParser.parse(line);
        
        assertEquals("PRIVMSG", msg.getCommand());
        assertEquals("nick", msg.getPrefix().getNick());
        assertEquals("#chan", msg.getParams()[0]);
        assertEquals("Hello world", msg.getParams()[1]);
    }

    public void testCtcpAction() {
        String line = ":nick!u@h PRIVMSG #chan :" + (char)1 + "ACTION waves" + (char)1;
        IrcMessage msg = ProtocolParser.parse(line);
        
        assertTrue(msg.isCtcp());
        assertEquals("ACTION", msg.getCtcpCommand());
        assertEquals("waves", msg.getCtcpParams()[0]);
    }
}
```

#### Test Guidelines
- **No MIDP dependencies** — use only `proto/`, `core/`, `stub/`
- **Fast** — each test < 10ms
- **Isolated** — no shared state between tests
- **Descriptive names** — `test<What>_<Condition>_<Expected>`

### Test Coverage Goals
| Module | Target | Current |
|--------|--------|---------|
| `proto/` | 95% | 92% |
| `core/` | 90% | 87% |
| `net/` | 70% | 45% (needs mock socket) |
| `ui/` | 0% | 0% (requires emulator) |
| `rms/` | 60% | 40% |

---

## Debugging

### Desktop Debugging (Tests)
```bash
# IntelliJ: Right-click test → Debug
# VS Code: F5 with launch.json:
{
    "type": "java",
    "request": "launch",
    "mainClass": "com.chatterbox.test.TestMain",
    "args": ["com.chatterbox.test.ProtocolParserTest"]
}
```

### Emulator Debugging (WTK)
```bash
# 1. Build debug version
ant clean compile -Djavac.debug=on

# 2. Start emulator with JDWP
$WTK_HOME/bin/emulator -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000

# 3. Attach debugger (IntelliJ/VS Code) to localhost:8000
# 4. Install: ant install-emu
# 5. Launch app in emulator
```

### On-Device Debugging (Limited)
- **System.out.println()** → WTK console (emulator only)
- **RMS inspection** → `ant install-emu` → WTK → Tools → Manage RMS
- **Network logging** → Enable `GATEWAY_LOG_LEVEL=DEBUG` on gateway

### Logging in Code
```java
// Use System.out for debug (stripped by ProGuard in release)
System.out.println("[Connection] Connected to " + host + ":" + port);

// For persistent logs, use RMS LogStore
LogStore.log(server, channel, timestamp, nick, message);
```

### Common Debug Targets
| Issue | Debug Approach |
|-------|----------------|
| Connection fails | Breakpoint in `Connection.run()` read loop |
| Parse error | Breakpoint in `ProtocolParser.parse()` |
| UI not updating | Check `Display.callSerially()` usage |
| RMS corrupt | Inspect WTK RMS viewer |
| Flood queue stuck | Log `FloodQueue` state in `send()` |

---

## Code Style & Conventions

### Java Version
- **Source/Target:** 1.5 (MIDP 2.0 requirement)
- **No:** generics, enums, varargs, enhanced for-loop, annotations
- **Yes:** `StringBuilder`, `ArrayList` (but prefer arrays for performance)

### Naming
```java
// Classes: PascalCase
public class ConnectionManager { }

// Methods: camelCase
public void sendMessage(String text) { }

// Constants: UPPER_SNAKE
private static final int MAX_BUFFER = 200;

// Fields: camelCase, no prefix
private Session session;
private final MessageHistory history;

// Local vars: camelCase
String channelName = "#general";
```

### Formatting
- **Indentation:** 4 spaces (no tabs)
- **Braces:** K&R style (opening brace same line)
- **Line length:** 100 chars max
- **Imports:** Explicit (no `.*`), grouped: java, javax, com.chatterbox

```java
// Good
if (connected) {
    sendPing();
} else {
    scheduleReconnect();
}

// Bad
if(connected){
sendPing();
}
```

### MIDP-Specific Patterns

#### RecordStore (RMS)
```java
// Always close in finally
RecordStore rs = null;
try {
    rs = RecordStore.openRecordStore("name", true);
    // ...
} catch (RecordStoreException e) {
    // Handle
} finally {
    if (rs != null) {
        try { rs.closeRecordStore(); } catch (RecordStoreException ignored) {}
    }
}
```

#### LCDUI Thread Safety
```java
// Never update UI from network thread
// WRONG:
public void onMessage(String msg) {
    chatView.append(msg);  // Crash or undefined behavior!
}

// RIGHT:
public void onMessage(final String msg) {
    Display.getDisplay(midlet).callSerially(new Runnable() {
        public void run() {
            chatView.append(msg);
        }
    });
}
```

#### Resource Cleanup
```java
// MIDlet lifecycle
protected void destroyApp(boolean unconditional) {
    connectionManager.disconnectAll();
    sessionManager.saveAll();
    // Close RMS handled by SessionManager
}
```

---

## Contributing

### Workflow
1. Fork repository
2. Create feature branch: `git checkout -b feature/awesome-thing`
3. Make changes with tests
4. Run `ant clean dist test` — all must pass
5. Submit PR with description

### Commit Messages
```
feat: add /ban command with hostmask support

- Implement ban logic in CommandProcessor
- Add ban entry to IgnoreList with type=BAN
- Update nick list context menu
- Add tests for ban parsing

Closes #42
```

### PR Checklist
- [ ] `ant test` passes (238 tests)
- [ ] `ant clean dist` produces JAR < 300 KB
- [ ] New code has unit tests (if in proto/core)
- [ ] No new warnings in build output
- [ ] Updated relevant documentation
- [ ] Commit messages follow convention

### CI Workflows (all run on push/PR)
| Workflow | What it proves |
|----------|----------------|
| **CI** | `javac` compile + full test suite on JDK 21, gateway syntax check |
| **Gateway functional test** | Real gateway vs fake TLS ircd: auth, relay, echo, `/healthz` |
| **Docs build check** | Strict `mkdocs build` |
| **Emulator boot test** | Headless MIDlet boot to the server list, zero errors |
| **Full build** | Compile, test, versioned JAR/JAD artifacts (tags stamp the version) |

### Areas Welcoming Contributions
- **Protocol:** IRCv3 support (CAP, batch, labeled-response)
- **UI:** Touch improvements for S60 5th Ed.
- **Gateway:** Prometheus metrics, health endpoint
- **Tests:** Network layer mocking, UI automation
- **Docs:** Translations, more examples

---

## Release Process

### Versioning
- **Scheme:** SemVer (MAJOR.MINOR.PATCH)
- **Tag:** `v1.2.3`
- **JAD:** `MIDlet-Version: 1.2.3`

### Release Steps
```bash
# 1. Update version in build.xml and ChatterboxMIDlet.java
# 2. Update CHANGELOG.md
# 3. Build release
ant clean dist

# 4. Verify
ls -lh dist/chatterbox.jar  # < 300 KB
ant test                    # 238 pass

# 5. Tag
git tag -a v1.2.3 -m "Release 1.2.3"
git push origin v1.2.3

# 6. GitHub Release: upload dist/chatterbox.jar + .jad
```

---

## Useful Scripts

### Quick Test Cycle
```bash
#!/bin/bash
# test-cycle.sh
set -e
ant clean compile test
echo " All tests pass"
```

### Device Deploy (Bluetooth)
```bash
#!/bin/bash
# deploy-bt.sh
ant dist
bluetooth-sendto --device="Nokia E71" dist/chatterbox.jad dist/chatterbox.jar
```

### Emulator Launch
```bash
#!/bin/bash
# run-emu.sh
ant install-emu
$WTK_HOME/bin/emulator -Xdescriptor:dist/chatterbox.jad
```

---

## Useful Resources

### Specifications
- [RFC 2812](https://tools.ietf.org/html/rfc2812) — IRC Client Protocol
- [RFC 1459](https://tools.ietf.org/html/rfc1459) — Original IRC (case mapping)
- [MIDP 2.0 Spec](https://jcp.org/en/jsr/detail?id=118)
- [CLDC 1.1 Spec](https://jcp.org/en/jsr/detail?id=139)

### Tools
- [WTK 2.5.2 Archive](https://web.archive.org/web/20100115071457/http://java.sun.com/products/sjwtoolkit/download-2_5_2.html)
- [ProGuard Manual](https://www.guardsquare.com/en/proguard/manual)
- [Nokia S60 SDK](https://web.archive.org/web/20120317024941/http://www.forum.nokia.com/Tools_and_Docs/) (archived)

### Community
- **IRC:** `#chatterbox` on Libera.Chat
- **Issues:** GitHub Issues
- **Wiki:** This documentation

---

## Debugging Checklist

| Symptom | Check |
|---------|-------|
| App won't start | `MIDlet-1` class name in JAD matches `ChatterboxMIDlet` |
| ClassNotFound | ProGuard over-obfuscated → check `proguard.cfg` keep rules |
| RMS full | `RecordStore.getSize()` vs `getSizeAvailable()` |
| OutOfMemory | Reduce `scrollback`, check for leaks in `MessageHistory` |
| Network timeout | `Connection` read timeout, keepalive PING |
| UI freeze | Long operation on LCDUI thread → move to worker |

---

## Related Docs

- [Architecture](architecture.md) — Code structure deep dive
- [Building](building.md) — Build system reference
- [Testing](building.md#running-tests) — Test commands
- [Configuration](configuration.md) — Settings architecture