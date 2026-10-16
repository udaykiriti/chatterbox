---
id: protocol
title: Protocol
triggers: [irc, parse, ctcp, sasl, cap, dcc, numeric, codec, ircv3, message]
packages: [proto]
complexity: medium
---

# Protocol Skill

Work with the IRC protocol layer — parsing, encoding, CTCP, SASL, CAP, DCC.

## When to Use

- Adding new IRC commands or numerics
- Modifying line parsing logic
- Working with CTCP, SASL, CAP, or DCC
- Debugging protocol issues
- Understanding the IRC message flow

## Package Overview

All protocol classes are in `midlet/src/com/chatterbox/proto/` — **pure Java, zero MIDP dependencies**.

| Class | Responsibility |
|-------|---------------|
| `IrcParser` | Parse raw IRC lines into structured messages |
| `IrcCodec` | Encode/decode IRC messages to/from bytes |
| `IrcLine` | Immutable representation of a parsed IRC line |
| `LineReader` | Buffered line reader for socket input |
| `Ctcp` | CTCP message parsing and construction |
| `Cap` | IRCv3 capability negotiation |
| `Sasl` | SASL authentication (PLAIN, EXTERNAL) |
| `Dcc` | DCC file transfer protocol |
| `Format` | IRC formatting codes (bold, color, etc.) |
| `Numeric` | IRC numeric reply constants |
| `Utf8` | UTF-8 encoding/decoding helpers |
| `IrcSanitizer` | Input sanitization (security-critical) |

## IRC Message Flow

```
Socket → LineReader → IrcParser → IrcLine → Session → CommandProcessor
                                                        ↓
                                              IrcEventListener
                                                        ↓
                                                     UI Update
```

## Adding a New IRC Command

### Step 1: Add numeric constant (if needed)

In `proto/Numeric.java`:
```java
public static final int RPL_TOPIC = 332;
public static final int RPL_NEWCMD = 999;  // your new numeric
```

### Step 2: Add parsing (if needed)

In `proto/IrcParser.java`:
```java
// Parse :server 999 nick :message
if (numeric == Numeric.RPL_NEWCMD) {
    return new IrcLine(IrcLine.TYPE_NUMERIC, "RPL_NEWCMD",
        new String[] { nick, message });
}
```

### Step 3: Add handling

In `core/CommandProcessor.java`:
```java
case Numeric.RPL_NEWCMD:
    handleNewCmd(line);
    break;
```

### Step 4: Add tests

In `test/src/com/chatterbox/test/SessionTest.java`:
```java
public void testNewCmd() {
    // Test the new command handling
}
```

## CTCP

CTCP messages are wrapped in `\x01` characters:

```
PRIVMSG nick :\x01VERSION\x01
NOTICE nick :\x01PING 12345\x01
```

Use `Ctcp.parse(line)` to extract CTCP type and parameters.
Use `Ctcp.build(type, params)` to construct CTCP messages.

## SASL Authentication

Supported mechanisms:
- **PLAIN** — Base64-encoded credentials
- **EXTERNAL** — Client certificate (rarely used on mobile)

Flow:
1. `CAP REQ :sasl`
2. `AUTHENTICATE PLAIN`
3. `AUTHENTICATE <base64>`
4. `900` (logged in) → `CAP END`

## CAP Negotiation

```java
// Request capabilities
Cap.request("sasl");
Cap.request("multi-prefix");
Cap.request("away-notify");

// Check if server has capability
if (Cap.has("sasl")) { ... }
```

## DCC

DCC is used for direct client-to-client file transfers:
- `DCC SEND filename ip port size`
- `DCC CHAT ip port`

Parse with `Dcc.parse(line)`, construct with `Dcc.build(...)`.

## Security: IrcSanitizer

**All IRC input must pass through `IrcSanitizer`** before display or storage:

```java
String clean = IrcSanitizer.sanitize(rawInput);
```

This strips control characters, null bytes, and other dangerous content.

## Common Patterns

### Parsing a PRIVMSG

```java
IrcLine line = IrcParser.parse(raw);
if (line.getType() == IrcLine.TYPE_PRIVMSG) {
    String target = line.getArg(0);
    String message = line.getArg(1);
    boolean isAction = Ctcp.isAction(message);
    // ...
}
```

### Sending a command

```java
// Via Session
session.send("PRIVMSG " + target + " :" + message);

// Via CommandProcessor
commandProcessor.execute("/privmsg " + target + " " + message);
```

## Anti-Patterns

```java
// WRONG: Not sanitizing IRC input before display
String message = line.getMessage();
display.showMessage(message);  // Security risk!

// RIGHT: Always sanitize first
String message = IrcSanitizer.sanitize(line.getMessage());
display.showMessage(message);
```

```java
// WRONG: Parsing IRC line manually
String[] parts = rawLine.split(":");  // Fragile, error-prone

// RIGHT: Use IrcParser
IrcLine line = IrcParser.parse(rawLine);
```

```java
// WRONG: Hardcoding numeric values
if (numeric == 332) {  // Magic number
    // ...
}

// RIGHT: Use named constants
if (numeric == Numeric.RPL_TOPIC) {
    // ...
}
```

```java
// WRONG: Not handling CTCP actions
String message = line.getMessage();
if (message.startsWith("\x01")) {
    // Treat as regular message — wrong!
}

// RIGHT: Use Ctcp helper
if (Ctcp.isAction(message)) {
    String action = Ctcp.parseAction(message);
    // Display as * nick action
}
```

## Code Template: Adding a New Numeric

```java
// 1. In proto/Numeric.java — add constant
public static final int RPL_NEWCMD = 999;

// 2. In proto/IrcParser.java — add parsing
// In the numeric parsing section:
if (numeric == Numeric.RPL_NEWCMD) {
    String nick = parser.next();
    String message = parser.next();
    return new IrcLine(IrcLine.TYPE_NUMERIC, "RPL_NEWCMD",
        new String[] { nick, message });
}

// 3. In core/CommandProcessor.java — add handling
case Numeric.RPL_NEWCMD:
    handleNewCmd(line.getArgs());
    break;

// 4. In test/src/com/chatterbox/test/SessionTest.java — add test
public void testNewCmdNumeric() {
    // Simulate server sending :server 999 nick :message
    session.processLine(":server 999 nick :message");
    // Verify handling
}
```

## Testing Protocol Changes

- Add test vectors to `ProtocolParserTest`
- Add integration tests to `SessionTest`
- Test both parsing and round-trip encoding
- Include edge cases: empty messages, unicode, very long lines
