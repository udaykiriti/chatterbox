---
id: session
title: Session
triggers: [session, command, event, channel, nick, join, part, message, listener]
packages: [core]
complexity: medium
---

# Session Skill

Work with session management, command processing, and the central event flow.

## When to Use

- Modifying session state or lifecycle
- Adding new IRC commands
- Working with the event system
- Debugging session-related issues
- Understanding the core architecture

## Package Overview

Session and command classes are in `midlet/src/com/chatterbox/core/`.

| Class | Responsibility |
|-------|---------------|
| `Session` | Central session state (connection, channels, nicks) |
| `SessionManager` | Manages multiple server sessions |
| `ChannelState` | Per-channel state (topic, nicks, modes) |
| `MessageRing` | Circular buffer for recent messages |
| `Message` | Single message data class |
| `CommandProcessor` | Parses and executes IRC commands |
| `Sender` | Sends messages to IRC server |
| `IrcEventListener` | Interface for session event callbacks |
| `ServerState` | Server connection state |
| `UserEntry` | User data (nick, ident, host, modes) |
| `IrcCase` | Case-insensitive string comparison |
| `Str` | String utilities |
| `Theme` | Color and appearance management |
| `HighlightRules` | Highlight matching rules |
| `IgnoreList` | Ignore list management |
| `AutoAway` | Auto-away on idle |
| `ServiceIdentify` | NickServ identification |
| `WhoisCache` | WHOIS response caching |
| `MessageHistory` | Message history buffer |

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    SessionManager                        │
│  (manages multiple Session instances)                   │
├─────────────────────────────────────────────────────────┤
│                      Session                             │
│  (central state: connection, channels, nicks)           │
├──────────────┬──────────────┬──────────────┬────────────┤
│ CommandProc  │  ChannelState│ MessageRing  │  Sender    │
│              │              │              │            ├──────────────┼──────────────┼──────────────┼────────────┤
│  IrcConnection│  E2E        │  Theme       │  Highlight │
│  (net/)      │  (crypto/)  │              │  Rules     │
└──────────────┴──────────────┴──────────────┴────────────┘
```

## Session Lifecycle

```
New → Connecting → Connected → Ready → Disconnected
                                    ↓
                              Reconnecting
```

### Creating a Session

```java
ConnectionConfig config = new ConnectionConfig();
config.host = "irc.libera.chat";
config.port = 6697;
config.useTls = true;

Session session = new Session(config, eventListener);
session.connect();
```

### Event Listener

```java
public class MyListener implements IrcEventListener {
    public void onMessage(Message msg) {
        // Handle incoming message
    }

    public void onJoin(String channel, String nick) {
        // Handle join
    }

    public void onPart(String channel, String nick, String reason) {
        // Handle part
    }

    public void onQuit(String nick, String reason) {
        // Handle quit
    }

    public void onNickChange(String oldNick, String newNick) {
        // Handle nick change
    }

    public void onDisconnect(String reason) {
        // Handle disconnect
    }
}
```

## Command Processing

The `CommandProcessor` handles all IRC commands:

### Adding a New Command

In `CommandProcessor.java`:
```java
public void execute(String input) {
    if (input.startsWith("/")) {
        String command = input.substring(1).toLowerCase();
        String[] parts = Str.split(command, " ");
        String cmd = parts[0];
        String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);

        if (cmd.equals("me")) {
            handleMe(args);
        } else if (cmd.equals("mycmd")) {
            handleMyCmd(args);  // Your new command
        }
        // ...
    } else {
        // Regular message
        sendMessage(input);
    }
}

private void handleMyCmd(String[] args) {
    // Implement command logic
    // Send to server, update UI, etc.
}
```

### Command Flow

```
User Input → InputLine → CommandProcessor.execute()
                                ↓
                    Parse command + args
                                ↓
                    Route to handler method
                                ↓
                    Send to server / Update UI
```

## Channel State

Each channel has its own state:

```java
ChannelState channel = session.getChannel("#mychannel");

// Access channel data
String topic = channel.getTopic();
UserEntry[] nicks = channel.getNicks();
boolean isOp = channel.isOp("mynick");
boolean isVoice = channel.isVoice("mynick");

// Modify channel state
channel.addNick("newnick", "@");
channel.removeNick("oldnick");
channel.setTopic("New topic");
```

## MessageRing

The `MessageRing` is a circular buffer for recent messages:

```java
MessageRing ring = session.getMessageRing("#channel");

// Add message
ring.add(new Message("nick", "Hello!"));

// Get recent messages
Message[] recent = ring.getRecent(50);

// Search messages
Message[] matches = ring.search("keyword");
```

## AutoAway

Automatically sets away status when idle:

```java
// Configure
AutoAway.setEnabled(true);
AutoAway.setTimeoutMinutes(10);
AutoAway.setMessage("Away from phone");

// Check status
boolean isAway = AutoAway.isAway();
```

## Highlight Rules

Manage highlight matching:

```java
// Add a rule
HighlightRules.add("keyword", true);  // true = case-sensitive
HighlightRules.add("nick:.*", false);  // regex pattern

// Check if message matches
boolean highlight = HighlightRules.matches(message);

// Remove a rule
HighlightRules.remove("keyword");
```

## Ignore List

Manage ignored users:

```java
// Add to ignore list
IgnoreList.add("spammer");
IgnoreList.add("*!*@bad.host");

// Check if ignored
boolean ignored = IgnoreList.isIgnored("spammer", "user", "host");

// Remove from ignore list
IgnoreList.remove("spammer");
```

## ServiceIdentify

NickServ identification:

```java
// Configure
ServiceIdentify.setNick("mynick");
ServiceIdentify.setPassword("secret");

// Trigger identification
ServiceIdentify.identify(session);

// Check status
boolean identified = ServiceIdentify.isIdentified();
```

## Common Tasks

### Send a message

```java
// Via Session
session.sendMessage("#channel", "Hello, world!");

// Via CommandProcessor
commandProcessor.execute("/msg #channel Hello, world!");
```

### Join a channel

```java
session.join("#channel");
session.join("#channel", "key");  // With key
```

### Part a channel

```java
session.part("#channel");
session.part("#channel", "Goodbye!");
```

### Change nick

```java
session.changeNick("newnick");
```

### Set away

```java
session.setAway("Away from phone");
session.setAway(null);  // Remove away
```

## Testing Session Code

- `SessionTest` has 83 tests covering protocol flow
- Add tests for new commands in `SessionTest`
- Test event listener callbacks
- Test channel state modifications
- Run `ant test` to verify

## Troubleshooting

### Session not connecting
- Verify `ConnectionConfig` is correct
- Check network connectivity
- Review `IrcConnection` error handling
- Check server supports required capabilities

### Commands not working
- Verify command is registered in `CommandProcessor`
- Check command parsing (case sensitivity)
- Review `InputLine` command detection
- Test with `/help` to see available commands

### Events not firing
- Verify `IrcEventListener` is registered
- Check `Session` event dispatch logic
- Review `IrcParser` event generation
- Add logging to trace event flow

## Anti-Patterns

```java
// WRONG: Modifying session state from multiple threads without synchronization
public void onMessage(Message msg) {
    channelState.addMessage(msg);  // Called from network thread
}

// RIGHT: Use event queue or synchronize access
public void onMessage(Message msg) {
    eventQueue.post(new MessageEvent(msg));  // Process on main thread
}
```

```java
// WRONG: Not checking for null channel
ChannelState channel = session.getChannel("#nonexistent");
channel.sendMessage("hello");  // NullPointerException!

// RIGHT: Check for null
ChannelState channel = session.getChannel("#nonexistent");
if (channel != null) {
    channel.sendMessage("hello");
}
```

```java
// WRONG: Catching all exceptions silently
try {
    session.execute("/command");
} catch (Exception e) {
    // Swallowed! User never knows
}

// RIGHT: Handle or report
try {
    session.execute("/command");
} catch (IllegalArgumentException e) {
    display.showError("Invalid command");
}
```

## Code Template: Adding a New Command to CommandProcessor

```java
// In core/CommandProcessor.java

private void handleMyCmd(String[] args) {
    if (args.length < 1) {
        session.showMessage("Usage: /mycmd <arg>");
        return;
    }

    String arg = args[0];

    // Validate
    if (!isValid(arg)) {
        session.showMessage("Invalid argument");
        return;
    }

    // Execute
    String result = doSomething(arg);

    // Report result
    session.showMessage("Result: " + result);
}

// In execute() method, add routing:
else if (cmd.equals("mycmd")) {
    handleMyCmd(args);
}
```
