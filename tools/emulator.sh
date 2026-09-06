#!/usr/bin/env bash
# Boots one of round 8's preview AVDs and waits for it to be ready.
# Usage: tools/emulator.sh [wl_phone|wl_small|wl_tall|wl_tablet]
# Defaults to wl_phone (the S24-ish baseline). One shared system image
# (system-images;android-35;google_apis;x86_64) backs all four - see
# CLAUDE.md's "Round 8" section for why these four and not more.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
source tools/env.sh

AVD="${1:-wl_phone}"

if ! avdmanager.bat list avd 2>/dev/null | grep -q "Name: $AVD$"; then
    echo "No AVD named '$AVD'. Known AVDs: wl_small wl_phone wl_tall wl_tablet" >&2
    exit 1
fi

if ! adb devices | grep -q "emulator.*device$"; then
    echo "Booting $AVD..."
    emulator.exe -avd "$AVD" -no-snapshot-load &
    adb wait-for-device
    # wait-for-device only confirms the ADB bridge is up, not that the boot
    # animation has finished - sys.boot_completed is the real signal.
    until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
        sleep 2
    done
    echo "$AVD is booted."
else
    echo "An emulator is already running."
fi

if [ -f app/build/outputs/apk/release/app-release.apk ]; then
    adb install -r app/build/outputs/apk/release/app-release.apk
fi
