#!/usr/bin/env bash
# Scroll the home feed with the remote on the emulator and report main-thread time per frame.
# A first pass loads every row (network), the measured pass scrolls the same rows again.
# Usage: scroll_bench.sh [runs]   — the app must already be installed.
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
export MSYS_NO_PATHCONV=1
PKG=com.matik.reatube
HERE="$(cygpath -w "$(cd "$(dirname "$0")" && pwd)")"
scroll() { for i in $(seq 1 10); do "$ADB" -e shell input keyevent "$1"; sleep 0.6; done; }
for run in $(seq 1 "${1:-2}"); do
  "$ADB" -e shell am force-stop $PKG
  "$ADB" -e shell am start -n $PKG/.MainActivity >/dev/null
  sleep 15
  scroll KEYCODE_DPAD_DOWN; scroll KEYCODE_DPAD_UP; sleep 2      # warm-up: rows and images loaded
  "$ADB" -e shell dumpsys gfxinfo $PKG reset >/dev/null
  scroll KEYCODE_DPAD_DOWN; scroll KEYCODE_DPAD_UP
  printf 'run %s: ' "$run"
  "$ADB" -e shell dumpsys gfxinfo $PKG framestats | python -I "$HERE\\ui_frames.py"
done
