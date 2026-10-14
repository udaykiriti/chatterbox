# Installing Chatterbox on Your Nokia Phone

Complete guide to transferring and installing Chatterbox on supported Nokia devices.

---

## Supported Installation Methods

| Method | Difficulty | Requires | Best For |
|--------|------------|----------|----------|
| **Bluetooth (OBEX)** |  Easy | PC with Bluetooth, paired phone | Most users |
| **Memory Card** |  Easy | microSD card + reader | No Bluetooth PC |
| **OTA (Web Download)** |  Medium | Web server with correct MIME types | Remote installs |
| **Nokia PC Suite / Ovi Suite** |  Easy | Windows PC, USB cable | Legacy Windows users |

---

## Required Files

From `ant dist` output:
- `dist/chatterbox.jar` — Application (~250-280 KB)
- `dist/chatterbox.jad` — Descriptor (~2 KB)

> **Both files are required.** The JAD tells the phone about the JAR (size, version, permissions).

---

## Method 1: Bluetooth (Recommended)

### Prerequisites
- PC with Bluetooth adapter
- Phone Bluetooth **On** and **Visible**
- Paired relationship established

### Steps

#### Linux (blueman / gnome-bluetooth / kdeconnect)
```bash
# 1. Pair phone in Bluetooth settings
# 2. Right-click chatterbox.jad → Send to Device → Select phone
# 3. Right-click chatterbox.jar → Send to Device → Select phone
# 4. On phone: Accept both transfers
# 5. Open from Inbox / Received files → Install
```

#### Windows 10/11
1. **Settings → Bluetooth & devices → Add device → Bluetooth** → Pair phone
2. **File Explorer → Right-click chatterbox.jad → Send to → Bluetooth device** → Select phone
3. Repeat for `chatterbox.jar`
4. On phone: **Accept** both → **Open** → **Install**

#### macOS
1. **System Settings → Bluetooth** → Pair phone
2. **Finder → Right-click chatterbox.jad → Share → Bluetooth** → Select phone
3. Repeat for `chatterbox.jar`
4. On phone: **Accept** both → **Open** → **Install**

### Phone Side (All OS)
- Files arrive in **Inbox** or **Received files** (Messaging app)
- Open `.jad` first → **Options → Install**
- App appears in **Applications** / **Games** / **Collection**

---

## Method 2: Memory Card

### Steps
```bash
# 1. Insert microSD into card reader
# 2. Mount on PC
# 3. Copy both files to card root or /Others/ folder
cp dist/chatterbox.jad /media/$USER/SDCARD/
cp dist/chatterbox.jar /media/$USER/SDCARD/

# 4. Safely eject, insert into phone
# 5. On phone: File Manager → Memory Card → chatterbox.jad → Options → Install
```

### Typical Paths by Device
| Device Series | Path |
|---------------|------|
| S60 3rd/5th Ed. | `E:/Others/` or `E:/` |
| S40 | `mass_memory/` or `E:/` |
| Symbian^3 (N8, E7, etc.) | `mass_memory/` |

> **Tip:** Create a folder `Chatterbox` on the card and keep both files there for easy updates.

---

## Method 3: OTA (Over-the-Air) Web Install

### Server Requirements
- Web server (nginx, Apache, Caddy, Python http.server)
- Correct MIME types **mandatory**

### MIME Type Configuration

#### nginx
```nginx
# /etc/nginx/mime.types or server block
types {
    text/vnd.sun.j2me.app-descriptor  jad;
    application/java-archive          jar;
}
```

#### Apache
```apache
# .htaccess or httpd.conf
AddType text/vnd.sun.j2me.app-descriptor .jad
AddType application/java-archive .jar
```

#### Caddy
```caddyfile
# Caddyfile
:80 {
    mime {
        .jad text/vnd.sun.j2me.app-descriptor
        .jar application/java-archive
    }
    file_server
}
```

#### Python (Quick Test Only)
```bash
# Serves with default MIME - may not work on all phones!
cd dist
python3 -m http.server 8000
# Then fix MIME types properly for production
```

### Deploy
```bash
# Copy to web root
sudo cp dist/chatterbox.jad dist/chatterbox.jar /var/www/html/

# Or custom location
scp dist/chatterbox.* user@server:/var/www/chatterbox/
```

### Install on Phone
1. Open **Browser** (not Opera Mini - use built-in Web/WAP browser)
2. Navigate to `http://yourserver.com/chatterbox.jad`
3. Phone prompts: **"Install application Chatterbox?"** → **Yes**
4. Downloads JAR automatically → **Install**

> **Critical:** Must open the `.jad` URL, not the `.jar`. The JAD triggers the MIDlet installer.

---

## ️ Method 4: Nokia PC Suite / Nokia Suite / Ovi Suite

### Windows Only (Legacy)

1. Install **Nokia Suite** (for newer) or **PC Suite** (for older)
2. Connect phone via **USB cable** in "PC Suite" / "Ovi Suite" mode
3. In software: **Tools → Install Applications** (or **Applications → Install**)
4. Browse to `dist/chatterbox.jad` → **Open**
5. Follow on-screen prompts

> **Note:** These tools are discontinued. Bluetooth or memory card are more reliable on modern Windows.

---

## First Launch & Setup

### Initial Screen: Settings
On first run (or after clearing RMS), the **Settings** screen opens automatically.

### Required Fields
| Field | Example | Notes |
|-------|---------|-------|
| **Server** | `irc.libera.chat` | IRC network hostname |
| **Port** | `6697` | TLS port (6667 for plain) |
| **Use TLS** |  Checked | Tries `ssl://` first |
| **Nick** | `mynick` | Primary nickname |
| **Alt nick** | `mynick_` | Fallback if taken |
| **User/Real name** | `Chatterbox User` | Sent in USER command |
| **Channels** | `#chan1,#chan2 key` | Comma-separated, space before key |

### Optional: Gateway Fields
| Field | Example | Notes |
|-------|---------|-------|
| **Gateway host** | `192.168.1.50` | Your gateway server IP |
| **Gateway port** | `6667` | Plain TCP port |
| **Gateway pass** | `shared-secret` | Must match gateway config |

### Save & Connect
1. **Left Softkey → Options → Save**
2. **Left Softkey → Options → Connect**

---

## Signing & Certificate Issues

### The TLS Problem
Nokia S60 requires **Trusted/Manufacturer** certificate for `ssl://` (TLS) sockets.
- **Self-signed** → "Security error" on `ssl://` connect
- **Workaround:** Use gateway (plain `socket://` to gateway, gateway does TLS)

### Signing Options (for direct TLS)

| Option | Cost | Difficulty | Status |
|--------|------|------------|--------|
| Self-signed | Free | Easy |  Blocks `ssl://` |
| Thawte/VeriSign MIDlet cert | $$$ | Medium |  Discontinued |
| Symbian Signed (Open Signed) | Free | Medium |  Service dead |
| **Dev cert (makecert + signtool)** | Free | Hard |  Works |
| **Gateway (recommended)** | VPS cost | Easy |  Works |

### Creating a Developer Certificate (Advanced)
```bash
# Requires Nokia S60 SDK tools
# 1. Create CSR
makecert -csr -o my.csr -k my.key -cn "My Cert" -ou "Dev" -o "Me" -c "US"

# 2. Get it signed (requires Symbian Signed account - no longer available)
# 3. Install .cer on phone (Bluetooth/email)
# 4. Sign JAR
signtool sign -keystore my.keystore -alias myalias -storepass storepass -keypass keypass dist/chatterbox.jar
```

> **Strong recommendation:** Skip signing complexity. Use the **gateway** for TLS.

---

## Post-Install Verification

### Checklist
- [ ] App launches without "Security error"
- [ ] Settings screen appears (first run) or loads saved profile
- [ ] Connect → Shows "Connecting..." → "Connected"
- [ ] Status bar shows server name + lag (e.g., "libera.chat 42ms")
- [ ] Channels auto-join, nick lists populate
- [ ] Send test message → Appears in channel
- [ ] Receive message → Badge on tab, vibrate/beep
- [ ] Background (red key) → Resume → Still connected
- [ ] Quit → Clean disconnect, settings persist

---

## Updating Chatterbox

### Same Method as Install
- **Bluetooth:** Send new JAD+JAR → Install (overwrites)
- **Memory Card:** Copy new files → Open JAD → Install
- **OTA:** Replace files on server → Open JAD URL → Update

### RMS Data Preservation
- **Settings, logs, ignore list, highlights** stored in RMS (RecordStore)
- **Survives update** — only JAR/JAD replaced
- **To reset:** Settings → Options → Clear Settings (or uninstall + reinstall)

---

## ️ Uninstalling

| Platform | Steps |
|----------|-------|
| **S60** | Menu → Applications → Chatterbox → Options → Uninstall |
| **S40** | Menu → Games/Collection → Chatterbox → Options → Delete |
| **All** | Also deletes RMS data (settings, logs) |

---

## 🆘 Installation Troubleshooting

| Error | Cause | Fix |
|-------|-------|-----|
| "Invalid JAD" | Wrong MIME type (OTA) | Fix server MIME types |
| "Jar size mismatch" | JAD size ≠ actual JAR | Rebuild: `ant clean dist` |
| "Certificate error" | Self-signed + direct TLS | Use gateway or dev cert |
| "Out of memory" | Phone heap full | Close other apps, restart phone |
| "Feature not supported" | MIDP/CLDC version too low | Requires MIDP 2.0 / CLDC 1.1 |
| "Install failed" (Bluetooth) | Transfer incomplete | Retry, keep phone awake |
| App not in menu | Installed to wrong folder | Check Applications/Games/Collection |

---

## Related Docs

- [Getting Started](getting-started.md) — Quick start overview
- [Configuration](configuration.md) — Detailed settings reference
- [Gateway](gateway.md) — Set up TLS relay server
- [Usage](usage.md) — Commands and key mappings