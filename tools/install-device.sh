#!/usr/bin/env bash
#
# Install the debug APK on a connected device, driving vivo's installer dialog.
#
# vivo gates adb installs behind a dialog adb cannot dismiss on its own:
#   - a checkbox "已了解应用的风险检测结果" (acknowledge the risk scan), and
#   - a "继续安装" button, which refuses with "请先确认…" until the box is ticked.
# Left alone the install aborts with
# INSTALL_FAILED_ABORTED: User rejected permissions, which reads like a policy
# refusal but is really an untapped checkbox.
#
# It also uninstalls first by default: when the installed build came from a
# different debug key (e.g. a CI artifact) vivo reports "install package
# signature duplicated" and the only way past it is uninstall + reinstall.
#
# Usage:
#   tools/install-device.sh                 # uses ANDROID_SERIAL, default APK
#   tools/install-device.sh <serial> [apk]
#   KEEP_DATA=1 tools/install-device.sh     # in-place upgrade, keep app data
#
set -uo pipefail

SERIAL="${1:-${ANDROID_SERIAL:-}}"
APK="${2:-app/build/outputs/apk/debug/app-debug.apk}"
PKG="com.justnpcx.starveil"
INSTALLER="com.android.packageinstaller"

if [ -z "$SERIAL" ]; then
  SERIAL="$(adb devices | awk 'NR>1 && $2=="device"{print $1; exit}')"
fi
[ -n "$SERIAL" ] || { echo "no device (set ANDROID_SERIAL)"; exit 1; }
[ -f "$APK" ] || { echo "APK not found: $APK"; exit 1; }

echo "device: $SERIAL"
echo "apk:    $APK"

# The matcher lives in its own file: a heredoc inside a function inside a
# command substitution is easy to get subtly wrong, and a stale dump silently
# makes it match the wrong screen.
MATCHER="$(mktemp /tmp/starveil-install-XXXX.py)"
trap 'rm -f "$MATCHER" /tmp/starveil-ui.xml' EXIT
cat >"$MATCHER" <<'PY'
import os, re, sys

MODE = os.environ['MODE']
DUMP = os.environ['DUMP']

# Gate on the node's own package, not on which activity happens to be resumed.
# The plain install dialog comes from com.android.packageinstaller, but the
# signature-conflict one comes from a different vivo component, so a
# foreground-package check misses it and the script taps nothing.
INSTALLER_PKG = re.compile(r'installer|safecenter|iqoo\.secure', re.IGNORECASE)
# Match the button by TEXT only. resource-id .../vbutton_title is shared with
# the '权限' (permissions) button at the top of the same dialog, so matching it
# tapped permissions and dismissed the install.
CONFIRM_LABELS = ('继续安装', '安装', '继续', 'Install', 'Continue')
# Anything that means "get past the conflict" in the vivo dialog.
CONFLICT_LABELS = ('卸载并重新安装', '重新安装', '卸载重装', 'OK')

try:
    xml = open(DUMP, encoding='utf-8', errors='replace').read()
except OSError:
    sys.exit(1)

def attr(node, name):
    m = re.search(name + r'="([^"]*)"', node)
    return m.group(1) if m else None

def centre(node):
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
    if not b:
        return None
    x1, y1, x2, y2 = map(int, b.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2

check = None
confirm = None
for node in re.finditer(r'<node[^>]*?>', xml):
    n = node.group(0)
    pkg = attr(n, 'package') or ''
    if not INSTALLER_PKG.search(pkg):
        continue
    # The acknowledgement box: tick it before the button will work.
    if check is None and 'checkable="true"' in n and 'checked="false"' in n:
        check = centre(n)
        continue
    if 'checkable="true"' in n:
        continue
    text = attr(n, 'text') or ''
    if confirm is None and text in CONFIRM_LABELS + CONFLICT_LABELS:
        confirm = centre(n)

if MODE == 'check':
    picked = check
elif MODE == 'confirm':
    picked = confirm
else:
    # One dump, both decisions: the checkbox first, the button after.
    picked, label = (check, 'check') if check else (confirm, 'confirm')
    if picked:
        print(label, *picked)
        sys.exit(0)
    sys.exit(1)

if picked:
    print(*picked)
    sys.exit(0)
sys.exit(1)
PY

dump_ui() {
  rm -f /tmp/starveil-ui.xml
  adb -s "$SERIAL" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || return 1
  adb -s "$SERIAL" pull /sdcard/ui.xml /tmp/starveil-ui.xml >/dev/null 2>&1 || return 1
  [ -s /tmp/starveil-ui.xml ]
}

# True only while the installer owns the screen.
is_installer_foreground() {
  adb -s "$SERIAL" shell dumpsys activity activities 2>/dev/null \
    | tr -d '\r' \
    | grep -m1 'topResumedActivity' \
    | grep -qE 'installer|safecenter|iqoo\.secure'
}

find_target() {
  dump_ui || return 1
  MODE="$1" DUMP=/tmp/starveil-ui.xml python3 "$MATCHER"
}

adb -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
adb -s "$SERIAL" push "$APK" /data/local/tmp/starveil.apk >/dev/null

# A previous run can leave the installer resumed but invisible, which makes
# every later install hang until it is cleared.
adb -s "$SERIAL" shell am force-stop "$INSTALLER" >/dev/null 2>&1
sleep 1

if [ "${KEEP_DATA:-0}" != "1" ] && adb -s "$SERIAL" shell pm path "$PKG" 2>/dev/null | grep -q package:; then
  echo "uninstalling $PKG (avoids the signature-duplicate dialog)"
  adb -s "$SERIAL" uninstall "$PKG" >/dev/null 2>&1
  sleep 2
fi

run_install() {
  OUT="$(mktemp)"
  adb -s "$SERIAL" shell pm install -r -d /data/local/tmp/starveil.apk >"$OUT" 2>&1 &
  INSTALL_PID=$!

  for _ in $(seq 1 60); do
    kill -0 "$INSTALL_PID" 2>/dev/null || break
    # The matcher only ever returns nodes owned by the installer, so it is
    # safe to dump every iteration regardless of what is in front.
    line="$(MODE=auto find_target auto)"
    if [ -n "$line" ]; then
      read -r action x y <<<"$line"
      echo "  $action -> $x $y"
      adb -s "$SERIAL" shell input tap "$x" "$y" >/dev/null 2>&1
      sleep 1
    fi
    sleep 1
  done

  wait "$INSTALL_PID"
  RESULT="$(tail -1 "$OUT")"
  rm -f "$OUT"
}

run_install
echo "$RESULT"

# A build signed with a different debug key (a CI artifact, say) makes vivo
# report "install package signature duplicated". Nothing but an uninstall gets
# past it, so do it here rather than leaving it to the user.
if printf '%s' "$RESULT" | grep -qE 'UPDATE_INCOMPATIBLE|signatures do not match|ALREADY_EXISTS'; then
  echo "signature conflict -> uninstalling and retrying"
  adb -s "$SERIAL" uninstall "$PKG" >/dev/null 2>&1
  sleep 2
  run_install
  echo "$RESULT"
fi

if [ "$RESULT" = "Success" ]; then
  adb -s "$SERIAL" shell rm -f /data/local/tmp/starveil.apk >/dev/null 2>&1
  exit 0
fi
exit 1
