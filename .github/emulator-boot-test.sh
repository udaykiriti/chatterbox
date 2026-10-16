#!/bin/bash
# Headless MIDlet boot regression test for Chatterbox.
#
# Compiles the app with ECJ (no WTK needed), packages a JAR/JAD, then boots
# the MIDlet in MicroEmulator under Xvfb using its built-in -Xautotest mode.
# Fails when startApp throws (the NoSuchMethodError class of bug that broke
# startup before) or when boot does not reach the server list.
#
# Usage: .github/emulator-boot-test.sh [workdir]
# Env:   BOOT_TIMEOUT   seconds to let the MIDlet run (default 90)
set -u
WORK="${1:-$(mktemp -d)}"
mkdir -p "$WORK"
TIMEOUT="${BOOT_TIMEOUT:-90}"
ECJ_URL="https://repo1.maven.org/maven2/org/eclipse/jdt/ecj/3.33.0/ecj-3.33.0.jar"
ME_URL="https://repo1.maven.org/maven2/org/microemu/microemulator/2.0.4/microemulator-2.0.4.jar"

echo "workdir: $WORK"
curl -sSL -o "$WORK/ecj.jar" "$ECJ_URL"
curl -sSL -o "$WORK/me.jar" "$ME_URL"
mkdir -p "$WORK/stub" "$WORK/classes"
# shellcheck disable=SC2046
java -jar "$WORK/ecj.jar" -source 8 -target 8 -encoding UTF-8 -nowarn \
    -d "$WORK/stub" $(find stub -name "*.java")
# shellcheck disable=SC2046
java -jar "$WORK/ecj.jar" -source 8 -target 8 -encoding UTF-8 -nowarn \
    -cp "$WORK/stub" -d "$WORK/classes" $(find midlet/src -name "*.java")

JAR="$WORK/app.jar"
JAD="$WORK/app.jad"
python3 - "$WORK/classes" "$JAR" "$JAD" <<'EOF'
import sys, zipfile, os
classes_dir, jar_path, jad_path = sys.argv[1], sys.argv[2], sys.argv[3]
manifest = ("Manifest-Version: 1.0\r\n"
    "MIDlet-1: Chatterbox, , com.chatterbox.ui.ChatterboxMIDlet\r\n"
    "MIDlet-Name: Chatterbox\r\n"
    "MIDlet-Vendor: chatterbox\r\n"
    "MIDlet-Version: 1.0\r\n"
    "MicroEdition-Configuration: CLDC-1.1\r\n"
    "MicroEdition-Profile: MIDP-2.0\r\n")
with zipfile.ZipFile(jar_path, "w", zipfile.ZIP_DEFLATED) as z:
    z.writestr("META-INF/MANIFEST.MF", manifest)
    for root, dirs, files in os.walk(classes_dir):
        for f in files:
            full = os.path.join(root, f)
            z.write(full, os.path.relpath(full, classes_dir))
    if os.path.exists("midlet/res/icon.png"):
        z.write("midlet/res/icon.png", "icon.png")
size = os.path.getsize(jar_path)
with open(jad_path, "w") as f:
    f.write("MIDlet-1: Chatterbox, , com.chatterbox.ui.ChatterboxMIDlet\n"
            "MIDlet-Name: Chatterbox\n"
            "MIDlet-Vendor: chatterbox\n"
            "MIDlet-Version: 1.0\n"
            "MIDlet-Jar-URL: file://" + jar_path + "\n"
            "MIDlet-Jar-Size: %d\n" % size +
            "MicroEdition-Configuration: CLDC-1.1\n"
            "MicroEdition-Profile: MIDP-2.0\n")
print("packaged %d bytes" % size)
EOF

# The MIDlet is interactive so --quit never fires; timeout always kills it.
# Success is judged from the log, not the exit code.
if command -v xvfb-run >/dev/null 2>&1; then
    XV="xvfb-run -a"
else
    XV="" # assume DISPLAY already points at a running Xvfb
fi
timeout "$TIMEOUT" $XV java -jar "$WORK/me.jar" \
    "-Xautotest:file://$JAD" --quit >"$WORK/emu.log" 2>&1 || true

fail=0
check() {
    if [ "$1" = "yes" ]; then echo "PASS $2"; else echo "FAIL $2"; fail=1; fi
}
grep -q "AutoTests start class \[com.chatterbox.ui.ChatterboxMIDlet\]" "$WORK/emu.log" \
    && s=yes || s=no
check "$s" "MIDlet startApp entered"
for cls in "ui/ServerListScreen" "ui/ChatCanvas" "rms/ProfileStore" "ui/Notifier"; do
    grep -q "Loading MIDlet class com/chatterbox/$cls" "$WORK/emu.log" \
        && s=yes || s=no
    check "$s" "boot reached $cls"
done
if grep -qiE "error Message|Exception in thread|NoSuchMethodError" "$WORK/emu.log"; then
    echo "--- emulator errors ---"
    grep -iE "error Message|Exception in thread|NoSuchMethodError" "$WORK/emu.log" | head -5
    fail=1
else
    echo "PASS no emulator errors"
fi
[ "$fail" = 0 ] && echo "emulator boot test: all green"
exit "$fail"
