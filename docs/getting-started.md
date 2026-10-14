# Getting Started with Chatterbox

This guide walks you through getting Chatterbox running on your Nokia phone in the shortest time possible.

---

## Prerequisites

### On Your Computer
- **Java JDK 8–21** — for building
- **Apache Ant 1.10+** — build orchestration
- **Sun WTK 2.5.2** or **Nokia S60 SDK** — for `preverify` and emulator (required for device JAR)
- **ProGuard 7.x+** — optional but recommended for size optimization

### On Your Phone
- Nokia S60 3rd/5th Ed. or S40 3rd/5th Ed. with MIDP 2.0
- ~500 KB free storage (for JAR + JAD + RMS)
- Wi-Fi or 2G/3G data connection

---

## 5-Minute Quick Start

### 1. Clone & Build
```bash
git clone https://github.com/your-repo/chatterbox.git
cd chatterbox

# Set WTK path (adjust for your system)
export WTK_HOME=/opt/WTK2.5.2

# Build release JAR + JAD
ant dist
```

**Output:** `dist/chatterbox.jar` + `dist/chatterbox.jad`

### 2. Transfer to Phone (Choose One)

| Method | Steps |
|--------|-------|
| **Bluetooth** | Pair phone  PC → Send both files via OBEX → Open from Inbox |
| **Memory Card** | Copy to `E:/Others/` or `mass_memory/` → Open in File Manager |
| **OTA (Web)** | Host files on web server with correct MIME types → Open JAD URL in browser |

### 3. First Run Configuration

On first launch, the **Settings** screen appears automatically. Fill in:

```
Server:       irc.libera.chat
Port:         6697
Use TLS:       (checked)
Nick:         yournick
Alt nick:     yournick_
Channels:     #channel1,#channel2
Real name:    Chatterbox User
```

**Optional — Gateway (recommended for TLS issues):**
```
Gateway host: your.server.ip
Gateway port: 6667
Gateway pass: your-shared-secret
```

Press **Options → Save**, then **Options → Connect**.

---

## Connection Flow

```
┌─────────────┐     ssl://:6697     ┌──────────────┐
│  Chatterbox │ ──────────────────► │ irc.libera.chat │
│   (Phone)   │   (Direct TLS)      │   (IRC Network) │
└─────────────┘                     └──────────────┘
       │
       │ ssl:// fails (cert issue)
       ▼
┌─────────────┐     socket://:6667   ┌──────────────┐     TLS      ┌──────────────┐
│  Chatterbox │ ──────────────────► │   Gateway    │ ───────────► │ irc.libera.chat │
│   (Phone)   │   (Plain TCP)       │  (Your VPS)  │              │   (IRC Network) │
└─────────────┘                     └──────────────┘              └──────────────┘
```

The MIDlet **tries direct TLS first**. Only if that fails does it fall back to the gateway.

---

## Verify It Works

1. **Connected** — Status shows "Connected" + server welcome (001 numeric)
2. **Auto-joined** — Channels from settings appear with nick lists
3. **Send a message** — Press `*` (Star) or Green Call key to send
4. **Receive a message** — Ask someone to ping you; badge appears on channel tab
5. **Nick completion** — Type first letters of a nick + `Tab` to cycle

---

## 🆘 If Something Goes Wrong

| Symptom | Likely Cause | Fix |
|---------|--------------|-----|
| "Security error" on connect | Device TLS cert blocked | Use gateway (see above) |
| "Connection refused" | Wrong host/port or firewall | Check server:port, try 6667 plain |
| "Invalid JAD" | MIME types wrong (OTA) | Server must send `text/vnd.sun.j2me.app-descriptor` |
| App won't install | Self-signed on S60 | Sign with dev cert or use gateway |
| No channels joined | Channels field empty | Add comma-separated channels in Settings |

See [Troubleshooting](troubleshooting.md) for more.

---

## Next Steps

- Learn the [Key Map](usage.md#key-map-nokia-e63e71e72c3)
- Master [Commands](usage.md#commands-reference)
- Set up your own [Gateway](gateway.md) for reliable TLS
- Read [Configuration](configuration.md) for advanced options

---

## Community

- **IRC:** `#chatterbox` on Libera.Chat
- **Issues:** GitHub Issues
- **Wiki:** This documentation

*Happy chatting on your classic Nokia!* 