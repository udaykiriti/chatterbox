---
id: debugging
title: Debugging
triggers: [debug, crash, error, exception, fail, broken, issue, problem, trace, log]
packages: []
complexity: medium
---

# Debugging Skill

Systematic debugging decision trees for common issues.

## When to Use

- App crashes or freezes
- Connection failures
- UI rendering issues
- Test failures
- Performance problems
- Any unexpected behavior

## Debugging Decision Trees

### App Crashes

```
App crashes on launch
├── Check JAD file
│   ├── Midlet-Jar-URL correct?
│   ├── Midlet-Name correct?
│   └── All required attributes present?
├── Check JAR file
│   ├── JAR contains all classes?
│   ├── Manifest correct?
│   └── Preverification done?
├── Check device compatibility
│   ├── MIDP 2.0 supported?
│   ├── CLDC 1.1 supported?
│   └── Screen size compatible?
└── Check logs
    ├── WTK console output
    ├── Device logs
    └── Stack trace analysis
```

### Connection Fails

```
Cannot connect to server
├── Check network
│   ├── Device has data connection?
│   ├── Signal strength OK?
│   └── APN settings correct?
├── Check server
│   ├── Server address correct?
│   ├── Port correct?
│   └── Server is online?
├── Check TLS
│   ├── Certificate valid?
│   ├── TLS version supported?
│   └── Certificate pin matches?
├── Check auth
│   ├── Nick available?
│   ├── Password correct?
│   └── SASL configured?
└── Check logs
    ├── Connection timeout?
    ├── Connection refused?
    └── Security exception?
```

### UI Not Rendering

```
UI displays incorrectly
├── Check screen size
│   ├── Hardcoded pixel values?
│   ├── Font metrics assumed?
│   └── Layout too large?
├── Check LCDUI constraints
│   ├── Using unsupported widgets?
│   ├── Too many items in Form?
│   └── Custom Canvas paint() too slow?
├── Check data
│   ├── Null values displayed?
│   ├── Empty lists handled?
│   └── Long strings truncated?
└── Check theme
    ├── Colors defined?
    ├── Font sizes valid?
    └── Theme applied?
```

### Test Fails

```
Test fails
├── Check test code
│   ├── Assertion correct?
│   ├── Test data valid?
│   └── Test order dependency?
├── Check source code
│   ├── Recent changes?
│   ├── Edge cases handled?
│   └── Null checks added?
├── Check environment
│   ├── Stubs compiled?
│   ├── Classpath correct?
│   └── JDK version compatible?
└── Check dependencies
    ├── Other tests interfering?
    ├── Shared state?
    └── Resource leaks?
```

## Debugging Techniques

### 1. Binary Search

When a bug was introduced recently:
```bash
# Find the commit that introduced the bug
git bisect start
git bisect bad HEAD
git bisect good v1.0.0
# Test each commit
ant test
git bisect good  # or bad
# Repeat until found
```

### 2. Print Debugging

Add temporary logging:
```java
// In Java ME
System.out.println("DEBUG: value=" + value);
System.out.println("DEBUG: state=" + state);

// View in WTK console or desktop output
```

### 3. Isolate the Problem

```java
// Create minimal test case
public void testMinimal() {
    // Test just the failing component
    // Remove all other dependencies
    String result = MyClass.method("input");
    assertEquals("expected", result);
}
```

### 4. Check Assumptions

```java
// Verify assumptions
assertNotNull("Input is null", input);
assertFalse("Input is empty", input.length() == 0);
assertEquals("Unexpected value", expected, actual);
```

## Common Issues and Fixes

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| `NullPointerException` | Missing null check | Add null validation |
| `ArrayIndexOutOfBounds` | Off-by-one error | Check array bounds |
| `OutOfMemoryError` | Memory leak | Reduce object churn |
| `StackOverflowError` | Infinite recursion | Add base case |
| `ClassNotFoundException` | Missing class in JAR | Check ProGuard config |
| `RecordStoreException` | RMS full or corrupt | Delete and recreate |
| `IOException: Connection refused` | Server down | Check server status |
| `SecurityException` | TLS/cert issue | Update cert pins |
| UI flickering | Too many repaints | Double buffering |
| Slow performance | Inefficient algorithm | Profile and optimize |

## Debugging Checklist

- [ ] Reproduce the issue consistently
- [ ] Identify the minimal test case
- [ ] Check recent changes (git log)
- [ ] Add logging at key points
- [ ] Verify assumptions
- [ ] Test the fix
- [ ] Add regression test
- [ ] Remove debug logging
- [ ] Update documentation

## Anti-Patterns

```java
// WRONG: Catching and ignoring exceptions
try {
    riskyOperation();
} catch (Exception e) {
    // Ignored! Bug hidden!
}

// RIGHT: Log and handle
try {
    riskyOperation();
} catch (Exception e) {
    System.err.println("Error in riskyOperation: " + e.getMessage());
    // Handle or rethrow
}
```

```java
// WRONG: Debug logging left in production code
System.out.println("DEBUG: " + sensitiveData);

// RIGHT: Use proper logging levels
if (Log.isDebugEnabled()) {
    Log.debug("Operation: " + operation);
}
```

```java
// WRONG: Changing multiple things at once
// Fixed 3 things, test passes, but which fix worked?

// RIGHT: Change one thing at a time
// Fix 1 → test → Fix 2 → test → Fix 3 → test
```
