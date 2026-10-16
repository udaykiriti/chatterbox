---
id: contributing
title: Contributing
triggers: [contribute, pull request, pr, commit, branch, review, workflow]
packages: []
complexity: easy
---

# Contributing Skill

Guide for contributing to the Chatterbox project.

## When to Use

- Setting up a development environment
- Making your first contribution
- Understanding the contribution workflow
- Following code standards
- Submitting pull requests

## Development Setup

### Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| JDK | 8–21 | Compile and test Java code |
| Apache Ant | 1.10+ | Build automation |
| Python | 3.11+ | Gateway server |
| MkDocs | Latest | Documentation |
| Git | Latest | Version control |
| ProGuard | 7.x+ | Obfuscation (release builds) |
| WTK or Nokia SDK | 2.5.2+ | Preverification + emulator |

### Quick Start

```bash
# Clone the repository
git clone https://github.com/yourusername/chatterbox.git
cd chatterbox

# Run tests
ant test

# Build documentation
pip install -r requirements-docs.txt
make serve

# Build distribution (requires WTK + ProGuard)
ant dist
```

## Contribution Workflow

### 1. Create a branch

```bash
git checkout -b feature/my-feature-name
```

### 2. Make your changes

- Follow the code style (see AGENTS.md)
- Add tests for new functionality
- Update documentation
- Run `ant test` to verify

### 3. Commit your changes

```bash
git add .
git commit -m "feat: add support for IRCv3 message-tags

- Parse message-tag in IrcParser
- Display tags in ChatView
- Add tests in ProtocolParserTest
- Update docs/architecture.md"
```

### 4. Push and create PR

```bash
git push origin feature/my-feature-name
# Create PR on GitHub
```

## Commit Message Format

```
<type>: <short description>

[optional body]

[optional footer]
```

### Types

| Type | Description |
|------|-------------|
| `feat` | New feature |
| `fix` | Bug fix |
| `docs` | Documentation only |
| `style` | Code style (formatting, etc.) |
| `refactor` | Code refactoring |
| `test` | Adding or updating tests |
| `chore` | Build, CI, or tooling changes |
| `perf` | Performance improvement |
| `security` | Security fix |

### Examples

```
feat: add SASL EXTERNAL authentication

- Implement EXTERNAL mechanism in Sasl.java
- Add certificate-based auth flow
- Update docs/configuration.md
- Add tests in SessionTest.java

Closes #123
```

```
fix: prevent crash on empty IRC line

- Check for empty input in IrcParser
- Add null check in Session.java
- Add regression test in ProtocolParserTest
```

## Code Review Checklist

Before submitting a PR, verify:

- [ ] All tests pass (`ant test`)
- [ ] Code follows style guidelines
- [ ] New features have tests
- [ ] Documentation is updated
- [ ] No debugging code left in
- [ ] Commit messages are clear
- [ ] Branch is up to date with `main`

## Pull Request Template

```markdown
## Description
Brief description of changes.

## Changes
- [ ] Change 1
- [ ] Change 2

## Testing
- [ ] Unit tests added/updated
- [ ] Manual testing performed
- [ ] Emulator testing performed (if UI changes)

## Documentation
- [ ] Docs updated
- [ ] Comments added
- [ ] CHANGELOG.md updated

## Screenshots (if UI changes)
```

## Areas to Contribute

### Good First Issues

- Documentation improvements
- Test coverage for `proto/` classes
- Bug fixes in `core/` classes
- Spanish translation updates

### Advanced Features

- New IRCv3 capabilities
- Additional crypto primitives
- UI/UX improvements
- Gateway enhancements

### Code Areas by Difficulty

| Area | Difficulty | Notes |
|------|-----------|-------|
| `docs/` | Easy | Markdown only |
| `test/` | Easy-Medium | Java, but isolated |
| `proto/` | Medium | Pure Java, well-tested |
| `crypto/` | Medium-Hard | Crypto expertise needed |
| `core/` | Medium | Complex state management |
| `net/` | Hard | Network I/O, concurrency |
| `ui/` | Hard | LCDUI constraints, manual testing |
| `gateway/` | Medium | Python, asyncio |

## Testing Requirements

| Change Type | Required Tests |
|-------------|---------------|
| `proto/` changes | Unit tests in `ProtocolParserTest` |
| `crypto/` changes | RFC test vectors in `CryptoTest` |
| `core/` changes | Unit tests in `CoreTest` or `SessionTest` |
| `net/` changes | Manual/emulator testing |
| `ui/` changes | Manual/emulator testing |
| `gateway/` changes | Syntax check + manual testing |
| `docs/` changes | `mkdocs build --strict` |

## Release Process

1. All tests pass
2. Version bumped in `build.xml`
3. `CHANGELOG.md` updated
4. `ant dist` produces JAR/JAD
5. Tag release: `git tag v1.x.x`
6. Push tag: `git push origin v1.x.x`
7. Create GitHub release with notes

## Getting Help

- Check `docs/development.md` for detailed guides
- Read `docs/architecture.md` for system overview
- Open a GitHub Discussion for questions
- Join the IRC channel (see README)

## Code of Conduct

- Be respectful and inclusive
- Focus on constructive feedback
- Help newcomers learn
- Follow the project's security practices

## Anti-Patterns

```bash
# WRONG: Committing directly to main
git checkout main
git commit -m "feat: add feature"
git push origin main

# RIGHT: Use feature branches
git checkout -b feature/my-feature
git commit -m "feat: add feature"
git push origin feature/my-feature
# Then create PR
```

```bash
# WRONG: Vague commit message
git commit -m "fix bug"

# RIGHT: Descriptive commit message
git commit -m "fix: prevent crash on empty IRC line

- Check for empty input in IrcParser
- Add null check in Session.java
- Add regression test in ProtocolParserTest

Closes #123"
```

## Code Template: Pull Request

```markdown
## Description
Brief description of changes.

## Changes
- [ ] Change 1
- [ ] Change 2

## Testing
- [ ] Unit tests added/updated
- [ ] Manual testing performed
- [ ] Emulator testing performed (if UI changes)

## Documentation
- [ ] Docs updated
- [ ] Comments added
- [ ] CHANGELOG.md updated

## Screenshots (if UI changes)
```
