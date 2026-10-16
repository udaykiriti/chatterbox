---
id: test
title: Test
triggers: [test, testing, junit, assert, suite, coverage, fail, pass]
packages: [test]
complexity: easy
---

# Test Skill

Run and maintain the Chatterbox test suite.

## When to Use

- Running tests before commits
- Adding new test cases
- Debugging test failures
- Understanding test coverage

## Running Tests

```bash
# Run all tests via Ant
ant test

# Run tests manually (without Ant)
javac -encoding UTF-8 -cp build/classes -d build/tests \
  $(find test/shim test/src -name '*.java')
java -cp build/classes:build/tests com.chatterbox.test.TestMain

# Run a single test class
java -cp build/classes:build/tests com.chatterbox.test.ProtocolParserTest
```

## Test Suites

| Suite | Path | Tests | Coverage Area |
|-------|------|-------|---------------|
| `ProtocolParserTest` | `test/src/.../ProtocolParserTest.java` | 32 | IRC line parsing, CTCP, numerics |
| `CoreTest` | `test/src/.../CoreTest.java` | 31 | Str, IrcCase, MessageRing, FloodQueue, ChannelState |
| `SessionTest` | `test/src/.../SessionTest.java` | 83 | Full protocol flow, commands, auto-away |
| `CryptoTest` | `test/src/.../CryptoTest.java` | 21 | HKDF, HMAC-SHA256, SHA-256, RFC 8439 |
| `X25519Test` | `test/src/.../X25519Test.java` | 9 | RFC 7748 vectors, DH, non-canonical input |
| `SecurityTest` | `test/src/.../SecurityTest.java` | 29 | IrcSanitizer, PasswordObfuscator |
| `E2ETest` | `test/src/.../E2ETest.java` | 28 | Pre-shared-key E2E codec, session integration |

**Total: 233 tests**

## Test Framework

The project uses a **custom JUnit 3 shim** (no external JARs):

- `test/shim/junit/framework/TestCase.java` — Base test class
- `test/shim/junit/framework/Assert.java` — Assertion methods
- `test/shim/junit/framework/TestRunner.java` — Test runner

### Writing Tests

```java
package com.chatterbox.test;

import junit.framework.TestCase;

public class MyFeatureTest extends TestCase {

    public void testSomething() {
        // Arrange
        String input = "test input";

        // Act
        String result = MyFeature.process(input);

        // Assert
        assertEquals("expected", result);
    }
}
```

### Assertion Methods Available

- `assertEquals(expected, actual)`
- `assertTrue(condition)`
- `assertFalse(condition)`
- `assertNull(object)`
- `assertNotNull(object)`
- `assertSame(expected, actual)`
- `fail(message)`

## Anti-Patterns

```java
// WRONG: Testing implementation details rather than behavior
public void testPrivateMethod() {
    MyClass obj = new MyClass();
    // Can't access private method!
}

// RIGHT: Test public API behavior
public void testPublicBehavior() {
    MyClass obj = new MyClass();
    String result = obj.process("input");
    assertEquals("expected", result);
}
```

```java
// WRONG: Test depends on external state
public void testDatabase() {
    // Assumes database is running!
}

// RIGHT: Use test fixtures or stubs
public void testWithStub() {
    MyStore store = new MyStore();  // Uses in-memory stub
    store.save(data);
    assertEquals(data, store.load());
}
```

```java
// WRONG: Catching exception and not failing
public void testException() {
    try {
        obj.doSomething();
    } catch (Exception e) {
        // Swallowed! Test passes even on error
    }
}

// RIGHT: Let exception propagate or assert it
public void testException() {
    try {
        obj.doSomething();
        fail("Expected exception");
    } catch (ExpectedException e) {
        // Expected
    }
}
```

## Code Template: Adding a New Test Suite

```java
package com.chatterbox.test;

import junit.framework.TestCase;

/**
 * Test suite for MyFeature.
 */
public class MyFeatureTest extends TestCase {

    // === Test: Basic functionality ===

    public void testBasicCase() {
        // Arrange
        MyFeature feature = new MyFeature();

        // Act
        String result = feature.process("input");

        // Assert
        assertNotNull(result);
        assertEquals("expected", result);
    }

    // === Test: Edge cases ===

    public void testEmptyInput() {
        MyFeature feature = new MyFeature();
        String result = feature.process("");
        assertNull(result);
    }

    public void testNullInput() {
        MyFeature feature = new MyFeature();
        try {
            feature.process(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    // === Test: Integration ===

    public void testIntegration() {
        // Test interaction with other components
        MyFeature feature = new MyFeature();
        AnotherComponent comp = new AnotherComponent();
        comp.setFeature(feature);
        comp.run();
        assertTrue(comp.isSuccess());
    }
}
```

## Test Constraints

- Tests run on **desktop JDK** (8+) — no WTK/emulator needed
- Tests use **stub MIDP classes** from `stub/` — no real device APIs
- `proto/` and `crypto/` classes are **fully unit-testable** (pure Java)
- `ui/` classes are **not unit-tested** — manual/emulator testing only
- All tests must pass before committing

## Coverage Goals

| Package | Minimum Coverage | Notes |
|---------|-----------------|-------|
| `proto/` | 90%+ | Pure Java, fully testable |
| `crypto/` | 90%+ | Pure Java, fully testable |
| `core/` | 70%+ | Some classes need MIDP stubs |
| `net/` | 50%+ | Socket I/O harder to test |
| `rms/` | 50%+ | RecordStore needs stubs |
| `ui/` | N/A | Manual testing only |

## Adding a New Test Suite

1. Create `test/src/com/chatterbox/test/MyNewTest.java`
2. Extend `junit.framework.TestCase`
3. Add `public void test*()` methods
4. Register in `test/src/com/chatterbox/test/TestMain.java`
5. Run `ant test` to verify
