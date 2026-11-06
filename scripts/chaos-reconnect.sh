#!/usr/bin/env bash
#
# Run the fifty-round chaos harness on a connected device.
#
# The node is a Shadowsocks server on the lab host. Its address, port and
# password are environment variables on purpose: they are test infrastructure,
# not source. Nothing here is committed with a real address.
#
#   CHAOS_HOST=192.0.2.10 CHAOS_PORT=18388 \
#   CHAOS_METHOD=aes-256-gcm CHAOS_PASSWORD=test \
#   ./scripts/chaos-reconnect.sh [serial]
#
# The device must be the benchmark variant of the app plus the benchmark test
# APK. Build them with:
#   ./gradlew :app:assembleBenchmark :benchmark:assembleBenchmark
#
# Output: build/chaos/<serial>/ with the round CSV, the kernel-state CSV and
# logcat for any round that timed out. The script exits non-zero when the test
# fails, which is the point: a red run is a result, not a reason to retry.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_ID="com.justnpcx.starveil"
SERIAL="${1:-}"
ADB=(adb)
if [[ -n "$SERIAL" ]]; then
    ADB=(adb -s "$SERIAL")
fi

: "${CHAOS_HOST:?set CHAOS_HOST to the lab node address}"
: "${CHAOS_PORT:?set CHAOS_PORT}"
: "${CHAOS_METHOD:?set CHAOS_METHOD, e.g. aes-256-gcm}"
: "${CHAOS_PASSWORD:?set CHAOS_PASSWORD}"
NAME="${CHAOS_NAME:-Lab SS}"

OUT="$ROOT/build/chaos/${SERIAL:-default}"
mkdir -p "$OUT"

echo "== device =="
"${ADB[@]}" shell 'getprop ro.product.model; getprop ro.build.version.sdk; getprop ro.product.cpu.abi'

echo "== install benchmark variant =="
APP_APK="$ROOT/app/build/outputs/apk/benchmark/app-armeabi-v7a-benchmark.apk"
TEST_APK="$ROOT/benchmark/build/outputs/apk/benchmark/benchmark-benchmark.apk"
[[ -f "$APP_APK" ]] || { echo "missing $APP_APK; run ./gradlew :app:assembleBenchmark" >&2; exit 1; }
[[ -f "$TEST_APK" ]] || { echo "missing $TEST_APK; run ./gradlew :benchmark:assembleBenchmark" >&2; exit 1; }

# The benchmark variant is debug-signed, so it cannot be installed over the
# release build. On the lab device that costs nothing; on any other device it
# would, which is why the script names the package it is about to replace.
if "${ADB[@]}" shell pm path "$APP_ID" >/dev/null 2>&1; then
    echo "uninstalling $APP_ID (data is not preserved across the signature change)"
    "${ADB[@]}" uninstall "$APP_ID" >/dev/null
fi
"${ADB[@]}" install -r "$APP_APK" >/dev/null
"${ADB[@]}" install -r "$TEST_APK" >/dev/null

echo "== seed the lab node =="
"${ADB[@]}" shell am start -n "$APP_ID/.MainActivity" >/dev/null
sleep 2
"${ADB[@]}" shell am broadcast \
    -a "$APP_ID.benchmark.LAB_NODE" \
    -n "$APP_ID/.benchmark.LabNodeReceiver" \
    --es host "$CHAOS_HOST" \
    --ei port "$CHAOS_PORT" \
    --es method "$CHAOS_METHOD" \
    --es password "$CHAOS_PASSWORD" \
    --es name "$NAME" \
    --es killSwitch block_on_disconnect

echo "== connect once (the first connect also asks for VPN consent) =="
"${ADB[@]}" shell am start -n "$APP_ID/.automation.AutomationActivity" -a "$APP_ID.action.START" >/dev/null
for _ in $(seq 1 40); do
    if "${ADB[@]}" shell dumpsys activity services "$APP_ID" 2>/dev/null | grep -q StarveilVpnService; then
        break
    fi
    sleep 1
done
# A consent dialog is a human decision; the script cannot press it for you.
if ! "${ADB[@]}" shell dumpsys activity services "$APP_ID" 2>/dev/null | grep -q StarveilVpnService; then
    echo "the VpnService is not running: grant VPN consent on the device, then rerun" >&2
    exit 1
fi

echo "== run =="
COMPONENT="$("${ADB[@]}" shell pm list instrumentation \
    | grep "target=$APP_ID.benchmark" \
    | sed 's/^instrumentation://; s/[[:space:]].*//' \
    | head -1)"
[[ -n "$COMPONENT" ]] || { echo "no instrumentation for $APP_ID.benchmark" >&2; exit 1; }

set +e
"${ADB[@]}" shell am instrument -w -e class com.justnpcx.starveil.benchmark.ChaosReconnectTest "$COMPONENT" \
    | tee "$OUT/instrument.txt"
RC=${PIPESTATUS[0]}
set -e

echo "== pull data =="
# Android 11 scoped storage hides Android/data from the shell, so the harness
# logs every row to logcat as well as to its own files. logcat is the channel
# that survives; the CSV is derived from it and the raw log is kept beside it.
"${ADB[@]}" logcat -d -s StarveilChaos -s StarveilChaosRound -s StarveilChaosSummary \
    > "$OUT/logcat-chaos.txt"
python3 "$ROOT/scripts/chaos-parse.py" "$OUT/logcat-chaos.txt" "$OUT"

echo "== summary =="
[[ -f "$OUT/chaos-summary.csv" ]] && cat "$OUT/chaos-summary.csv" || true
echo "raw data: $OUT"
exit "$RC"
