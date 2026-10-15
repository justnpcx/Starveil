#!/usr/bin/env bash
# Small UI automation helper for manual device smoke tests.
#
# Every tap is guarded: it refuses to act unless Starveil is the foreground app,
# because a stray `input tap` lands in whatever app is actually on screen and
# that is how you end up in a wallpaper picker.
#
# Usage:
#   ui.sh dump                 # print visible text + clickable bounds
#   ui.sh find "Nodes"         # print the centre of the first node with that text
#   ui.sh tap "Nodes"          # tap the first node with that text
#   ui.sh tapxy 540 2200       # raw tap (still guarded)
#   ui.sh swipe x1 y1 x2 y2
#   ui.sh back
set -euo pipefail

PKG=com.justnpcx.starveil
# Set ANDROID_SERIAL when more than one device/transport is attached; adb
# otherwise picks the only one.
if [ -n "${ANDROID_SERIAL:-}" ]; then export ANDROID_SERIAL; fi

dump_xml() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb shell cat /sdcard/ui.xml 2>/dev/null
}

foreground_ok() {
  # The keyguard covers the app: dumpsys still reports Starveil as the resumed
  # activity, so a tap would land on the lock screen instead. Check it first.
  if adb shell dumpsys window 2>/dev/null | grep -q "isKeyguardShowing=true"; then
    return 1
  fi
  adb shell "dumpsys activity activities | grep -m1 ResumedActivity" 2>/dev/null | grep -q "$PKG"
}

guard() {
  if ! foreground_ok; then
    echo "REFUSING: $PKG is not foreground" >&2
    exit 3
  fi
}

case "${1:-}" in
  dump)
    dump_xml | python3 -c '
import sys, re
x = sys.stdin.read()
for m in re.finditer(r"<node[^>]*>", x):
    n = m.group(0)
    t = re.search(r"text=\"([^\"]*)\"", n)
    b = re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", n)
    if not b: continue
    x1, y1, x2, y2 = map(int, b.groups())
    label = t.group(1) if t else ""
    if not label.strip(): continue
    print(f"{label!r:52} centre=({(x1+x2)//2},{(y1+y2)//2})")
'
    ;;
  find)
    needle="${2:?needle}"
    dump_xml | python3 -c '
import sys, re
needle = sys.argv[1]
x = sys.stdin.read()
for m in re.finditer(r"<node[^>]*>", x):
    n = m.group(0)
    t = re.search(r"text=\"([^\"]*)\"", n)
    if not t or needle not in t.group(1): continue
    b = re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", n)
    if not b: continue
    x1, y1, x2, y2 = map(int, b.groups())
    print(f"{(x1+x2)//2} {(y1+y2)//2} {t.group(1)!r}")
' "$needle"
    ;;
  tap)
    guard
    read -r cx cy _ <<<"$("$0" find "${2:?needle}" | head -1)"
    [ -n "${cx:-}" ] || { echo "not found: $2" >&2; exit 4; }
    adb shell input tap "$cx" "$cy"
    echo "tapped $2 at ($cx,$cy)"
    ;;
  tapxy)
    guard
    adb shell input tap "${2:?x}" "${3:?y}"
    echo "tapped (${2},${3})"
    ;;
  swipe)
    guard
    adb shell input swipe "${2:?x1}" "${3:?y1}" "${4:?x2}" "${5:?y2}" "${6:-300}"
    ;;
  longpress)
    guard
    read -r cx cy _ <<<"$("$0" find "${2:?needle}" | head -1)"
    [ -n "${cx:-}" ] || { echo "not found: $2" >&2; exit 4; }
    # A long press is a swipe that starts and ends on the same point; the
    # duration is what makes it a long press.
    adb shell input swipe "$cx" "$cy" "$cx" "$cy" 900
    echo "long-pressed $2 at ($cx,$cy)"
    ;;
  back)
    adb shell input keyevent KEYCODE_BACK
    ;;
  *)
    sed -n '2,14p' "$0"
    exit 1
    ;;
esac
