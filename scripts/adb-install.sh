#!/usr/bin/env bash
# Install an APK on a vivo device, answering the sideload confirmation.
#
# vivo's package installer puts a security notice in front of every install
# from outside its own store and refuses to proceed until "已了解应用的风险检测结果"
# is ticked and 继续安装 is tapped. Until then `adb install` just sits there and
# eventually reports
#
#     INSTALL_FAILED_ABORTED: User rejected permissions
#
# which reads like a broken or unsigned APK and sends you looking in entirely
# the wrong place. (It is also what you get when the screen is merely locked.)
#
# Two rules this follows, both learned the hard way:
#
#   1. A checkbox is only tapped when it reports itself UNCHECKED. Matching on
#      its wording instead meant an already-ticked box was tapped again and
#      un-ticked — it flipped every pass.
#   2. Ticking and pressing the button happen on separate passes. vivo keeps the
#      button inert until the tick registers, so pressing immediately just
#      burns a tap and leaves the dialog up.
#
# Usage:
#   adb-install.sh app.apk              # fresh install
#   adb-install.sh -r -d app.apk        # reinstall, allow downgrade
set -uo pipefail

if [ $# -lt 1 ]; then
  sed -n '2,24p' "$0"
  exit 1
fi

if [ -n "${ANDROID_SERIAL:-}" ]; then export ANDROID_SERIAL; fi

APK="${!#}"
INSTALL_ARGS=("${@:1:$#-1}")

if [ ! -f "$APK" ]; then
  echo "no such apk: $APK" >&2
  exit 2
fi

LOG=$(mktemp)
adb install "${INSTALL_ARGS[@]}" "$APK" >"$LOG" 2>&1 &
INSTALL_PID=$!

# Only the package installer's own windows are ours to touch. Without this the
# loop taps whatever happens to be on screen — on the first run it pressed two
# controls on a vivo Settings page that happened to be behind the dialog, which
# is the same trap scripts/ui.sh documents.
installer_foreground() {
  adb shell dumpsys window 2>/dev/null | grep -m1 mCurrentFocus | grep -q "packageinstaller"
}

# What the installer has on screen right now: "TICK x y", "TAP x y", or nothing.
#
# TICK is emitted only for a box that is checkable and *not* checked. TAP is
# emitted only when no box needs ticking, so the two never happen in one pass.
next_action() {
  installer_foreground || return 0
  adb shell uiautomator dump /sdcard/adb-install.xml >/dev/null 2>&1
  adb shell cat /sdcard/adb-install.xml 2>/dev/null | python3 -c '
import re, sys

x = sys.stdin.read()
BUTTON_WORDS = ("继续安装", "安装", "确定", "允许", "继续")

unticked = None
button = None

for m in re.finditer(r"<node[^>]*>", x):
    n = m.group(0)

    def g(key):
        mm = re.search(key + r"=\"([^\"]*)\"", n)
        return mm.group(1) if mm else ""

    bounds = re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", n)
    if not bounds:
        continue

    rid = g("resource-id")
    label = (g("text") + " " + g("content-desc")).strip()
    x1, y1, x2, y2 = map(int, bounds.groups())
    centre = ((x1 + x2) // 2, (y1 + y2) // 2)

    # The risk acknowledgement. Keyed on the box reporting itself UNCHECKED,
    # never on its wording: matching the words meant an already-ticked box got
    # tapped again and un-ticked, so it flipped on every pass.
    if g("checkable") == "true" or rid.endswith("deleted_file_state_cb"):
        if g("checked") != "true" and unticked is None:
            unticked = centre
        continue

    # The dialog positive button. android:id/button1 is the framework id for it
    # and outlives a re-wording; the label is only a fallback.
    if rid == "android:id/button1" or any(w in label for w in BUTTON_WORDS):
        if button is None:
            button = centre

if unticked:
    print(f"TICK {unticked[0]} {unticked[1]}")
elif button:
    print(f"TAP {button[0]} {button[1]}")
'
}

answered=0
last_tap_at=0
last_tap_at_xy=""

for _ in $(seq 1 150); do
  kill -0 "$INSTALL_PID" 2>/dev/null || break

  action=$(next_action)
  if [ -n "$action" ]; then
    read -r verb cx cy <<<"$action"
    now=$(date +%s)

    # The same control, twice in a row, within a couple of seconds is the loop
    # reading a stale tree and pressing again — not a new prompt. Without this
    # a single install reported 62 "prompts" answered, all of them the same
    # button.
    if [ "$cx $cy" = "$last_tap_at_xy" ] && [ $((now - last_tap_at)) -lt 3 ]; then
      sleep 1
      continue
    fi

    if [ "$verb" = "TICK" ]; then
      adb shell input tap "$cx" "$cy" >/dev/null 2>&1
      echo "  ticked the risk notice at ($cx,$cy)"
    else
      adb shell input tap "$cx" "$cy" >/dev/null 2>&1
      echo "  tapped the install button at ($cx,$cy)"
    fi
    answered=$((answered + 1))
    last_tap_at=$now
    last_tap_at_xy="$cx $cy"

    # Give the dialog a moment to react before looking again; without this the
    # next dump reads the pre-tap tree and we act on stale state.
    sleep 1.5
  else
    sleep 0.8
  fi
done

wait "$INSTALL_PID"
STATUS=$?
cat "$LOG"
rm -f "$LOG"

if [ "$STATUS" -eq 0 ]; then
  echo "installed (answered $answered installer prompt(s))"
else
  echo "install failed (answered $answered installer prompt(s))" >&2
  if grep -q "User rejected permissions" "$LOG" 2>/dev/null; then
    echo "  note: that message also appears when the screen is locked" >&2
  fi
fi
exit "$STATUS"
