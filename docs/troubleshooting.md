# Troubleshooting Guide

Solutions to common issues with Chatterbox on device, emulator, gateway, and build.

---

## Device Issues

### "Security Error" on Connect

**Symptom:** App shows "Security error" or "Permission denied" when connecting with TLS.

**Cause:** Nokia S60 requires a Trusted/Manufacturer certificate for `ssl://` sockets. Self-signed certs are blocked.

**Solutions:**
1. **Use the gateway (recommended)** — Configure gateway in Settings, disable direct TLS
2. **Developer certificate** — Create with Nokia SDK `makecert`/`signtool`, install `.cer` on phone
3. **Plain port** — Connect to port 6667 without TLS (if network supports)

```bash
# Gateway setup (quickest fix)
# Server: your.vps.ip
# Port: 6667
# Gateway: enabled with password
# Use TLS: unchecked (or checked for auto-fallback)
```

### "Invalid JAD" / "JAR Size Mismatch"

**Symptom:** Install fails with "Invalid JAD" or "Jar size mismatch".

**Cause:** JAD file `MIDlet-Jar-Size` doesn't match actual JAR bytes.

**Fix:**
```bash
ant clean dist  # Rebuilds both with correct size
# Verify:
ls -l dist/chatterbox.jar
grep MIDlet-Jar-Size dist/chatterbox.jad
```

### App Won't Install / "Corrupt JAR"

**Symptom:** "Application not valid", "Corrupt JAR", or install silently fails.

**Causes & Fixes:**
| Cause | Fix |
|-------|-----|
| Transfer corrupted (Bluetooth) | Re-send, keep phone awake, don't interrupt |
| JAD MIME type wrong (OTA) | Server must send `text/vnd.sun.j2me.app-descriptor` |
| JAR > phone heap limit | Ensure < 300 KB (ProGuard), try debug build |
| Wrong CLDC/MIDP version | Requires MIDP 2.0 / CLDC 1.1 |
| Duplicate install | Uninstall first: Menu → Apps → Chatterbox → Options → Uninstall |

### Out of Memory / App Crashes

**Symptom:** "Out of memory", app closes unexpectedly, phone slows down.

**Fixes:**
1. **Reduce scrollback:** Settings → Scrollback lines → 100 (default 200)
2. **Close other apps:** Red key → close browser, maps, etc.
3. **Restart phone:** Clears heap fragmentation
4. **Clear logs:** `/log clearall` or Settings → Clear Logs

### Connection Drops Frequently

**Symptom:** Disconnects every few minutes, reconnects automatically.

**Causes & Fixes:**
| Cause | Fix |
|-------|-----|
| NAT timeout (router) | Gateway `GATEWAY_IDLE_TIMEOUT=120`, phone keepalive 90s |
| Phone sleep mode | Disable "Power saver" or keep app in foreground |
| 2G/3G signal loss | Enable Reconnect (default), increase backoff cap |
| Server ping timeout | Increase keepalive: Settings → Keepalive → 120 |

### Vibrate/Beep Not Working

**Symptom:** No vibration or sound on highlight/PM.

**Fixes:**
1. **Profile check:** Not in Silent/Meeting/Offline profile
2. **Settings:** Settings → Vibrate on highlight → 
3. **Device support:** S40 devices lack vibrate API (silent only)
4. **Volume:** Media volume up (not ringtone)

### Non-Latin Characters Broken

**Symptom:** Cyrillic/Greek/CJK shows as boxes or garbage.

**Fixes:**
1. **Font support:** Device must have font for script
2. **Encoding:** Server must send UTF-8 (modern networks do)
3. **Workaround:** `/set encoding utf-8` (if supported)

---

## ️ Emulator Issues

### WTK Emulator Won't Start

**Symptom:** `emulator` command not found or fails.

**Fixes:**
```bash
# Check WTK_HOME
echo $WTK_HOME
# Should be: /opt/WTK2.5.2

# Check binary
ls $WTK_HOME/bin/emulator

# Run directly
$WTK_HOME/bin/emulator
```

### "Preverify Failed" / Class Format Error

**Symptom:** Build fails at `preverify` step.

**Causes:**
| Cause | Fix |
|-------|-----|
| JDK too new (class file version) | Use `-source 1.5 -target 1.5` (build.xml does this) |
| WTK version mismatch | Must be WTK 2.5.2 exactly |
| ProGuard ran before preverify | Order: compile → preverify → proguard |

### Network Not Working in Emulator

**Symptom:** Can't connect to IRC/gateway from emulator.

**Fixes:**
1. **WTK:** Uses host network directly — check firewall
2. **S60 SDK:** Set networking to "Ethernet" or "Loopback" in SDK preferences
3. **Gateway:** Test with `telnet localhost 6667` from host

### RMS Not Persisting in Emulator

**Symptom:** Settings lost on emulator restart.

**Fix:** WTK stores RMS in `$WTK_HOME/appdb/` — don't delete this folder. Use `ant install-emu` not manual JAD install.

---

## Gateway Issues

### Gateway Won't Start

**Symptom:** `systemctl status chatterbox-gateway` shows failed.

**Debug:**
```bash
# Check logs
sudo journalctl -u chatterbox-gateway -n 50

# Common errors:
# - Address already in use → change GATEWAY_LISTEN_PORT
# - Permission denied → port < 1024 needs root (use 6667)
# - Config missing → check /etc/chatterbox-gateway.env
```

### Client Connects But No Relay

**Symptom:** Phone connects to gateway, but no IRC traffic flows.

**Debug:**
```bash
# Check gateway logs
sudo journalctl -u chatterbox-gateway -f

# Should see:
# Client connected: <phone-ip>
# TLS established to irc.libera.chat:6697
# Relay started

# If missing "TLS established":
# - Check GATEWAY_IRC_HOST/PORT reachable from VPS
# - Test: openssl s_client -connect irc.libera.chat:6697
```

### "Authentication Failed" in Gateway Logs

**Symptom:** Gateway logs "Authentication failed from <ip>".

**Cause:** Phone gateway password ≠ `GATEWAY_PASSWORD`.

**Fix:**
```bash
# Verify match
# Phone Settings → Gateway pass: "mysecret"
# Server /etc/chatterbox-gateway.env: GATEWAY_PASSWORD=mysecret
```

### Rate Limiting Too Aggressive

**Symptom:** Messages dropped during busy conversation.

**Fix:**
```bash
# Increase limit
GATEWAY_RATE_LIMIT=20
# Or disable (not recommended for public)
GATEWAY_RATE_LIMIT=0

# Restart
sudo systemctl restart chatterbox-gateway
```

### TLS Verification Fails

**Symptom:** Gateway logs TLS handshake error to IRC server.

**Fix:**
```bash
# For self-signed IRC certs (rare)
GATEWAY_TLS_VERIFY=false

# Normal: leave true (default)
GATEWAY_TLS_VERIFY=true
```

---

## ️ Build Issues

### WTK_HOME Not Set

**Error:** `WTK_HOME not set` or `preverify not found`

**Fix:**
```bash
export WTK_HOME=/opt/WTK2.5.2
# Add to ~/.bashrc for persistence
echo 'export WTK_HOME=/opt/WTK2.5.2' >> ~/.bashrc
```

### ProGuard Not Found / JAR Too Large

**Symptom:** `dist/chatterbox.jar` > 500 KB, ProGuard step skipped.

**Fix:**
```bash
export PROGUARD_HOME=/opt/proguard7.4
# Verify
ls $PROGUARD_HOME/lib/proguard.jar

# Check build output for "proguard" task
ant dist 2>&1 | grep -i proguard
```

### "Cannot Find Symbol" for MIDP Classes

**Error:** Compilation fails on `javax.microedition.*` classes.

**Cause:** WTK libs not in classpath.

**Fix:**
```bash
# Check build.xml has correct paths
grep midpapi build.xml
# Should reference ${wtk.lib}/midpapi20.jar

# Verify WTK_HOME/lib contents
ls $WTK_HOME/lib/
# midpapi20.jar, cldcapi11.jar must exist
```

### Signing Fails

**Error:** `ant sign` fails with keystore errors.

**Fix:**
```bash
# All 4 env vars required
export KEYSTORE=/path/to/keystore
export KEY_ALIAS=myalias
export KEYSTORE_PASS=storepass
export KEY_PASS=keypass

# Verify keystore
keytool -list -keystore $KEYSTORE -storepass $KEYSTORE_PASS
```

---

## Runtime Commands for Debugging

### Connection Diagnostics
```
/lag          # Shows current ping time
/version      # Client + server version
/uptime       # Connection duration
/raw PING :test  # Manual ping
```

### State Inspection
```
/set          # All runtime settings
/profile      # Current profile name
/ignore       # Active ignore masks
/highlight    # Active highlight rules
/log          # View scrollback
```

### Force Actions
```
/reconnect    # Force reconnect now
/save         # Persist settings to RMS
/clear        # Clear current channel buffer
```

---

## Diagnostic Checklist

### Before Reporting an Issue

| Check | Command/Action |
|-------|----------------|
| Build version | `/version` |
| Test suite | `ant test` (129 pass) |
| JAR size | `ls -lh dist/chatterbox.jar` (< 300 KB) |
| Gateway logs | `journalctl -u chatterbox-gateway -n 100` |
| Phone OS | S60 3rd/5th or S40 3rd/5th |
| Network | Wi-Fi vs 2G/3G |
| Certificate | Self-signed vs gateway |

### Information to Include in Bug Reports

1. **Device:** Model, firmware (e.g., `Nokia E71, RM-346, 400.21.013`)
2. **Network:** IRC network, port, TLS/gateway
3. **Steps to reproduce:** Exact sequence
4. **Logs:** Gateway logs, any error messages on phone
5. **Build:** `ant test` output, JAR size

---

## 🆘 Quick Fixes Summary

| Problem | Quick Fix |
|---------|-----------|
| Security error on TLS | Use gateway |
| Install fails | `ant clean dist`, check MIME types |
| Disconnects often | Enable reconnect, check NAT timeout |
| No vibrate | Check profile not Silent, settings enabled |
| JAR too big | Set `PROGUARD_HOME`, rebuild |
| Preverify fails | Use WTK 2.5.2, JDK 8-21 |
| Gateway won't start | Check port 6667 free, env vars set |
| Rate limited | Increase `GATEWAY_RATE_LIMIT` |
| Garbled text | Ensure UTF-8, check device font support |

---

## Getting Help

1. **Check logs first** — Gateway: `journalctl`, Phone: RMS log (`/log`)
2. **Search issues** — GitHub Issues (open/closed)
3. **IRC:** `#chatterbox` on Libera.Chat
4. **Create issue:** Include diagnostic checklist above

---

## Related Docs

- [Installation](installation.md) — Install methods
- [Configuration](configuration.md) — Settings reference
- [Gateway](gateway.md) — Gateway setup & config
- [Building](building.md) — Build troubleshooting
- [Security](security.md) — Certificate & TLS details