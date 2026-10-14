# Configuration Guide

Complete reference for all Chatterbox settings, profiles, and customization options.

---

## Settings Screen Fields

Access via **Settings** (first run) or **Options → Settings** from main screen.

### Connection Tab

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| **Profile name** | Text | `default` | RMS record store name (multiple profiles supported) |
| **Server** | Text | `irc.libera.chat` | IRC server hostname or IP |
| **Port** | Number | `6697` | Server port (6697 TLS, 6667 plain) |
| **Use TLS** | Checkbox |  On | Try `ssl://` first; fallback to gateway/plain |
| **Server password** | Text | *(empty)* | PASS sent to server (optional) |
| **Nick** | Text | `user` | Primary nickname (max 31 chars) |
| **Alt nick** | Text | `user_` | Fallback if primary taken |
| **Username** | Text | `chatterbox` | USER command username |
| **Real name** | Text | `Chatterbox User` | USER command real name (GECOS) |
| **Channels** | Text | *(empty)* | Auto-join on connect: `#chan1,#chan2 key` |

### Gateway Tab (TLS Relay)

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| **Gateway host** | Text | *(empty)* | Your gateway server IP/hostname |
| **Gateway port** | Number | `6667` | Gateway plain TCP port |
| **Gateway password** | Text | *(empty)* | Shared secret (must match `GATEWAY_PASSWORD`) |
| **Use gateway** | Checkbox | Auto |  = only on TLS failure,  = always use gateway |

### Advanced Tab

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| **Reconnect** | Checkbox |  On | Auto-reconnect with exponential backoff |
| **Keepalive (s)** | Number | `90` | PING interval (0 = disable) |
| **Flood protection** | Checkbox |  On | Client-side message queue (2 msg/2s) |
| **Scrollback lines** | Number | `200` | Per-channel ring buffer size (50-500) |
| **Timestamps** | Checkbox |  On | Show `[HH:MM]` in chat |
| **Theme** | Choice | `Auto` | `Dark` / `Light` / `Auto` (by time) |
| **Vibrate on highlight** | Checkbox |  On | Vibrate + beep on mention/PM |
| **Confirm quit** | Checkbox |  On | Prompt before disconnecting |

---

## ️ Multiple Profiles

Chatterbox stores each profile as a separate RMS record store.

### Creating Profiles
1. **Settings → Options → New Profile**
2. Enter profile name (e.g., `libera`, `oftc`, `work`)
3. Fill settings → **Save**
4. **Options → Profiles** to switch

### Profile Storage
- **RMS Record Store:** `chatterbox-profile-<name>`
- **Per-profile:** Server, nick, channels, gateway, UI prefs
- **Shared:** Ignore list, highlight rules, scrollback logs (global)

### Profile Switching
- **Main screen → Options → Profiles → Select**
- Disconnects current → Loads new → Auto-connects
- Quick switch: **`/profile <name>`** command

---

## Command-Line Style Config (Advanced)

For power users, settings can be pre-configured via JAD properties (OTA deploy):

### `chatterbox.jad` Custom Properties
```jad
MIDlet-Name: Chatterbox
MIDlet-Version: 1.0.0
MIDlet-Vendor: Chatterbox Project
MIDlet-Jar-URL: chatterbox.jar
MIDlet-Jar-Size: 262144
MIDlet-1: Chatterbox,/res/icon.png,com.chatterbox.ui.ChatterboxMIDlet
MicroEdition-Profile: MIDP-2.0
MicroEdition-Configuration: CLDC-1.1

# Custom config (read on first run if RMS empty)
Chatterbox-Default-Server: irc.libera.chat
Chatterbox-Default-Port: 6697
Chatterbox-Default-TLS: true
Chatterbox-Default-Nick: mynick
Chatterbox-Default-Channels: #general,#random
Chatterbox-Gateway-Host: gateway.example.com
Chatterbox-Gateway-Port: 6667
Chatterbox-Gateway-Pass: shared-secret
```

> **Note:** These only apply on **first run** (empty RMS). User changes override.

---

## Themes

### Built-in Themes
| Theme | Background | Text | Timestamp | Own Msg | Highlight | System Msg |
|-------|------------|------|-----------|---------|-----------|------------|
| **Dark** | `#000000` | `#FFFFFF` | `#888888` | `#00FF00` | `#FFFF00` | `#FFAA00` |
| **Light** | `#FFFFFF` | `#000000` | `#888888` | `#0000FF` | `#FF0000` | `#AA5500` |
| **Auto** | Time-based | | | | | |

### Auto Theme Schedule
- **06:00–18:00** → Light
- **18:00–06:00** → Dark

### Customizing Colors (Source Code)
Edit `midlet/src/com/chatterbox/ui/ChatView.java`:
```java
// Dark theme colors
private static final int COLOR_BG_DARK = 0x000000;
private static final int COLOR_TEXT_DARK = 0xFFFFFF;
private static final int COLOR_TS_DARK = 0x888888;
private static final int COLOR_OWN_DARK = 0x00FF00;
private static final int COLOR_HILITE_DARK = 0xFFFF00;
private static final int COLOR_SYS_DARK = 0xFFAA00;

// Light theme colors
private static final int COLOR_BG_LIGHT = 0xFFFFFF;
// ...
```

Recompile: `ant clean dist`

---

## Highlight Rules

Configure what triggers highlight badge + vibrate/beep.

### Default Rules
- **Own nick** (exact match, case-insensitive per IRC case mapping)
- **Private messages** (always highlight)

### Custom Rules
**Settings → Highlights → Options → Add**
- **Pattern:** Text or regex (Java regex syntax)
- **Case sensitive:**  / 
- **Match type:** `Contains` / `Word boundary` / `Regex`
- **Sound:** `Default` / `Beep` / `Vibrate` / `Both` / `None`

### Examples
| Pattern | Type | Matches |
|---------|------|---------|
| `deploy` | Contains | "deploy", "deployment", "redeploy" |
| `\bdeploy\b` | Regex | "deploy" as whole word |
| `(?i)urgent` | Regex | "urgent", "URGENT", "Urgent" |
| `@mynick` | Contains | Mentions with @ prefix |

---

## Ignore List

Block messages from specific nicks/hostmasks.

### Managing
**Settings → Ignore List → Options → Add**
- **Mask:** `nick!user@host` (wildcards `*` `?` allowed)
- **Type:** `All` / `Channel` / `Private` / `CTCP`

### Examples
| Mask | Blocks |
|------|--------|
| `spammer!*@*` | All from spammer |
| `*!*@*.badisp.com` | All from badisp.com |
| `bot*!*@*` | Nicks starting with "bot" |
| `annoying!~user@host` | Specific user@host |

### Commands
- `/ignore <mask>` — Add to ignore list
- `/unignore <mask>` — Remove
- `/ignore` — List all

---

## Scrollback & Logs

### In-Memory Ring Buffer
- **Per-channel:** 200 lines default (configurable 50-500)
- **Survives:** Channel switch, background, reconnect
- **Lost on:** App exit, `/clear`, profile switch

### Persistent RMS Log
- **Enabled by default**
- **File:** RMS record store `chatterbox-log-<server>`
- **Format:** Timestamp + channel + sender + message
- **Retention:** Last 1000 lines per channel
- **View:** `/log <channel>` or **Options → View Log**

### Log Commands
- `/log` — View current channel log
- `/log #channel` — View specific channel
- `/log clear` — Clear current channel log
- `/log clearall` — Clear all logs

---

## ️ Input & Completion Settings

### Nick Completion
- **Trigger:** `Tab` key (cycles through nicks in channel)
- **Prefix:** `@` optional (configurable)
- **Sort:** By last activity (most recent first)

### Input Behavior
| Setting | Default | Description |
|---------|---------|-------------|
| **Send key** | `*` (Star) / Green Call | Configurable in source |
| **Toggle focus** | `#` (Hash) | Switch input  scroll |
| **History** | ↑/↓ in input | Recall last 20 sent messages |
| **Max input** | 400 chars | IRC protocol limit |

---

## Reconnect & Backoff

### Exponential Backoff Algorithm
```
Attempt 1: 2 seconds
Attempt 2: 4 seconds
Attempt 3: 8 seconds
Attempt 4: 16 seconds
Attempt 5: 32 seconds
Attempt 6+: 60 seconds (capped)
```

### Triggers
- Connection lost (network change, signal loss)
- Server disconnect (ERROR, ping timeout)
- App backgrounded > 30s (configurable)

### Manual Reconnect
- **Options → Reconnect** (main screen)
- **`/reconnect`** command
- Auto on resume if enabled

---

## Device-Specific Notes

### S60 3rd Edition (E63, E71, E72, E90, N95, etc.)
- Full canvas support, vibrate API works
- Softkeys: Left=Menu, Right=Back
- Red key = background app

### S60 5th Edition (5800, N97, X6, etc.)
- Touch + keypad both work
- Virtual keyboard appears on text fields
- Canvas touch: tap = focus input, drag = scroll

### Series 40 (C3, X3, C5, E5, etc.)
- No vibrate API (silent on highlight)
- Smaller heap (~1-2 MB) — lower scrollback recommended
- No background execution — disconnects on red key

---

## Runtime Configuration Commands

| Command | Description |
|---------|-------------|
| `/set <key> <value>` | Set runtime option |
| `/set` | List all options |
| `/save` | Persist current settings to RMS |
| `/reset` | Reset to defaults (keeps profiles) |

### Settable Keys
```
server, port, tls, nick, altnick, user, realname, channels
gateway_host, gateway_port, gateway_pass, use_gateway
reconnect, keepalive, floodprot, scrollback, timestamps
theme, vibrate, confirm_quit
```

Example:
```
/set scrollback 300
/set theme Dark
/save
```

---

## Related Docs

- [Getting Started](getting-started.md) — First-run setup
- [Installation](installation.md) — Installing on device
- [Gateway](gateway.md) — Gateway server configuration
- [Usage](usage.md) — Commands and key mappings
- [Architecture](architecture.md) — Settings storage internals