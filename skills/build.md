---
id: build
title: Build
triggers: [build, compile, jar, jad, ant, proguard, preverify, dist, obfuscate]
packages: [build]
complexity: easy
---

# Build Skill

Build the Chatterbox MIDlet JAR/JAD and manage build artifacts.

## When to Use

- Creating a distribution build
- Cleaning build artifacts
- Troubleshooting build failures
- Understanding the build pipeline

## Build Pipeline

The Ant build (`build.xml`) runs these phases in order:

1. **clean** — Remove `build/` and `dist/` directories
2. **stubs** — Compile MIDP/CLDC stubs from `stub/`
3. **compile** — Compile midlet source from `midlet/src/`
4. **preverify** — CLDC preverification (requires WTK or Nokia SDK)
5. **obfuscate** — ProGuard shrink/optimize/obfuscate
6. **jar** — Package into JAR with manifest
7. **jad** — Generate JAD descriptor
8. **test** — Compile and run test suite
9. **sign** — Sign JAR (requires keystore)

## Commands

```bash
# Full distribution build
ant dist

# Individual phases
ant clean          # Remove build artifacts
ant stubs          # Compile stubs only
ant compile        # Compile midlet only
ant test           # Compile and run tests
ant sign           # Sign the JAR

# Build with specific JDK (device code needs Java 8 toolchain)
ant -Dbuild.compiler=modern dist
```

## Output Artifacts

| Artifact | Path | Description |
|----------|------|-------------|
| Compiled classes | `build/classes/` | Pre-verification classes |
| Preverified | `build/preverified/` | CLDC preverified classes |
| Obfuscated | `build/obfuscated/` | ProGuard output |
| JAR | `dist/chatterbox.jar` | Distribution JAR |
| JAD | `dist/chatterbox.jad` | Application descriptor |
| Test classes | `build/tests/` | Compiled test classes |

## Troubleshooting

### Preverify fails
- Ensure WTK 2.5.2 or Nokia S60 SDK is installed
- Set `WTK_HOME` environment variable
- Check `build.xml` for preverify task configuration

### ProGuard fails
- Ensure ProGuard 7.x+ is in PATH
- Check `proguard.cfg` for syntax errors
- Verify `-libraryjars` paths are correct

### JAR too large
- Ensure ProGuard is running (check `build/obfuscated/` exists)
- Review `proguard.cfg` for overly aggressive keep rules
- Target: under 300 KB

## Anti-Patterns

```bash
# WRONG: Building without ProGuard (JAR will be too large)
ant compile

# RIGHT: Full build with obfuscation
ant dist
```

```bash
# WRONG: Forgetting to clean before rebuilding
ant dist

# RIGHT: Clean first to avoid stale artifacts
ant clean dist
```

```bash
# WRONG: Building on JDK 21 without setting source level
ant dist  # May fail with "unsupported class file version"

# RIGHT: Use appropriate JDK or set source level
ant -Dbuild.compiler=modern dist
```

## Code Template: Adding a New Build Target

```xml
<!-- Add to build.xml -->
<target name="mytarget" depends="compile">
    <echo message="Running my target..."/>
    <mkdir dir="build/myoutput"/>
    <javac srcdir="midlet/src"
           destdir="build/myoutput"
           source="1.3"
           target="1.1"
           encoding="UTF-8">
        <classpath>
            <pathelement path="build/classes"/>
        </classpath>
    </javac>
</target>
```

## Constraints

- Device code must compile at source level 1.3 (no generics, lambdas)
- Preverification requires WTK or Nokia SDK
- ProGuard is mandatory for release builds
- Final JAR must be under 300 KB
