---
id: skills-index
title: Skills Index
triggers: [skill, help, index, navigate, find]
---

# Chatterbox Skills

This directory contains specialized skill guides for working with the Chatterbox IRC client codebase. Each skill provides detailed instructions for a specific area of development.

## Skill Index

| Skill | Description |
|-------|-------------|
| [build.md](build.md) | Build the MIDlet JAR/JAD, manage build artifacts |
| [test.md](test.md) | Run and maintain the 233-test suite |
| [protocol.md](protocol.md) | IRC protocol layer — parsing, CTCP, SASL, CAP, DCC |
| [crypto.md](crypto.md) | E2E encryption primitives and Double Ratchet |
| [gateway.md](gateway.md) | Python TLS relay server |
| [ui.md](ui.md) | LCDUI screens and custom Canvas chat view |
| [rms.md](rms.md) | Persistent storage via RecordStore |
| [net.md](network.md) | IRC connections, flood control, reconnection |
| [stub.md](stub.md) | MIDP/CLDC API stubs for desktop compilation |
| [session.md](session.md) | Session management and command processing |
| [emulator.md](emulator.md) | Testing with Sun WTK or Nokia S60 emulator |
| [security.md](security.md) | Security features and secure coding practices |
| [docs.md](docs.md) | Documentation site — MkDocs, translations |
| [contributing.md](contributing.md) | Contribution workflow and standards |

## Quick Start

New to the project? Read these first:

1. [AGENTS.md](../AGENTS.md) — Project overview and conventions
2. [build.md](build.md) — How to build the project
3. [test.md](test.md) — How to run tests
4. [contributing.md](contributing.md) — How to contribute

## Choosing a Skill

### By Task

| Task | Skill |
|------|-------|
| Fix a bug in IRC parsing | [protocol.md](protocol.md) |
| Add a new command | [protocol.md](protocol.md) + [session.md](session.md) |
| Fix a test failure | [test.md](test.md) |
| Add encryption feature | [crypto.md](crypto.md) |
| Modify the gateway | [gateway.md](gateway.md) |
| Change the UI | [ui.md](ui.md) |
| Add persistent data | [rms.md](rms.md) |
| Fix connection issues | [net.md](net.md) |
| Test on emulator | [emulator.md](emulator.md) |
| Security review | [security.md](security.md) |
| Update docs | [docs.md](docs.md) |

### By Package

| Package | Skill |
|---------|-------|
| `proto/` | [protocol.md](protocol.md) |
| `crypto/` | [crypto.md](crypto.md) |
| `core/` | [session.md](session.md) |
| `net/` | [net.md](net.md) |
| `rms/` | [rms.md](rms.md) |
| `ui/` | [ui.md](ui.md) |
| `stub/` | [stub.md](stub.md) |
| `gateway/` | [gateway.md](gateway.md) |
| `test/` | [test.md](test.md) |

## Contributing to Skills

To add or update a skill:

1. Edit the relevant `.md` file
2. Follow the existing format and style
3. Add code examples where helpful
4. Update this README if adding a new skill
5. Test any code examples before including them

## Skill Metadata

Each skill has YAML frontmatter for programmatic routing:

```yaml
---
id: skill-name
title: Display Title
triggers: [keyword1, keyword2, keyword3]
packages: [package1, package2]
complexity: easy|medium|hard
---
```

### Trigger Keywords

Use these keywords in agent queries to route to the right skill:

| Keyword | Routes To |
|---------|-----------|
| build, compile, jar, ant | `build.md` |
| test, junit, assert, coverage | `test.md` |
| irc, parse, ctcp, sasl, cap | `protocol.md` |
| encrypt, decrypt, chacha, e2e | `crypto.md` |
| gateway, tls, relay, python | `gateway.md` |
| ui, screen, canvas, lcdui | `ui.md` |
| rms, recordstore, storage, persist | `rms.md` |
| network, socket, connect, reconnect | `net.md` |
| stub, mock, desktop, shim | `stub.md` |
| session, command, event, channel | `session.md` |
| emulator, wtk, nokia, s60 | `emulator.md` |
| security, sanitize, encrypt, password | `security.md` |
| docs, documentation, mkdocs | `docs.md` |
| contribute, pr, commit, branch | `contributing.md` |

## Quick Reference

### By Task

| Task | Skill |
|------|-------|
| Fix a bug in IRC parsing | [protocol.md](protocol.md) |
| Add a new command | [protocol.md](protocol.md) + [session.md](session.md) |
| Fix a test failure | [test.md](test.md) |
| Add encryption feature | [crypto.md](crypto.md) |
| Modify the gateway | [gateway.md](gateway.md) |
| Change the UI | [ui.md](ui.md) |
| Add persistent data | [rms.md](rms.md) |
| Fix connection issues | [net.md](net.md) |
| Test on emulator | [emulator.md](emulator.md) |
| Security review | [security.md](security.md) |
| Update docs | [docs.md](docs.md) |

### By Package

| Package | Skill |
|---------|-------|
| `proto/` | [protocol.md](protocol.md) |
| `crypto/` | [crypto.md](crypto.md) |
| `core/` | [session.md](session.md) |
| `net/` | [net.md](net.md) |
| `rms/` | [rms.md](rms.md) |
| `ui/` | [ui.md](ui.md) |
| `stub/` | [stub.md](stub.md) |
| `gateway/` | [gateway.md](gateway.md) |
| `test/` | [test.md](test.md) |

### By Complexity

| Complexity | Skills |
|-----------|--------|
| Easy | build, test, stub, docs, contributing |
| Medium | protocol, gateway, rms, session, emulator |
| Hard | crypto, ui, net, security |
