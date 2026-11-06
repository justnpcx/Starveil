#!/usr/bin/env bash
#
# Memory soak for the lab device. Two stages, one script, because the second
# stage's only interesting comparison is against the first stage's baseline.
#
#   ./scripts/soak.sh a          # 100 connect/disconnect cycles, ~15 min
#   ./scripts/soak.sh b          # 24 h resident, one sample every 10 min
#   ./scripts/soak.sh both       # a, then b
#
# Preconditions, all of them deliberate:
#   - the benchmark variant with LabNodeReceiver is installed and a node is
#     selected (scripts/chaos-reconnect.sh does the seeding);
#   - Kill Switch is OFF: stage A asserts that no tun and no VpnService remain
#     after the last disconnect, and BLOCK_ON_DISCONNECT deliberately keeps a
#     tun, so the two cannot be asserted in the same configuration;
#   - the device is plugged in and battery optimisation is off.
#
# Output: build/soak/<serial>/{stage-a.csv,stage-a-residual.txt,...}. Raw rows
# only; the verdict comes from scripts/soak-analyze.py, so the numbers can be
# re-read without trusting this script's arithmetic.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_ID="com.justnpcx.starveil"
STAGE="${1:-a}"
SERIAL="${2:-}"
ADB=(adb)
if [[ -n "$SERIAL" ]]; then
    ADB=(adb -s "$SERIAL")
fi

CYCLES="${SOAK_CYCLES:-100}"
HOURS="${SOAK_HOURS:-24}"
OUT="$ROOT/build/soak/${SERIAL:-default}"
mkdir -p "$OUT"

shell() { "${ADB[@]}" shell "$@"; }

meminfo() {
    # One TOTAL line per dump. Fields: TOTAL PSS: <kb> TOTAL RSS: <kb> ...
    local dump
    dump="$(shell dumpsys meminfo "$APP_ID" 2>/dev/null || true)"
    local pss rss
    pss="$(printf '%s\n' "$dump" | awk '/TOTAL PSS:/ {print $3; exit}')"
    rss="$(printf '%s\n' "$dump" | awk '/TOTAL PSS:/ {for (i=1;i<=NF;i++) if ($i=="RSS:") print $(i+1); exit}')"
    printf '%s,%s\n' "${pss:-0}" "${rss:-0}"
}

service_running() {
    shell dumpsys activity services "$APP_ID" 2>/dev/null | grep -q StarveilVpnService
}

wait_service() {
    local want="$1" timeout="${2:-30}"
    for _ in $(seq 1 "$timeout"); do
        if [[ "$want" == up ]] && service_running; then return 0; fi
        if [[ "$want" == down ]] && ! service_running; then return 0; fi
        sleep 1
    done
    return 1
}

start_tunnel() {
    shell am start -n "$APP_ID/.automation.AutomationActivity" -a "$APP_ID.action.START" >/dev/null 2>&1 || true
}

stop_tunnel() {
    shell am start -n "$APP_ID/.automation.AutomationActivity" -a "$APP_ID.action.STOP" >/dev/null 2>&1 || true
}

stage_a() {
    local csv="$OUT/stage-a.csv"
    echo "cycle,phase,pssKb,rssKb,epochMs" > "$csv"
    echo "stage A: $CYCLES cycles"

    for i in $(seq 1 "$CYCLES"); do
        start_tunnel
        if ! wait_service up 30; then
            echo "cycle $i: the tunnel did not come up" >&2
            return 1
        fi
        sleep 3
        read -r pss rss < <(meminfo | tr ',' ' ')
        echo "$i,connected,$pss,$rss,$(date +%s%3N)" >> "$csv"

        stop_tunnel
        if ! wait_service down 30; then
            echo "cycle $i: the tunnel did not go down" >&2
            return 1
        fi
        sleep 2
        read -r pss rss < <(meminfo | tr ',' ' ')
        echo "$i,disconnected,$pss,$rss,$(date +%s%3N)" >> "$csv"

        if (( i % 10 == 0 )); then echo "  $i/$CYCLES"; fi
    done

    # The assertion is about what is left behind after the last disconnect.
    {
        echo "tun0: $(shell ip link show tun0 2>&1 | head -1)"
        echo "vpnservice: $(service_running && echo running || echo absent)"
        echo "notification: $(shell dumpsys notification --noredact 2>/dev/null | grep -c "pkg=$APP_ID" || true)"
    } > "$OUT/stage-a-residual.txt"
    cat "$OUT/stage-a-residual.txt"
}

stage_b() {
    local csv="$OUT/stage-b.csv"
    local samples=$(( HOURS * 6 ))
    echo "sample,epochMs,pssKb,rssKb,pingOk" > "$csv"
    echo "stage B: $HOURS h, $samples samples every 10 min"

    start_tunnel
    wait_service up 30 || { echo "stage B: tunnel did not come up" >&2; return 1; }

    for i in $(seq 1 "$samples"); do
        sleep 600
        read -r pss rss < <(meminfo | tr ',' ' ')
        local ping=0
        if shell ping -c 1 -W 3 1.1.1.1 >/dev/null 2>&1; then ping=1; fi
        echo "$i,$(date +%s%3N),$pss,$rss,$ping" >> "$csv"
        echo "  sample $i/$samples pss=${pss}kB ping=$ping"
    done
}

case "$STAGE" in
    a) stage_a ;;
    b) stage_b ;;
    both) stage_a; stage_b ;;
    *) echo "usage: $0 [a|b|both] [serial]" >&2; exit 2 ;;
esac

echo "raw data: $OUT"
