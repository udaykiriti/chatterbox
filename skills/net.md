---
id: net
title: Network
triggers: [network, connection, socket, connect, disconnect, reconnect, flood, timeout, tls]
packages: [net]
complexity: hard
---

# Network Skill

Work with IRC connections, socket handling, flood control, and reconnection logic.

## When to Use

- Modifying connection behavior
- Working with flood control
- Debugging network issues
- Understanding the connection lifecycle
- Adding new connection features

## Package Overview

All network classes are in `midlet/src/com/chatterbox/net/`.

| Class | Responsibility |
|-------|---------------|
| `IrcConnection` | Main connection class (connect, send, receive, reconnect) |
| `SocketFactory` | Socket creation and configuration |
| `FloodQueue` | Rate limiting for outgoing messages |
| `ConnectionConfig` | Connection parameters (host, port, TLS, timeout) |

## Architecture

```
┌─────────────────────────────────────────┐
│           IrcConnection                 │
│  (lifecycle: connect, disconnect,       │
│   reconnect, send, receive)             │
├──────────────┬──────────────┬───────────┤
│ SocketFactory│  FloodQueue  │ Connection│
│              │              │  Config   │
├──────────────┴──────────────┴───────────┤
│     javax.microedition.io (SocketConnection) │
└─────────────────────────────────────────┘
```

## Connection Lifecycle

```
Disconnected → Connecting → Connected → Authenticating → Ready
     ↑                                                      │
     └──────────────── Reconnecting ←───────────────────────┘
```

### States

| State | Description |
|-------|-------------|
| `Disconnected` | No connection |
| `Connecting` | TCP connection in progress |
| `Connected` | TCP connected, not yet authenticated |
| `Authenticating` | SASL/cap negotiation |
| `Ready` | Fully connected and authenticated |
| `Reconnecting` | Attempting to reconnect after failure |

## Modifying Connection Behavior

### Adding a new connection option

In `ConnectionConfig.java`:
```java
public class ConnectionConfig {
    public String host;
    public int port;
    public boolean useTls;
    public int timeoutMs;
    // Add new field:
    public boolean compression;  // Example: zlib compression
}
```

### Modifying flood control

In `FloodQueue.java`:
```java
// Adjust rate limiting
private static final int MAX_MESSAGES = 5;
private static final int TIME_WINDOW_MS = 2000;

public boolean canSend() {
    long now = System.currentTimeMillis();
    // Remove old entries
    while (!timestamps.isEmpty() && now - timestamps.peek() > TIME_WINDOW_MS) {
        timestamps.poll();
    }
    if (timestamps.size() < MAX_MESSAGES) {
        timestamps.offer(now);
        return true;
    }
    return false;
}
```

### Adding reconnect logic

In `IrcConnection.java`:
```java
private void scheduleReconnect() {
    reconnectAttempts++;
    if (reconnectAttempts > MAX_RECONNECT_ATTEMPTS) {
        notifyDisconnected("Max reconnect attempts reached");
        return;
    }

    int delay = RECONNECT_BASE_DELAY_MS * (1 << (reconnectAttempts - 1));
    if (delay > MAX_RECONNECT_DELAY_MS) {
        delay = MAX_RECONNECT_DELAY_MS;
    }

    TimerTask task = new TimerTask() {
        public void run() {
            connect();
        }
    };
    timer.schedule(task, delay);
}
```

## SocketFactory

Handles socket creation with proper configuration:

```java
SocketFactory.connect(config, new SocketFactory.Callback() {
    public void onConnected(SocketConnection socket) {
        // Connection established
    }

    public void onError(Exception e) {
        // Connection failed
    }
});
```

### TLS Configuration

```java
// In SocketFactory
SocketConnection conn = (SocketConnection) Connector.open(url);
if (config.useTls) {
    // Set TLS parameters
    conn.setSocketOption(SocketConnection.LINGER, 5);
}
```

## Flood Control

The `FloodQueue` prevents server-side throttling or bans:

| Parameter | Default | Description |
|-----------|---------|-------------|
| `MAX_MESSAGES` | 5 | Messages per time window |
| `TIME_WINDOW_MS` | 2000 | Time window duration |
| `BURST_SIZE` | 2 | Allowed burst before throttling |

### Using FloodQueue

```java
FloodQueue queue = new FloodQueue();

if (queue.canSend()) {
    sendRaw(message);
} else {
    // Queue for later or notify user
    queue.queue(message);
}
```

## ConnectionConfig

```java
ConnectionConfig config = new ConnectionConfig();
config.host = "irc.libera.chat";
config.port = 6697;
config.useTls = true;
config.timeoutMs = 30000;

IrcConnection conn = new IrcConnection(config);
conn.connect();
```

## Error Handling

### Common exceptions

| Exception | Cause | Action |
|-----------|-------|--------|
| `ConnectionNotFoundException` | Network unavailable | Retry with backoff |
| `IOException` | Server unreachable | Notify user, retry |
| `SecurityException` | TLS/cert issue | Notify user, don't retry |
| `SocketTimeoutException` | Connection timeout | Retry with backoff |

### Reconnect strategy

1. **Immediate retry** — first failure
2. **Linear backoff** — subsequent failures (2s, 4s, 8s, ...)
3. **Max attempts** — give up after N attempts
4. **Manual reconnect** — user must trigger

## Anti-Patterns

```java
// WRONG: Blocking the main thread on network I/O
public void connect() {
    socket = SocketFactory.connect(config);  // Blocks!
    // UI frozen during connection
}

// RIGHT: Use callbacks or async pattern
public void connect() {
    SocketFactory.connect(config, new Callback() {
        public void onConnected(SocketConnection s) {
            // Handle on event thread
        }
        public void onError(Exception e) {
            // Handle error
        }
    });
}
```

```java
// WRONG: Not handling connection state properly
public void send(String message) {
    socket.send(message);  // May throw if disconnected!
}

// RIGHT: Check state before sending
public void send(String message) {
    if (state != State.READY) {
        throw new IllegalStateException("Not connected");
    }
    socket.send(message);
}
```

```java
// WRONG: Infinite reconnect loop
public void reconnect() {
    while (true) {
        connect();
        Thread.sleep(1000);
    }
}

// RIGHT: Limited attempts with backoff
public void reconnect() {
    if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
        notifyDisconnected("Max attempts reached");
        return;
    }
    int delay = Math.min(BASE_DELAY * (1 << reconnectAttempts), MAX_DELAY);
    reconnectAttempts++;
    timer.schedule(new TimerTask() {
        public void run() { connect(); }
    }, delay);
}
```

```java
// WRONG: Not closing socket on disconnect
public void disconnect() {
    state = State.DISCONNECTED;
    // Socket still open!
}

// RIGHT: Always close socket
public void disconnect() {
    state = State.DISCONNECTED;
    if (socket != null) {
        try {
            socket.close();
        } catch (IOException e) {
            // Log but don't throw
        }
        socket = null;
    }
}
```

## Code Template: Adding a New Connection Feature

```java
// In ConnectionConfig.java
public class ConnectionConfig {
    // ... existing fields ...
    public boolean compression;  // New field
}

// In IrcConnection.java
private void enableCompression() {
    if (config.compression) {
        // Send COMPRESS command
        send("COMPRESS zlib");
    }
}

// In SocketFactory.java
public static void connect(ConnectionConfig config, Callback callback) {
    String url = "socket://" + config.host + ":" + config.port;
    SocketConnection conn = (SocketConnection) Connector.open(url);

    if (config.compression) {
        // Enable compression on socket
        conn.setSocketOption(SocketConnection.KEEPALIVE, 1);
    }

    callback.onConnected(conn);
}
```

## Testing Network Code

- Network classes compile against stubs but **cannot run on desktop**
- Test on emulator or physical device
- Use mock sockets for unit testing flood control logic
- Test reconnection with a local test server

## Troubleshooting

### Connection drops frequently
- Check `FloodQueue` settings (too aggressive?)
- Verify server supports IRCv3 capabilities
- Check network signal strength (mobile data)

### Slow message delivery
- Verify `FloodQueue` isn't throttling too much
- Check server ping/pong handling
- Review `IrcConnection` buffer sizes

### TLS handshake fails
- Verify server supports TLS 1.2+
- Check certificate validity
- Review `SocketFactory` TLS configuration

### Reconnection loop
- Check `MAX_RECONNECT_ATTEMPTS` is reasonable
- Verify backoff calculation
- Ensure `Disconnected` state is properly set
