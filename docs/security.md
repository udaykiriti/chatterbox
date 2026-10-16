# Security Notes

Threat model, TLS handling, certificate management, and security considerations for Chatterbox.

---

## Threat Model

### Assets to Protect
| Asset | Sensitivity | Threat |
|-------|-------------|--------|
| **IRC credentials** (NickServ password) | High | Theft → account takeover |
| **Gateway password** | High | Theft → gateway abuse |
| **Private messages** | Medium | Eavesdropping |
| **Channel history** | Low | Local device access |
| **Device identity** | Low | Tracking |

### Trust Boundaries
```
┌─────────────────────────────────────────────────────────────────┐
│                        TRUSTED                                  │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐         │
│  │  Chatterbox │    │   Gateway   │    │  IRC Server │         │
│  │   (Phone)   │    │  (Your VPS) │    │ (libera.chat)│        │
│  └──────┬──────┘    └──────┬──────┘    └──────┬──────┘         │
└─────────│──────────────────│──────────────────│────────────────┘
          │                  │                  │
    ┌─────▼─────┐      ┌─────▼─────┐      ┌─────▼─────┐
    │  UNTRUSTED │      │  TRUSTED  │      │  TRUSTED  │
    │  Network   │      │  (You)    │      │  (IRC Net)│
    └───────────┘      └───────────┘      └───────────┘
```

### Attack Vectors & Mitigations

| Vector | Risk | Mitigation |
|--------|------|------------|
| **MITM on phonegateway** | Medium | Gateway password (shared secret), TLS to IRC |
| **MITM on gatewayIRC** | Low | TLS 1.2+, cert verification (default) |
| **Malicious IRC server** | Low | No client-side exec, input sanitized |
| **RMS data theft** | Low | Phone lock code, no sensitive data in RMS |
| **Gateway compromise** | Medium | Isolated VPS, minimal attack surface |
| **Phishing (fake gateway)** | Medium | Verify gateway password, host key |

---

## TLS & Certificate Handling

### Direct TLS (Phone → IRC Server)

**How it works:**
1. MIDlet opens `ssl://irc.libera.chat:6697`
2. Phone JSSE validates server certificate against **built-in root CAs**
3. If valid → encrypted connection established
4. If invalid → "Security error", connection fails

**Limitations on Nokia S60:**
- **Root CA store:** Fixed at manufacture (~2006-2010 vintage)
- **Missing modern CAs:** Let's Encrypt, ISRG Root X1, etc.
- **Result:** Most modern IRC networks **fail** direct TLS on device

**Workarounds:**
1. **Gateway (recommended)** — Phone uses plain TCP to your VPS, VPS does modern TLS
2. **Developer certificate** — Sign MIDlet with trusted cert (complex, see below)
3. **Plain port** — Connect to 6667 unencrypted (not recommended)

### Gateway TLS (Gateway → IRC Server)

**How it works:**
1. Gateway (Python `ssl` module) connects to `irc.libera.chat:6697`
2. Uses **system CA store** (updated via OS packages)
3. Validates hostname (SNI), certificate chain, expiration
4. `GATEWAY_TLS_VERIFY=true` (default) — enforces validation

**Configuration:**
```bash
# Strict (default, recommended)
GATEWAY_TLS_VERIFY=true

# Permissive (self-signed IRC certs only)
GATEWAY_TLS_VERIFY=false
```

### Certificate Pinning

Implemented in `gateway/server.py` — two complementary mechanisms:

1. **Pinned root CA** — `GATEWAY_TLS_CA=/path/to/ca.pem`:
   the TLS context trusts *only* this CA and keeps hostname checking on.
   This takes precedence over `GATEWAY_TLS_VERIFY=false`.
2. **Pinned certificate fingerprint** — `GATEWAY_TLS_SHA256=<64 hex chars>`:
   after the handshake the gateway hashes the served certificate's DER bytes
   and closes the connection on any mismatch. Get the value from the server:

```bash
openssl s_client -connect irc.libera.chat:6697 < /dev/null 2>/dev/null \
  | openssl x509 -outform DER | sha256sum
```

Both may be combined for defense in depth:

```python
# Environment
GATEWAY_TLS_CA=/etc/chatterbox/libera-ca.pem
GATEWAY_TLS_SHA256=4f8b...dc91
```

---

## Gateway Authentication

### Shared Secret Model

```
Phone                          Gateway
  │                               │
  ├─ PASS <secret> ──────────────►│  Verify
  │                               │
  │◄── :gateway NOTICE * :OK ────┤
  │                               │
  ├─ NICK/USER ─────────────────►│  Relay to IRC
  │                               │
```

**Security Properties:**
- **Password sent in plaintext** over phonegateway link
- **Mitigation:** Gateway on VPS you control, phone on your network/VPN
- **Not a cryptographic auth** — just access control

### Strengthening Gateway Auth

#### Option 1: IP Allowlist (Firewall)
```bash
# Only your phone's IP range
sudo ufw allow from 203.0.113.0/24 to any port 6667
```

#### Option 2: VPN (WireGuard/Tailscale)
```bash
# Gateway binds to VPN interface only
GATEWAY_LISTEN_HOST=100.64.0.1  # Tailscale IP
# Phone connects via VPN
```

#### Option 3: TLS Client Certs (Advanced)
```python
# Gateway requires client cert
context.verify_mode = ssl.CERT_REQUIRED
context.load_verify_locations(cafile="client-ca.pem")
# Phone needs cert installed (complex on J2ME)
```

---

## Device-Side Security

### What's Stored in RMS (RecordStore)

| Store | Contains | Sensitivity |
|-------|----------|-------------|
| `chatterbox-profile-*` | Server, nick, **gateway password**, channels | Medium |
| `chatterbox-settings` | UI prefs, highlights, ignore list | Low |
| `chatterbox-log-*` | Chat history (last 1000 lines/channel) | Low |
| `chatterbox-ignore` | Ignore masks | Low |

**Note:** NickServ/ChanServ passwords are **NOT stored** — sent only at connect via `ServiceIdentify`.

### Protecting Device Data

1. **Phone lock code** — Prevents physical access to RMS
2. **No sensitive data in logs** — Passwords masked in `/log`
3. **Remote wipe** — Uninstall app → RMS deleted

### Avoiding Credential Leaks

```java
// GOOD: Password only in memory, sent once at connect
connection.send("PASS " + config.gatewayPassword);

// BAD: Never log passwords
System.out.println("PASS " + password);  // NEVER DO THIS
```

---

## ️ Input Sanitization

### Protocol Parser Hardening

```java
// ProtocolParser.parse() - never throws, handles malformed
public static IrcMessage parse(String line) {
    // 1. Length limit (512 bytes per RFC)
    if (line.length() > 512) line = line.substring(0, 512);
    
    // 2. No control chars except CTCP delimiter
    // 3. Graceful handling of missing params
    // 4. Validates command format
}
```

### UI Input Validation

```java
// CommandProcessor - validates before send
public boolean process(String input) {
    if (input.startsWith("/")) {
        // Parse command, validate args
        // No shell injection possible (no shell)
    } else {
        // Message: length limit 400 chars
        if (input.length() > 400) input = input.substring(0, 400);
    }
}
```

### Flood Protection

```java
// FloodQueue - token bucket
// 2 messages per 2 seconds, burst 4
// Prevents: accidental spam, malicious flood from compromised client
```

---

## Network Security

### Connection Security Checklist

| Layer | Check |
|-------|-------|
| **Phone → Gateway** | Plain TCP, shared secret auth, your VPS |
| **Gateway → IRC** | TLS 1.2+, cert verify, SNI, modern ciphers |
| **IRC Network** | Server-side: SASL, cloaking, TLS enforcement |

### Recommended Gateway Hardening

```bash
# 1. Firewall - only necessary ports
sudo ufw default deny incoming
sudo ufw allow 22/tcp      # SSH (your IP only)
sudo ufw allow 6667/tcp    # Gateway (your phone IP or VPN)
sudo ufw enable

# 2. Fail2Ban on gateway auth failures
# (See gateway.md for config)

# 3. Non-root user
sudo useradd -r -s /bin/false gateway
# Run as 'gateway' user (systemd does this)

# 4. Read-only filesystem (optional)
# systemd: ProtectSystem=strict, ReadWritePaths=/var/log

# 5. Resource limits
# systemd: LimitNOFILE=10000, LimitNPROC=50
```

### Monitoring for Abuse

```bash
# Watch for unusual connections
sudo journalctl -u chatterbox-gateway -f | grep -E "(connected|failed|rate limit)"

# Alert on many failed auths
# (Integrate with Prometheus/Grafana if desired)
```

---

## Developer Certificate (For Direct TLS)

### Why It's Needed

Nokia S60 MIDP security model:
- **Untrusted domain** (self-signed) → `socket://` OK, `ssl://` **BLOCKED**
- **Trusted domain** (VeriSign/Thawte) → Both OK (certs expired/unavailable)
- **Manufacturer domain** (Nokia signed) → Both OK (requires Nokia signing)
- **Developer domain** (dev cert) → Both OK **if cert installed on phone**

### Creating a Developer Certificate

**Requires:** Nokia S60 SDK (Windows) or `makecert`/`signtool` from SDK.

```bash
# 1. Generate key pair + CSR
makecert -csr -o my.csr -k my.key -cn "Chatterbox Dev" -ou "Dev" -o "Me" -c "US"

# 2. Submit to Symbian Signed (service discontinued)
#    Alternative: Self-sign with OpenSSL, but phone won't trust it

# 3. If you have a dev cert (.cer):
#    - Install on phone: Bluetooth/email → Open → Install certificate
#    - Sign JAR:
signtool sign -keystore my.keystore -alias myalias \
  -storepass storepass -keypass keypass \
  dist/chatterbox.jar
```

### Practical Reality

> **This path is effectively dead** — Symbian Signed discontinued, Nokia tools unavailable.
> 
> **Use the gateway instead.** It's simpler, more secure, and works on all devices.

---

## Security Checklist for Deployment

### Gateway Server
- [ ] `GATEWAY_TLS_VERIFY=true`
- [ ] Strong `GATEWAY_PASSWORD` (20+ random chars)
- [ ] Firewall: only phone IP/VPN can reach port 6667
- [ ] Run as non-root user (`gateway`)
- [ ] systemd hardening: `ProtectSystem=strict`, `NoNewPrivileges=yes`
- [ ] Fail2Ban on auth failures
- [ ] Regular OS updates (`apt update && apt upgrade`)

### Phone Configuration
- [ ] Use gateway (not direct TLS)
- [ ] Phone lock code enabled
- [ ] Gateway password not shared
- [ ] App permissions: Network only (no SMS, contacts, etc.)

### Build & Distribution
- [ ] ProGuard enabled (obfuscation)
- [ ] No debug symbols in release JAR
- [ ] JAD/JAR served over HTTPS (OTA)
- [ ] Verify JAR checksum before install

---

## Incident Response

### If Gateway Compromised
1. **Immediate:** `systemctl stop chatterbox-gateway`
2. **Rotate:** Change `GATEWAY_PASSWORD`, restart
3. **Audit:** Check logs for unauthorized connections
4. **Rebuild:** Fresh VPS if root access suspected

### If Phone Lost/Stolen
1. **Revoke:** Change gateway password
2. **NickServ:** `/msg NickServ SET PASSWORD newpass`
3. **Uninstall:** Remote wipe if MDM available

### If IRC Credentials Leaked
1. **NickServ:** `/msg NickServ SET PASSWORD newpass`
2. **ChanServ:** Re-identify with new password
3. **Check:** `/msg NickServ ACCESS LIST` for unauthorized

---

## Compliance Notes

### Data Protection
- **No personal data** collected by Chatterbox
- **No analytics, tracking, or telemetry**
- **RMS data** stays on device
- **Gateway logs** only connection metadata (IP, timestamps)

### Export Controls
- **Encryption:** Uses TLS (standard, publicly available)
- **Classification:** EAR99 (mass market software)
- **No license required** for distribution

---

## Security Resources

- [OWASP Mobile Top 10](https://owasp.org/www-project-mobile-top-10/)
- [MIDP 2.0 Security Model](https://jcp.org/en/jsr/detail?id=118)
- [Python SSL Best Practices](https://docs.python.org/3/library/ssl.html#security-considerations)
- [Let's Encrypt Root CA Timeline](https://letsencrypt.org/docs/certificate-compatibility/)

---

## Related Docs

- [Gateway](gateway.md) — Gateway setup & TLS config
- [Configuration](configuration.md) — Gateway password in settings
- [Installation](installation.md) — Certificate signing (historical)
- [Troubleshooting](troubleshooting.md) — Security error fixes
- [Architecture](architecture.md) — Security boundaries