---
id: emulator
title: Emulator
triggers: [emulator, wtk, nokia, s60, device, install, debug, test]
packages: [test, ui]
complexity: medium
---

# Emulator Skill

Test and debug Chatterbox using the Sun WTK or Nokia S60 emulator.

## When to Use

- Testing UI on a virtual device
- Debugging device-specific issues
- Verifying JAR/JAD installation
- Testing on different screen sizes
- Validating real device behavior

## Overview

The emulator provides a virtual Java ME device for testing without physical hardware. The project supports:

| Emulator | Platform | Best For |
|----------|----------|----------|
| Sun WTK 2.5.2 | S60 3rd Edition | General testing, preverification |
| Nokia S60 SDK | S60 3rd/5th Edition | Nokia-specific features |
| MicroEmulator | Desktop | Quick UI testing |

## Setting Up Sun WTK

### Installation

1. Download Sun WTK 2.5.2 from Oracle archives
2. Install to `/opt/wtk` (Linux) or `C:\WTK` (Windows)
3. Set environment variable:
```bash
export WTK_HOME=/opt/wtk
```

### Configuration

Verify WTK is detected by Ant:
```bash
ant -v dist 2>&1 | grep WTK
```

## Running in the Emulator

### Via Ant

```bash
# Build and run in emulator
ant run

# Build, run, and install
ant install
```

### Via WTK GUI

1. Open WTK: `$WTK_HOME/bin/ktoolbar`
2. File → Open Project → Select `dist/chatterbox.jad`
3. Click "Run" to launch emulator

### Via Command Line

```bash
$WTK_HOME/bin/emulator -Xdescriptor:dist/chatterbox.jad
```

## Testing UI in the Emulator

### Screen Sizes

| Device | Resolution | Category |
|--------|-----------|----------|
| E63, E71, E72 | 320x240 | QWERTY landscape |
| C3 | 240x320 | QWERTY portrait |
| N95 | 240x320 | Slider |
| 5800 XpressMusic | 360x640 | Touch |

### Key Mapping

| Emulator Key | Device Key |
|-------------|------------|
| Arrow keys | D-pad |
| Enter | Center select |
| Softkey left | Left softkey |
| Softkey right | Right softkey |
| Backspace | Clear/back |
| Number keys | Number pad |

### Testing Checklist

- [ ] App installs from JAD
- [ ] Splash screen displays
- [ ] Server list loads
- [ ] Connection to server works
- [ ] Chat view renders correctly
- [ ] Input line accepts text
- [ ] Nick list displays
- [ ] Settings screen works
- [ ] Back navigation works
- [ ] App exits cleanly

## Debugging in the Emulator

### Enable Logging

In `build.xml`, add to the run target:
```xml
<target name="run" depends="dist">
    <exec executable="${wtk.home}/bin/emulator">
        <arg value="-Xverbose"/>
        <arg value="-Xdescriptor:${dist.dir}/chatterbox.jad"/>
    </exec>
</target>
```

### View Logs

```bash
# WTK logs are in the emulator output
ant run 2>&1 | tee emulator.log

# Search for errors
grep -i "exception\|error" emulator.log
```

### Common Emulator Issues

| Issue | Cause | Fix |
|-------|-------|-----|
| "Class not found" | JAR not in JAD | Verify `Midlet-Jar-URL` in JAD |
| "Invalid JAD" | Missing attributes | Check all required MIDlet attributes |
| "Out of memory" | Heap too small | Increase `-Xheapsize` in emulator |
| "Connection refused" | No network | Enable network in emulator settings |
| "Preverify failed" | WTK not set | Verify `WTK_HOME` environment variable |

## MicroEmulator (Desktop)

For quick UI testing without WTK:

```bash
# Download MicroEmulator
wget https://microemu.org/microemulator-2.0.4.zip

# Run with Chatterbox
java -jar microemulator.jar dist/chatterbox.jad
```

### MicroEmulator Configuration

Create `microemulator.properties`:
```properties
device=org.microemu.device.default.DefaultDevice
screenWidth=320
screenHeight=240
keyUp=UP
keyDown=DOWN
keyLeft=LEFT
keyRight=RIGHT
keyFire=ENTER
```

## Preverification

Preverification is required for CLDC 1.1 and must be done with WTK:

```bash
# Via Ant (automatic)
ant dist

# Manual preverify
$WTK_HOME/bin/preverify \
  -classpath $WTK_HOME/lib/cldcapi11.jar:$WTK_HOME/lib/midpapi20.jar \
  -d build/preverified \
  build/classes/com/chatterbox/**/*.class
```

## Performance Testing

### Memory Usage

```bash
# In emulator, check memory
# WTK: Menu → Memory → Used
# Target: < 1 MB heap usage
```

### Startup Time

Measure from JAD launch to server list:
- Target: < 3 seconds on emulator
- Target: < 5 seconds on physical device

### Network Latency

Test with different network conditions:
- WiFi (fast)
- 3G (medium)
- Edge (slow)

## Release Testing

Before releasing a new version:

1. **Install on emulator** — verify JAR/JAD works
2. **Test all screens** — navigate every menu
3. **Test connection** — connect to a real IRC server
4. **Test commands** — verify all IRC commands work
5. **Test on physical device** — if possible
6. **Check JAR size** — must be under 300 KB
7. **Verify ProGuard** — ensure obfuscation ran

## Troubleshooting

### Emulator won't start
- Check `WTK_HOME` is set
- Verify Java is in PATH
- Try running emulator directly: `$WTK_HOME/bin/emulator -version`

### App crashes on launch
- Check JAD file is valid
- Verify all classes are in JAR
- Review emulator logs for stack trace
- Test with `ant test` first

### Network doesn't work in emulator
- Enable network in emulator settings
- Check firewall isn't blocking emulator
- Try pinging from emulator console

### UI renders incorrectly
- Test on multiple screen sizes
- Verify LCDUI constraints are followed
- Check for hardcoded pixel values
- Test on physical device

## Anti-Patterns

```bash
# WRONG: Running emulator without enough memory
emulator -Xdescriptor:dist/chatterbox.jad  # May OOM

# RIGHT: Specify heap size
emulator -Xheapsize:2M -Xdescriptor:dist/chatterbox.jad
```

```bash
# WRONG: Not cleaning between test runs
# Old state may interfere with new tests

# RIGHT: Clean install each time
ant clean install
```

## Code Template: Adding a Device Profile

```properties
# In WTK device configuration
# Add to emulator settings

# Screen
screenWidth=320
screenHeight=240

# Keys
keyUp=UP
keyDown=DOWN
keyLeft=LEFT
keyRight=RIGHT
keyFire=ENTER
keySoft1=-6    # Left softkey
keySoft2=-7    # Right softkey

# Network
networkType=http
proxyHost=
proxyPort=8080

# Memory
heapSize=2048000  # 2MB
```
