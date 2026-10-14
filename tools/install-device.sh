#!/usr/bin/env bash
#
# Install the debug APK on a connected device, driving vivo's installer dialog.
#
# vivo shows a dialog adb cannot dismiss on its own:
#   - a checkbox "已了解应用的风险检测结果" (acknowledge the risk scan), and
#   - a "继续安装" button, which refuses with "请先确认…" until the box is ticked.
# Left alone the install aborts with
# INSTALL_FAILED_ABORTED: User rejected permissions, which reads like a policy
# refusal but is really just an untapped checkbox.
#
# Usage:
#   tools/install-device.sh                 # uses ANDROID_SERIAL, default APK
#   tools/install-device.sh <serial> [apk]
#
set -uo pipefail

SERIAL="${1:-${ANDROID_SERIAL:-}}"
APK="${2:-app/build/outputs/apk/debug/app-debug.apk}"

if [ -z "$SERIAL" ]; then
  SERIAL="$(adb devices | awk 'NR>1 && $2=="device"{print $1; exit}')"
fi
[ -n "$SERIAL" ] || { echo "no device (set ANDROID_SERIAL)"; exit 1; }
[ -f "$APK" ] || { echo "APK not found: $APK"; exit 1; }

echo "device: $SERIAL"
echo "apk:    $APK"

adb -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
adb -s "$SERIAL" push "$APK" /data/local/tmp/starveil.apk >/dev/null

OUT="$(mktemp)"
adb -s "$SERIAL" shell pm install -r -d /data/local/tmp/starveil.apk >"$OUT" 2>&1 &
INSTALL_PID=$!

# Prints the centre of the node matching $1 ("check" or "confirm"), if any.
find_target() {
  adb -s "$SERIAL" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb -s "$SERIAL" pull /sdcard/ui.xml /tmp/starveil-ui.xml >/dev/null 2>&1
  [ -f /tmp/starveil-ui.xml ] || return 1
  MODE="$1" python3 - <<'PY'
import os, re, sys
try:
    xml = open('/tmp/starveil-ui.xml', encoding='utf-8', errors='replace').read()
except OSError:
    raise SystemExit
mode = os.environ['MODE']
confirm_labels = ('继续安装', '安装', '继续', 'Install', 'Continue')

def centre(node):
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
    if not b:
        return None
    x1, y1, x2, y2 = map(int, b.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2

for node in re.finditer(r'<node[^>]*?>', xml):
    n = node.group(0)
    if mode == 'check':
        # The acknowledgement box: tick it before the button will work.
        if 'checkable="true"' in n and 'checked="false"' in n:
            c = centre(n)
            if c:
                print(*c)
                raise SystemExit
    else:
        if 'checkable="true"' in n:
            continue
        text = re.search(r'text="([^"]*)"', n)
        if text and any(l in text.group(1) for l in confirm_labels):
            c = centre(n)
            if c:
                print(*c)
                raise SystemExit
PY
}

for _ in $(seq 1 30); do
  kill -0 "$INSTALL_PID" 2>/dev/null || break
  # Checkbox first: pressing the button before it is ticked just re-prompts.
  for mode in check confirm; do
    coord="$(find_target "$mode")"
    if [ -n "$coord" ]; then
      echo "  $mode -> $coord"
      # shellcheck disable=SC2086
      adb -s "$SERIAL" shell input tap $coord >/dev/null 2>&1
      sleep 1
      break
    fi
  done
  sleep 1
done

wait "$INSTALL_PID"
RESULT="$(tail -1 "$OUT")"
rm -f "$OUT"
echo "$RESULT"

if [ "$RESULT" = "Success" ]; then
  adb -s "$SERIAL" shell rm -f /data/local/tmp/starveil.apk >/dev/null 2>&1
  exit 0
fi
exit 1
