---
id: release
title: Release
triggers: [release, deploy, version, tag, publish, changelog, ship]
packages: []
complexity: medium
---

# Release Skill

Step-by-step release process for Chatterbox.

## When to Use

- Cutting a new version
- Publishing to GitHub
- Deploying documentation
- Creating distribution artifacts

## Release Checklist

### Pre-Release

- [ ] All tests pass (`ant test`)
- [ ] No known critical bugs
- [ ] CHANGELOG.md updated
- [ ] Version bumped in `build.xml`
- [ ] JAR size under 300 KB
- [ ] Documentation updated
- [ ] Gateway syntax check passes

### Build

```bash
# Clean build
ant clean dist

# Verify artifacts
ls -la dist/
# chatterbox.jar (< 300 KB)
# chatterbox.jad
```

### Test

```bash
# Run full test suite
ant test

# Test on emulator
ant run

# Test on physical device (if possible)
# Install JAR/JAD via Bluetooth or cable
```

### Tag and Release

```bash
# Tag the release
git tag -a v1.x.x -m "Release v1.x.x"

# Push tag
git push origin v1.x.x

# Create GitHub release
gh release create v1.x.x \
  --title "Chatterbox v1.x.x" \
  --notes-file CHANGELOG.md \
  dist/chatterbox.jar \
  dist/chatterbox.jad
```

### Post-Release

- [ ] Deploy documentation (`make deploy`)
- [ ] Verify docs site is live
- [ ] Announce on IRC/social media
- [ ] Update version in `build.xml` for next dev cycle

## Version Numbering

Follow [Semantic Versioning](https://semver.org/):

| Version | When | Example |
|---------|------|---------|
| `MAJOR` | Breaking changes | 2.0.0 |
| `MINOR` | New features | 1.1.0 |
| `PATCH` | Bug fixes | 1.0.1 |

## CHANGELOG Format

```markdown
## [1.1.0] - 2026-10-16

### Added
- New IRCv3 capability support
- Spanish documentation

### Fixed
- Crash on empty channel
- Memory leak in message buffer

### Changed
- Improved flood control algorithm
- Updated ProGuard configuration

### Security
- Fixed timing attack in MAC comparison
```

## Release Types

### Patch Release (1.0.1)

For bug fixes:
1. Fix bug
2. Add regression test
3. Update CHANGELOG
4. Bump patch version
5. Build, test, tag, release

### Minor Release (1.1.0)

For new features:
1. Develop feature
2. Add tests
3. Update docs
4. Update CHANGELOG
5. Bump minor version
6. Build, test, tag, release

### Major Release (2.0.0)

For breaking changes:
1. Plan migration
2. Implement changes
3. Update docs
4. Update CHANGELOG
5. Bump major version
6. Build, test, tag, release

## Anti-Patterns

```bash
# WRONG: Releasing without testing
git tag v1.0.0
git push origin v1.0.0

# RIGHT: Always test first
ant test && git tag v1.0.0 && git push origin v1.0.0
```

```bash
# WRONG: Forgetting to update version
# Version still says 1.0.0 in build.xml

# RIGHT: Bump version before release
# Edit build.xml: <property name="version" value="1.1.0"/>
```

## Release Template

```markdown
# Release vX.Y.Z

## Release Date
YYYY-MM-DD

## Highlights
- Feature 1
- Feature 2
- Bug fix 1

## Assets
- chatterbox.jar
- chatterbox.jad

## Verification
- [x] All tests pass
- [x] Tested on emulator
- [x] Tested on physical device
- [x] Documentation updated
- [x] CHANGELOG updated
```
