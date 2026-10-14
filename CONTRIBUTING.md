# Contributing to Chatterbox

Thank you for contributing! This guide covers the workflow, standards, and processes.

---

##  Ways to Contribute

| Type | Description |
|------|-------------|
| **Code** | Bug fixes, features, optimizations |
| **Tests** | Unit tests for proto/core/net modules |
| **Docs** | Fixes, expansions, translations |
| **Gateway** | Metrics, health checks, hardening |
| **Device testing** | Real hardware reports (E63, E71, C3, etc.) |
| **Issues** | Bug reports, feature requests, triage |

---

##  Quick Start

```bash
# 1. Fork & clone
git clone https://github.com/yourfork/chatterbox.git
cd chatterbox

# 2. Set up build env
export WTK_HOME=/opt/WTK2.5.2
export PROGUARD_HOME=/opt/proguard7.4

# 3. Verify build & tests
ant clean dist test
# Should produce: dist/chatterbox.jar (< 300 KB), 129 tests pass

# 4. Create feature branch
git checkout -b feature/awesome-thing
```

---

##  Development Workflow

### Branch Naming
```
feature/<short-description>   # New features
fix/<short-description>       # Bug fixes
docs/<short-description>      # Documentation only
refactor/<short-description>  # Code cleanup
test/<short-description>      # Test additions
```

### Commit Messages
Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

**Types:** `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `perf`, `ci`

**Examples:**
```
feat(gateway): add Prometheus metrics endpoint

- Expose /metrics on port 9090
- Include connection count, message rate, errors

Closes #42
```

```
fix(protocol): handle malformed CTCP without crash

- Parser now gracefully handles missing delimiter
- Adds test for edge case

Fixes #128
```

---

##  Testing Requirements

### Before Submitting PR
```bash
# Full test suite
ant test
# Must show: Tests run: 129, Failures: 0, Errors: 0

# Release build
ant clean dist
# Must produce: dist/chatterbox.jar < 300 KB

# Quick sanity check
ls -lh dist/chatterbox.jar
```

### Test Coverage Guidelines
| Module | Min Coverage | Notes |
|--------|--------------|-------|
| `proto/` | 95% | Pure Java, easy to test |
| `core/` | 90% | Pure Java, testable |
| `net/` | 70% | Needs mock socket framework |
| `ui/` | 0% | Requires emulator (manual) |
| `rms/` | 60% | Can test with WTK RMS |

### Adding Tests
1. **Location:** `test/src/com/chatterbox/test/`
2. **Naming:** `<ClassName>Test.java`
3. **Base:** Extend `TestCase` from `test/shim/`
4. **Run:** `ant test -Dtest.class=com.chatterbox.test.NewTest`

```java
// Example test
public class FloodQueueTest extends TestCase {
    public void testTokenBucketRefill() throws Exception {
        FloodQueue q = new FloodQueue(2, 2000); // 2 per 2s
        assertTrue(q.tryAcquire());
        assertTrue(q.tryAcquire());
        assertFalse(q.tryAcquire()); // exhausted
        Thread.sleep(2100);
        assertTrue(q.tryAcquire()); // refilled
    }
}
```

---

##  Code Style

### Java Version Constraints
- **Source/Target:** 1.5 (MIDP 2.0)
- **Forbidden:** Generics, enums, varargs, enhanced for, annotations, lambdas, streams
- **Allowed:** `StringBuilder`, arrays, `Vector`/`Hashtable` (prefer arrays)

### Formatting
- **Indent:** 4 spaces
- **Braces:** K&R (same line)
- **Line length:** 100 chars
- **Imports:** Explicit, grouped (java → javax → com.chatterbox)

### Naming
```java
// Classes
public class ConnectionManager { }

// Interfaces
public interface CommandHandler { }

// Methods
public void sendMessage(String text) { }

// Constants
private static final int MAX_BUFFER = 200;

// Fields
private Session session;

// Local vars
String channelName = "#general";
```

### MIDP-Specific Rules
```java
// ALWAYS close RecordStore in finally
RecordStore rs = null;
try {
    rs = RecordStore.openRecordStore("name", true);
    // ...
} finally {
    if (rs != null) try { rs.closeRecordStore(); } catch (RecordStoreException ignored) {}
}

// NEVER update UI from network thread
// WRONG:
chatView.append(msg);

// RIGHT:
Display.getDisplay(midlet).callSerially(() -> chatView.append(msg));
```

---

##  PR Checklist

### Code Quality
- [ ] `ant test` passes (129 tests)
- [ ] `ant clean dist` produces JAR < 300 KB
- [ ] No new compiler warnings
- [ ] ProGuard keeps rules updated if new classes added
- [ ] MIDP 2.0 / CLDC 1.1 compatible (no Java 6+ APIs)

### Testing
- [ ] Unit tests added for new logic (proto/core)
- [ ] Manual test on emulator (WTK or S60 SDK)
- [ ] Edge cases covered (empty input, malformed data, limits)

### Documentation
- [ ] Updated relevant `.md` files in `docs/`
- [ ] `CHANGELOG.md` entry under `[Unreleased]`
- [ ] Code comments for non-obvious logic

### Git Hygiene
- [ ] Commits are logical, atomic
- [ ] Commit messages follow convention
- [ ] Branch rebased on latest `main`
- [ ] No merge commits in feature branch

---

## ️ Issue Labels

| Label | Purpose |
|-------|---------|
| `bug` | Something broken |
| `enhancement` | New feature request |
| `docs` | Documentation improvement |
| `good first issue` | Beginner-friendly |
| `help wanted` | Community help needed |
| `device-testing` | Needs real hardware verification |
| `gateway` | Gateway server related |
| `protocol` | IRC protocol parsing |
| `ui` | LCDUI/Canvas issues |
| `performance` | Optimization |

---

##  Code Review Guidelines

### For Reviewers
- Check test coverage for new code
- Verify MIDP compatibility
- Confirm ProGuard doesn't break new classes
- Validate RMS changes don't corrupt existing data
- Test on emulator if UI changed

### For Authors
- Respond to feedback within 48h
- Push follow-up commits (don't force-push after review starts)
- Squash only when requested by maintainer

---

##  Device Testing Matrix

If you have hardware, please test and report:

| Device | Series | MIDP | Status | Notes |
|--------|--------|------|--------|-------|
| Nokia E63 | S60 3rd | 2.0 |  | Primary target |
| Nokia E71 | S60 3rd | 2.0 |  | Primary target |
| Nokia E72 | S60 3rd | 2.0 |  | Primary target |
| Nokia C3-00 | S40 5th | 2.1 |  | QWERTY candybar |
| Nokia 5800 | S60 5th | 2.1 | ️ | Touch + keypad |
| Nokia N95 | S60 3rd | 2.0 | ️ | Slider |
| ... | ... | ... |  | Add your results! |

**Report format:**
```
Device: Nokia E71 (RM-346)
Firmware: 400.21.013
Test: Connect → Join #test → Send/recv → Background → Resume
Result: PASS / FAIL (details)
```

---

##  Resources

### Specifications
- [RFC 2812](https://tools.ietf.org/html/rfc2812) — IRC Client Protocol
- [RFC 1459](https://tools.ietf.org/html/rfc1459) — Case mapping
- [MIDP 2.0](https://jcp.org/en/jsr/detail?id=118) / [CLDC 1.1](https://jcp.org/en/jsr/detail?id=139)

### Tools
- [WTK 2.5.2](https://web.archive.org/web/20100115071457/http://java.sun.com/products/sjwtoolkit/download-2_5_2.html)
- [ProGuard Manual](https://www.guardsquare.com/en/proguard/manual)
- [Nokia S60 SDK](https://web.archive.org/web/20120317024941/http://www.forum.nokia.com/) (archived)

### Community
- **IRC:** `#chatterbox` on Libera.Chat
- **Issues:** GitHub Issues
- **Docs:** `docs/` directory

---

##  License

By contributing, you agree your contributions will be licensed under the **MIT License** (same as project).

---

##  Questions?

- Open a GitHub Discussion
- Ask in `#chatterbox` on Libera.Chat
- Tag maintainers in PR

**Thank you for making Chatterbox better!** 