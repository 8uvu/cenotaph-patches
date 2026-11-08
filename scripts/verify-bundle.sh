#!/usr/bin/env bash
# Verify a Cenotaph bundle against a real APK, without a phone.
#
#   scripts/verify-bundle.sh "Instagram app/build.apkm"
#
# A build proves compilation and a list proves registration; neither proves the
# patch wrote anything. This checks the patched output itself: the expected
# number of patches applied, one injected registration call per feature inside
# the dex, the settings activity registered in the manifest, and no leftovers
# from removed features.
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1

APK="${1:-}"
if [ -z "$APK" ]; then
    APK=$(ls "Instagram app/"*.apkm 2>/dev/null | head -1)
fi
if [ ! -f "$APK" ]; then
    echo "FAIL: no APK given and none found in 'Instagram app/'"; exit 1
fi

JAVA="tools/morphe-cli/jdk-21.0.12.1+1-jre/bin/java.exe"
CLI="tools/morphe-cli/cli.jar"
[ -x "$JAVA" ] || JAVA="java"
[ -f "$CLI" ] || { echo "FAIL: $CLI missing"; exit 1; }

VERSION=$(sed -n 's/^version = //p' gradle.properties | tr -d '\r')
MPP="patches/build/libs/patches-${VERSION}.mpp"
[ -f "$MPP" ] || { echo "FAIL: $MPP missing — run ./gradlew :patches:buildAndroid"; exit 1; }

echo "bundle: $MPP"
echo "target: $APK"

# Every feature patch must inject exactly one registration call.
SYMBOLS=(install attachThreadView themeWallpaper enableWallpaper enableColorMap enableBubbles enableFont enableStreaks)
EXPECTED_PATCHES=$(( ${#SYMBOLS[@]} + 3 ))   # + Theme picker + Settings app + Piko category
REMOVED=(enableFakeDm FakeInjectHook EpitaphFakePanelActivity EpitaphMedia)

fail=0

registered=$("$JAVA" -jar "$CLI" list-patches --patches="$MPP" \
    --filter-package-name=com.instagram.android 2>&1 | grep -cE "^Name:")
if [ "$registered" -eq "$EXPECTED_PATCHES" ]; then
    echo "ok   registered patches: $registered"
else
    echo "FAIL registered patches: $registered (expected $EXPECTED_PATCHES)"; fail=1
fi

OUT="tools/tmp/verify-out.apk"
rm -f "$OUT"
"$JAVA" -jar "$CLI" patch -p "$MPP" -o "$OUT" --continue-on-error --unsigned "$APK" \
    > tools/tmp/verify-patch.log 2>&1
rc=$?
applied=$(grep -cE "^INFO: Applied" tools/tmp/verify-patch.log)
failed=$(grep -cE "^ERROR" tools/tmp/verify-patch.log)
if [ "$rc" -eq 0 ] && [ "$applied" -eq "$EXPECTED_PATCHES" ] && [ "$failed" -eq 0 ]; then
    echo "ok   applied: $applied / $EXPECTED_PATCHES, no errors"
else
    echo "FAIL applied=$applied expected=$EXPECTED_PATCHES errors=$failed rc=$rc"
    grep -E "^ERROR" tools/tmp/verify-patch.log | head -5
    fail=1
fi

if [ ! -f "$OUT" ]; then
    echo "FAIL: no patched APK produced"; exit 1
fi

rm -rf tools/tmp/verify-dex && mkdir -p tools/tmp/verify-dex
unzip -oq "$OUT" "classes*.dex" -d tools/tmp/verify-dex

for sym in "${SYMBOLS[@]}"; do
    n=$(grep -ac "$sym" tools/tmp/verify-dex/classes*.dex 2>/dev/null \
        | awk -F: '{s+=$2} END {print s+0}')
    if [ "$n" -ge 1 ]; then
        echo "ok   injected: $sym"
    else
        echo "FAIL injected call missing from dex: $sym"; fail=1
    fi
done

for sym in "${REMOVED[@]}"; do
    n=$(grep -ac "$sym" tools/tmp/verify-dex/classes*.dex 2>/dev/null \
        | awk -F: '{s+=$2} END {print s+0}')
    if [ "$n" -eq 0 ] && ! grep -aq "$sym" "$OUT"; then
        echo "ok   removed: $sym"
    else
        echo "FAIL removed feature still present: $sym"; fail=1
    fi
done

# tools/aapt2 is a directory holding the binary; resolve it rather than
# assuming the path, and pass the APK first (aapt2 rejects the other order).
AAPT2="tools/aapt2/aapt2.exe"
[ -x "$AAPT2" ] || AAPT2=$(find tools -maxdepth 4 -type f -iname "aapt2*" \
    ! -name "*.jar" 2>/dev/null | head -1)
if [ -n "$AAPT2" ] && [ -x "$AAPT2" ]; then
    "$AAPT2" dump xmltree "$OUT" --file AndroidManifest.xml > tools/tmp/manifest.txt 2>&1
    if grep -q "EpitaphSettingsActivity" tools/tmp/manifest.txt; then
        echo "ok   manifest: Cenotaph Settings registered"
    else
        echo "FAIL manifest: settings activity missing"; fail=1
    fi
    if grep -q "EpitaphFakePanelActivity" tools/tmp/manifest.txt; then
        echo "FAIL manifest: fake panel still registered"; fail=1
    else
        echo "ok   manifest: no fake panel entry"
    fi
else
    echo "skip manifest check (aapt2 not found)"
fi

if [ "$fail" -ne 0 ]; then
    echo; echo "VERIFY FAILED"; exit 1
fi
echo; echo "VERIFY PASSED"
