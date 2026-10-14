# Chatterbox Documentation

Welcome to the complete documentation for **Chatterbox** — a production-ready IRC client for classic Nokia QWERTY phones (E63, E71, E72, C3, etc.) running Symbian S60 3rd/5th Edition or Series 40 3rd/5th Edition.

##  Documentation Index

| Document | Description |
|----------|-------------|
| [Getting Started](getting-started.md) | Quick start guide for new users |
| [Building from Source](building.md) | Complete build instructions with all requirements |
| [Installation on Device](installation.md) | Multiple methods to install on your Nokia phone |
| [Configuration Guide](configuration.md) | Detailed settings, profiles, and gateway setup |
| [Gateway Server](gateway.md) | TLS relay server setup (Docker, systemd, bare metal) |
| [Usage & Commands](usage.md) | Complete command reference and UI key mappings |
| [Architecture Overview](architecture.md) | Code structure, design patterns, and module breakdown |
| [Development Guide](development.md) | Testing, debugging, and contributing |
| [Troubleshooting](troubleshooting.md) | Common issues and solutions |
| [Security Notes](security.md) | TLS, certificates, and threat model |

---

##  Quick Links

- **Main README**: [../README.md](../README.md)
- **Source Code**: [../midlet/src/](../midlet/src/)
- **Gateway Server**: [../gateway/](../gateway/)
- **Test Suite**: [../test/](../test/)
- **Build Script**: [../build.xml](../build.xml)

---

##  Supported Devices

| Series | Models | Notes |
|--------|--------|-------|
| **S60 3rd Ed.** | E63, E71, E72, E90, N82, N95, 5700, 6110, 6120, 6220, N73, N76, N81, N93 | Full support |
| **S60 5th Ed.** | 5800, N97, N97 mini, X6, C6, 5230, 5233, 5250, 5530 | Touch + keypad |
| **S40 3rd Ed.** | 6131, 6233, 6234, 6300, 6500, 7370, 7373, 7390, 7500, 7900 | Feature phone |
| **S40 5th Ed.** | C3-00, C3-01, X3-02, C5-00.2, C6-00, E5-00, E7-00 | QWERTY candybar |

> **Minimum requirements:** MIDP 2.0, CLDC 1.1, ~300 KB free heap, TCP/IP networking

---

##  Feature Highlights

- **Multiple server profiles** stored in RMS (persistent)
- **TLS-first** with automatic plain fallback via gateway
- **Full RFC 2812** IRC protocol support
- **Per-channel ring buffers** (200 lines scrollback)
- **Nick lists** with modes (@ + % & ~ !)
- **Highlight badges** + vibrate/beep on mention/PM
- **Tab nick completion** in input field
- **129 unit tests** passing (protocol, core, session)
- **< 300 KB JAR** after ProGuard optimization

---

##  Start Here

1. **New to Chatterbox?** → [Getting Started](getting-started.md)
2. **Want to build it?** → [Building from Source](building.md)
3. **Need to install on phone?** → [Installation on Device](installation.md)
4. **Setting up the gateway?** → [Gateway Server](gateway.md)
5. **Learning commands?** → [Usage & Commands](usage.md)

---

##  License

MIT License — see [LICENSE](../LICENSE) file.

*Built with  for the love of physical keyboards and IRC.*