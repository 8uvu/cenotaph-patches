#!/usr/bin/env bash
# Verify a Cenotaph bundle against one target APK/APKM.
#
#   scripts/verify-bundle.sh "Instagram app/<build>.apkm"
#   scripts/verify-bundle.sh screenshots/base-447.apk
#
# Checks, in order: the bundle registers the expected patches; every one applies;
# the extension carries the symbols the injections call; and each injected call
# is present in the patched dex while absent from the source APK. That last check
# is the one that separates a real hook from an inert patch.
set -uo pipefail
cd "$(dirname "$0")/.."

TARGET="${1:-}"
if [ -z "$TARGET" ]; then
  echo "usage: $0 <apk|apkm>" >&2
  exit 2
fi

MPP=$(ls -1t patches/build/libs/*.mpp 2>/dev/null | grep -vE "sources|javadoc" | head -1)
if [ -z "$MPP" ]; then
  echo "no bundle built: run ./gradlew :patches:buildAndroid" >&2
  exit 2
fi

JAVA=tools/morphe-cli/jdk-21.0.12.1+1-jre/bin/java.exe
CLI=tools/morphe-cli/cli.jar
WORK=$(mktemp -d)
OUT="$WORK/patched.apk"
LOG="$WORK/patch.log"

SYMBOLS=(install attachThreadView themeWallpaper enableWallpaper)
CALLS=(attachThreadView themeWallpaper)
EXPECTED_PATCHES=4

FAILED=0
fail() { echo "FAIL $*"; FAILED=1; }

echo "bundle: $MPP"
echo "target: $TARGET"

REGISTERED=$("$JAVA" -jar "$CLI" list-patches --patches="$MPP" 2>/dev/null | grep -c '^Name: ')
[ "$REGISTERED" -eq "$EXPECTED_PATCHES" ]   && echo "ok   registered patches: $REGISTERED"   || fail "registered patches: $REGISTERED (expected $EXPECTED_PATCHES)"

"$JAVA" -jar "$CLI" patch -p "$MPP" -o "$OUT" --continue-on-error --unsigned "$TARGET" > "$LOG" 2>&1

APPLIED=$(grep -c "INFO: Applied:" "$LOG")
ERRORS=$(grep -c "ERROR: FAILED:" "$LOG")
[ "$APPLIED" -eq "$EXPECTED_PATCHES" ] && [ "$ERRORS" -eq 0 ]   && echo "ok   applied: $APPLIED / $EXPECTED_PATCHES, no errors"   || fail "applied=$APPLIED expected=$EXPECTED_PATCHES errors=$ERRORS"

grep "ERROR: FAILED:" "$LOG" | sed 's/^/     /'

if [ -s "$OUT" ]; then
  for sym in "${SYMBOLS[@]}"; do
    n=$(unzip -p "$MPP" extensions/extension.mpe 2>/dev/null | grep -a -c "$sym" || true)
    [ "${n:-0}" -gt 0 ] && echo "ok   extension symbol: $sym" || fail "extension symbol missing: $sym"
  done

  for call in "${CALLS[@]}"; do
    rm -rf "$WORK/p" "$WORK/s"; mkdir -p "$WORK/p" "$WORK/s"
    unzip -o -q "$OUT" "classes*.dex" -d "$WORK/p" 2>/dev/null
    unzip -o -q "$TARGET" "classes*.dex" -d "$WORK/s" 2>/dev/null
    patched=$(grep -ac "$call" "$WORK"/p/classes*.dex 2>/dev/null | grep -v ":0" | wc -l)
    source=$(grep -ac "$call" "$WORK"/s/classes*.dex 2>/dev/null | grep -v ":0" | wc -l)
    if [ "$patched" -gt 0 ] && [ "$source" -eq 0 ]; then
      echo "ok   injected call: $call (present after patching, absent before)"
    else
      fail "injected call: $call (patched=$patched source=$source)"
    fi
  done

  # A patched manifest is binary AXML, so it has to be decoded, not grepped.
  if tools/aapt2/aapt2.exe dump xmltree --file AndroidManifest.xml "$OUT" 2>/dev/null       | grep -q "EpitaphSettingsActivity"; then
    echo "ok   manifest: settings activity registered"
  else
    fail "manifest: settings activity missing"
  fi
else
  fail "no patched APK produced"
fi

rm -rf "$WORK"

if [ "$FAILED" -eq 0 ]; then
  echo
  echo "VERIFY PASSED"
  exit 0
fi
echo
echo "VERIFY FAILED"
exit 1
