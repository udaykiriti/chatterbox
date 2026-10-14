# Usage & Commands Reference

Complete guide to using Chatterbox: UI navigation, key mappings, commands, and features.

---

## Main Screen Layout

```
┌─────────────────────────────────┐
│ Chatterbox  [libera.chat 42ms]  │ ← Title: app + server + lag
├─────────────────────────────────┤
│ #general        ● 12            │ ← Channel tabs (● = unread)
│ #random         ▲ 3             │   (▲ = highlight/mention)
│ @pmuser         ■               │   (■ = private msg)
├─────────────────────────────────┤
│                                 │
│  <nick>  Hello world            │ ← Chat canvas (scrollable)
│  <nick2> Hi there               │
│  * nick3 waves                  │
│                                 │
├─────────────────────────────────┤
│ Topic: Welcome to #general      │ ← Topic header (toggle with 0)
├─────────────────────────────────┤
│ [Input field              ]     │ ← Inline input (focus with #)
│                                 │
├─────────────────────────────────┤
│ Menu                    Back    │ ← Softkeys: Left=Menu, Right=Back
└─────────────────────────────────┘
```

### Tab Indicators
| Symbol | Meaning |
|--------|---------|
| (none) | No activity |
| `●` | Unread messages |
| `▲` | Highlight/mention (your nick) |
| `■` | Private message |
| `+` | You have ops (@) |
| `%` | You have halfop |
| `~` | You are owner |

---

## Key Map (Nokia E63/E71/E72/C3)

### Navigation
| Key | Action |
|-----|--------|
| **↑ / ↓** | Scroll chat history (when input not focused) |
| **9 / 8** | Page up / Page down (9 lines at a time) |
| **← / →** | Switch channel tabs (when input not focused) |
| **# (Hash)** | Toggle focus: Input field  Chat scroll |
| **0** | Toggle topic header visibility |

### Input & Sending
| Key | Action |
|-----|--------|
| **\* (Star)** | Send message (or Green Call key) |
| **Green Call** | Send message (alternative) |
| **C / Clear** | Backspace in input |
| **← / →** (in input) | Move caret left/right |
| **Tab** | Nick completion (cycle through channel nicks) |
| **Long press \*** | Send `/me ` action prefix |

### Menus & Shortcuts
| Key | Action |
|-----|--------|
| **1** | Open nick list for current channel |
| **2** | Open command menu |
| **3** | Cycle channel (`/part` + `/join`) |
| **7** | Toggle timestamps on/off |
| **Left Softkey** | Context menu (channel/user specific) |
| **Right Softkey** | Back / Close dialog / Close PM tab |
| **Red Key / End Call** | Background app (keeps connection alive) |

### In Dialogs/Lists
| Key | Action |
|-----|--------|
| **↑ / ↓** | Navigate list |
| **Left Softkey** | Select / OK |
| **Right Softkey** | Cancel / Back |
| **\* (Star)** | Select (in some lists) |

---

## Commands Reference

All commands start with `/`. Type in input field and press `*` to send.

### Channel Management
| Command | Syntax | Description |
|---------|--------|-------------|
| `/join` | `/join <#channel> [key]` | Join channel (key optional) |
| `/part` | `/part [#channel] [reason]` | Leave current or specified channel |
| `/cycle` | `/cycle [#channel]` | Part and rejoin (refresh) |
| `/close` | `/close` | Close current tab (part channel / close PM) |
| `/topic` | `/topic [new topic]` | View or set channel topic |
| `/names` | `/names [#channel]` | List nicks in channel |
| `/list` | `/list [pattern]` | List channels (server dependent) |

### Private Messages
| Command | Syntax | Description |
|---------|--------|-------------|
| `/msg` | `/msg <nick> <message>` | Send private message |
| `/query` | `/query <nick>` | Open PM tab with nick |
| `/r` | `/r <message>` | Reply to last PM received |

### User Info & Interaction
| Command | Syntax | Description |
|---------|--------|-------------|
| `/whois` | `/whois <nick>` | Query user info |
| `/whowas` | `/whowas <nick>` | Query recently quit user |
| `/who` | `/who <mask>` | List users matching mask |
| `/kick` | `/kick <#channel> <nick> [reason]` | Kick user (requires ops) |
| `/mode` | `/mode <#channel> [+/-modes] [args]` | Set channel modes |
| `/invite` | `/invite <nick> <#channel>` | Invite to channel |

### Nick & Identity
| Command | Syntax | Description |
|---------|--------|-------------|
| `/nick` | `/nick <newnick>` | Change nickname |
| `/away` | `/away [reason]` | Set away status |
| `/back` | `/back` | Remove away status |
| `/me` | `/me <action>` | Send CTCP ACTION |

### Settings & Preferences
| Command | Syntax | Description |
|---------|--------|-------------|
| `/set` | `/set [key] [value]` | View/set runtime options |
| `/save` | `/save` | Persist settings to RMS |
| `/reset` | `/reset` | Reset to defaults |
| `/profile` | `/profile <name>` | Switch profile |
| `/theme` | `/theme <Dark\|Light\|Auto>` | Change theme |

### Ignore & Highlights
| Command | Syntax | Description |
|---------|--------|-------------|
| `/ignore` | `/ignore [mask]` | List or add ignore mask |
| `/unignore` | `/unignore <mask>` | Remove ignore mask |
| `/highlight` | `/highlight [pattern]` | Manage highlight rules |

### Logging & History
| Command | Syntax | Description |
|---------|--------|-------------|
| `/log` | `/log [#channel]` | View scrollback log |
| `/clear` | `/clear` | Clear current channel buffer |
| `/history` | `/history` | Show input history (↑/↓ also works) |

### Connection
| Command | Syntax | Description |
|---------|--------|-------------|
| `/connect` | `/connect` | Connect using current profile |
| `/disconnect` | `/disconnect [reason]` | Disconnect cleanly |
| `/reconnect` | `/reconnect` | Force reconnect |
| `/quit` | `/quit [reason]` | Quit IRC (clean QUIT sent) |
| `/raw` | `/raw <command>` | Send raw IRC line |
| `/server` | `/server <host> [port]` | Quick connect to server |

### Help & Info
| Command | Syntax | Description |
|---------|--------|-------------|
| `/help` | `/help [command]` | Show help (all or specific) |
| `/version` | `/version` | Show client version |
| `/uptime` | `/uptime` | Show connection uptime |
| `/lag` | `/lag` | Show current lag |

---

## Nick Completion

### How It Works
1. Type first letters of a nick in channel: `joh`
2. Press **Tab** → completes to first match: `johnny`
3. Press **Tab** again → cycles to next: `johndoe`, `johnsmith`, etc.
4. Press **Shift+Tab** (or `*` + `Tab` on some keypads) → cycle backward

### Completion Rules
- **Source:** Current channel's nick list (sorted by last activity)
- **Case:** Preserves original case from nick list
- **Suffix:** Adds `:` if at start of line, space otherwise
- **PM tabs:** Completes from all known nicks across channels

### Example
```
Input: "joh[TAB]" → "johnny: "
Input: "joh[TAB][TAB]" → "johndoe: "
Input: "Hello joh[TAB]" → "Hello johnny "
```

---

## ️ Context Menus (Left Softkey)

### Channel Tab Menu
- **Join Channel** — `/join` dialog
- **Part Channel** — `/part` current
- **Cycle** — `/cycle` current
- **Channel Info** — `/names` + topic + modes
- **Nick List** — Open nick list (same as `1` key)
- **Clear Buffer** — `/clear`
- **View Log** — `/log`
- **Close Tab** — `/close`

### Nick List Menu (select nick first)
- **Query** — `/query` (open PM)
- **Whois** — `/whois`
- **Kick** — `/kick` (if ops)
- **Ban** — `/mode #chan +b nick!*@*`
- **Op/Deop** — `/mode #chan +o/-o nick`
- **Voice/Devoice** — `/mode #chan +v/-v nick`
- **Copy Nick** — Copy to clipboard (for paste in input)

### Private Message Tab Menu
- **Close** — Close PM tab
- **Whois** — `/whois`
- **Ignore** — Add to ignore list

### Main Screen Menu (no tab selected)
- **Settings** — Open settings
- **Profiles** — Switch profile
- **New Profile** — Create profile
- **Connect** — Connect current profile
- **Disconnect** — Disconnect
- **Reconnect** — Force reconnect
- **View Log** — Global log viewer
- **Highlights** — Manage highlight rules
- **Ignore List** — Manage ignore list
- **Help** — Command reference
- **About** — Version info
- **Quit** — Exit app (clean disconnect)

---

## Highlights & Notifications

### What Triggers Highlight
1. **Your nick** mentioned in channel (case-insensitive, IRC case mapping)
2. **Private message** received
3. **Custom patterns** (configured in Settings → Highlights)

### Visual Indicators
- **Channel tab:** `▲` badge (mention) or `■` (PM)
- **Nick list:** Highlighted nick in **bold/yellow**
- **Chat canvas:** Highlighted line in **yellow** (configurable)
- **Status bar:** "● 3 highlights" counter

### Audio/Haptic
- **Vibrate** + **Beep** on highlight (if enabled in settings)
- **Profile dependent:** Won't vibrate in Silent/Meeting profile
- **Configurable:** Settings → Vibrate on highlight

---

## Scrollback & History

### Chat Canvas Scrolling
- **Focus chat:** Press `#` to toggle from input to scroll
- **↑ / ↓** — Line by line
- **9 / 8** — Page up/down (9 lines)
- **New message** → Auto-scroll to bottom (if at bottom)
- **Scroll lock** — Scrolled up → stays, new msg shows badge

### Input History
- **In input field:** ↑ / ↓ recalls last 20 sent messages
- **`/history`** — Shows numbered list
- **Persisted** across sessions (RMS)

### Persistent Logs (RMS)
- **Per-channel:** Last 1000 lines saved to RMS
- **`/log #channel`** — View full log
- **`/log clear`** — Clear current channel log
- **Survives:** App restart, phone reboot

---

## Themes & Appearance

### Switching Themes
- **Settings → Theme:** Dark / Light / Auto
- **Command:** `/theme Dark`
- **Auto:** Switches at 06:00 (Light) and 18:00 (Dark)

### Timestamp Toggle
- **Key `7`** — Toggle timestamps on/off
- **Settings → Timestamps** — Persistent setting
- **Format:** `[HH:MM]` (24-hour)

### Topic Header
- **Key `0`** — Toggle topic bar visibility
- Shows: `Topic: <text> | Set by <nick> at <date>`

---

## Advanced Usage

### Raw IRC Commands
```
/raw PRIVMSG #channel :Hello
/raw MODE #channel +o nick
/raw WHOIS nick
```
Useful for unsupported commands or debugging.

### Multiple Server Windows
- Each **profile** = separate connection
- **`/profile <name>`** switches (disconnects current, connects new)
- **Settings → Profiles** for GUI switching

### CTCP Support
| CTCP | Sent By Client | Reply Sent |
|------|----------------|------------|
| **VERSION** | — | `Chatterbox 1.0 MIDP 2.0` |
| **ACTION** | `/me text` | — |
| **PING** | — | Timestamp echo |
| **TIME** | — | Local time |
| **FINGER** | — | `Chatterbox user` |

### URL Handling
- URLs in chat: `http://...` `https://...` `ftp://...`
- **Select URL** in nick list menu → **Open in Browser**
- Requires phone browser support

---

## Quick Reference Card

```
┌────────────────────────────────────────────────────────────┐
│ CHATTERBOX QUICK REFERENCE                                 │
├────────────────────────────────────────────────────────────┤
│ NAVIGATION                    INPUT                        │
│ ↑/↓     Scroll chat           * / Green    Send            │
│ 9/8     Page up/down          #            Toggle focus    │
│ ←/→     Switch tabs           Tab          Nick complete   │
│ 0       Toggle topic          C/Clear      Backspace       │
│ #       Focus toggle                                    │
├────────────────────────────────────────────────────────────┤
│ SHORTCUTS                  COMMON COMMANDS                 │
│ 1       Nick list           /join #chan                  │
│ 2       Command menu        /part [reason]               │
│ 3       Cycle channel       /msg nick text               │
│ 7       Toggle timestamps   /nick newnick                │
│ Left SK   Menu              /whois nick                  │
│ Right SK  Back/Close        /quit [reason]               │
│ Red Key   Background        /help [cmd]                  │
├────────────────────────────────────────────────────────────┤
│ TAB INDICATORS                                               │
│ (none)  Quiet          ▲  Highlight/mention                │
│ ●       Unread         ■  Private message                  │
│ + @     You're op      %  You're halfop                    │
└────────────────────────────────────────────────────────────┘
```

---

## 🆘 Common Tasks

### Join a Channel
```
/join #newchannel
# or with key:
/join #secretchannel password123
```

### Reply to Last PM
```
/r Thanks for the message!
```

### Change Nick Temporarily
```
/nick newnick
# If taken, retries with altnick automatically
```

### Mark All Read
- **Options → Mark All Read** (main menu)
- Or visit each tab with `←/→`

### Save Settings After Changes
```
/set timestamps false
/save
```

### Debug Connection Issues
```
/raw PING :test
/lag
/version
```

---

## Related Docs

- [Getting Started](getting-started.md) — First run walkthrough
- [Configuration](configuration.md) — All settings detailed
- [Installation](installation.md) — Device install methods
- [Gateway](gateway.md) — TLS relay setup
- [Architecture](architecture.md) — Command processing internals