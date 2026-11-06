#!/usr/bin/env bash
#
# Host-driven chaos run for a device whose adb link rides the same radio the
# test is about to switch off.
#
# The instrumentation version (ChaosReconnectTest) is the regression test: it
# owns the whole run in one process. This script exists because on the lab TV
# the adb transport itself dies with the radio, and an `am instrument` session
# that loses its host can take the test process with it. Here every round is
# bracketed by a MARK broadcast and the radio is brought back by a detached
# one-shot on the device, so the host only has to reconnect and read.
#
#   CHAOS_ROUNDS=50 ./scripts/chaos-run.sh [serial]
#
# Preconditions: the benchmark variant is installed, the lab node is selected
# and connected, and Kill Switch is BLOCK_ON_DISCONNECT. The script refuses to
# start when the app is not already connected, so a failed connect is not
# mistaken for a slow recovery.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_ID="com.justnpcx.starveil"
SERIAL="${1:-${CHAOS_SERIAL:-}}"
ROUNDS="${CHAOS_ROUNDS:-50}"
HOLD_MS="${CHAOS_HOLD_MS:-2500}"
GATE_MS=1200
ABORT_MS=10000
if [[ -z "$SERIAL" ]]; then
    echo "usage: $0 <serial>   (or set CHAOS_SERIAL)" >&2
    exit 2
fi
OUT="$ROOT/build/chaos/host-${SERIAL//[:.]/_}"
mkdir -p "$OUT"

adb_() { timeout 10 adb -s "$SERIAL" "$@"; }
shell() { adb_ shell "$@"; }

wait_online() {
    local deadline=$((SECONDS + 30))
    while (( SECONDS < deadline )); do
        timeout 5 adb connect "$SERIAL" >/dev/null 2>&1 || true
        if timeout 5 adb devices | grep -q "$SERIAL[[:space:]]*device"; then
            return 0
        fi
        sleep 1
    done
    return 1
}

mark() {
    shell am broadcast -a "$APP_ID.benchmark.CHAOS_MARK" \
        -n "$APP_ID/.benchmark.ChaosStateReceiver" --es label "$1" >/dev/null 2>&1 || true
}

ui_connected() {
    shell 'uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; cat /sdcard/ui.xml' 2>/dev/null \
        | grep -q 'text="CONNECTED"'
}

service_running() {
    shell dumpsys activity services "$APP_ID" 2>/dev/null | grep -q StarveilVpnService
}

notification_present() {
    shell dumpsys notification --noredact 2>/dev/null | grep -q "pkg=$APP_ID"
}

wait_online || { echo "device did not come online" >&2; exit 1; }
if ! ui_connected; then
    echo "the app is not connected; connect it before the run" >&2
    exit 1
fi

shell am broadcast -a "$APP_ID.benchmark.CHAOS_START" \
    -n "$APP_ID/.benchmark.ChaosStateReceiver" >/dev/null 2>&1

# Safety net: whatever happens to the per-round one-shot, the radio comes back
# every five seconds for the length of the run.
shell 'setsid sh -c "i=0; while [ \$i -lt 2400 ]; do sleep 5; cmd wifi set-wifi-enabled enabled; i=\$((i+1)); done" >/dev/null 2>&1 &' >/dev/null 2>&1 || true

echo "round,ui,service,notification,epochMs" > "$OUT/host-rounds.csv"
failures=0
for (( round = 1; round <= ROUNDS; round++ )); do
    mark "r${round}:start"

    # The one-shot is started before the radio goes down, so its MARK lands in
    # the same clock as the kernel transitions and its enable is not delayed by
    # the host's reconnect.
    shell "setsid sh -c 'sleep $((HOLD_MS / 1000)); am broadcast -a $APP_ID.benchmark.CHAOS_MARK -n $APP_ID/.benchmark.ChaosStateReceiver --es label r${round}:wifi_enable; cmd wifi set-wifi-enabled enabled' >/dev/null 2>&1 &" >/dev/null 2>&1 || true
    shell cmd wifi set-wifi-enabled disabled >/dev/null 2>&1 || true

    if ! wait_online; then
        echo "round $round: host did not reconnect" >&2
        failures=$((failures + 1))
        break
    fi

    recovered=0
    deadline=$((SECONDS + ABORT_MS / 1000))
    while (( SECONDS < deadline )); do
        if ui_connected; then recovered=1; break; fi
        sleep 1
    done
    if (( recovered == 0 )); then
        echo "round $round: not recovered within ${ABORT_MS}ms" >&2
        shell logcat -d -t 800 > "$OUT/logcat-round${round}.txt" 2>/dev/null || true
        failures=$((failures + 1))
        break
    fi

    mark "r${round}:connected"
    ui=1
    service=0; notification=0
    service_running && service=1 || true
    notification_present && notification=1 || true
    echo "$round,$ui,$service,$notification,$(date +%s%3N)" >> "$OUT/host-rounds.csv"
    sleep 2
    if (( round % 5 == 0 )); then echo "  $round/$ROUNDS"; fi
done

shell am broadcast -a "$APP_ID.benchmark.CHAOS_STOP" \
    -n "$APP_ID/.benchmark.ChaosStateReceiver" >/dev/null 2>&1 || true
adb_ logcat -d -s StarveilChaos -s StarveilChaosRound -s StarveilChaosSummary \
    > "$OUT/logcat-chaos.txt" 2>/dev/null || true
python3 "$ROOT/scripts/chaos-parse.py" "$OUT/logcat-chaos.txt" "$OUT"
echo "raw data: $OUT"
exit "$failures"
