#!/usr/bin/env bash
#
# Measure what the per-row health curve costs, with and without the curve.
#
#   ./scripts/chart-measure.sh <serial> 6 with-curve
#   ./scripts/chart-measure.sh <serial> 0  no-curve
#
# The node page's cards carry `LatencySparkline` (core/ui/.../NodeHealthChart.kt),
# not the larger `NodeHealthChart`; the latter is only in the dashboard's
# details dialog in 0.9.26. This script measures the per-row composable that
# the scroll actually crosses. `samples=0` seeds the same 1000 rows with no
# history: the row still composes, the Canvas has nothing to draw. That is the
# closest controlled "chart hidden" the current UI allows without changing it.
#
# Trace: 35 s of `gfx`/`view`/`input` ftrace with the app's atrace tags, written
# as text (`--txt`) so `Recomposer:recompose` and `Compose:recompose` slices can
# be counted without trace_processor. Frames: `dumpsys gfxinfo framestats` over
# the same scroll, parsed by scripts/chart-analyze.py.
#
# The device must have the benchmark variant installed (the seeder lives there).

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_ID="com.justnpcx.starveil"
SERIAL="${1:?usage: chart-measure.sh <serial> <samples 6|0> <label>}"
SAMPLES="${2:?samples: 6 or 0}"
LABEL="${3:-run}"
OUT="$ROOT/build/chart/${SERIAL//[:.]/_}/$LABEL"
mkdir -p "$OUT"

adb_() { timeout 15 adb -s "$SERIAL" "$@"; }
shell() { adb_ shell "$@"; }

echo "== seed 1000 nodes, samples=$SAMPLES =="
shell pm clear "$APP_ID" >/dev/null
shell am start -n "$APP_ID/.MainActivity" >/dev/null
sleep 2
shell am broadcast --include-stopped-packages -a "$APP_ID.benchmark.SEED" \
    -n "$APP_ID/.benchmark.SeedReceiver" --ei count 1000 --ei samples "$SAMPLES" >/dev/null

echo "== open Nodes =="
for attempt in 1 2 3 4 5; do
    shell input tap 720 1026 >/dev/null 2>&1 || true
    sleep 2
    if shell 'uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; cat /sdcard/ui.xml' 2>/dev/null | grep -q '·'; then
        break
    fi
    if (( attempt == 5 )); then echo "the node rows never appeared" >&2; exit 1; fi
done

echo "== trace =="
cat > /tmp/chart.cfg <<'EOF'
buffers { size_kb: 65536 }
data_sources {
  config {
    name: "linux.ftrace"
    ftrace_config {
      ftrace_events: "sched/sched_switch"
      ftrace_events: "power/suspend_resume"
      atrace_categories: "gfx"
      atrace_categories: "view"
      atrace_categories: "input"
      atrace_apps: "com.justnpcx.starveil"
    }
  }
}
duration_ms: 35000
EOF
adb_ push /tmp/chart.cfg /data/local/tmp/chart.cfg >/dev/null

shell dumpsys gfxinfo "$APP_ID" reset >/dev/null
# Perfetto blocks for 35 s; keep it in the background while the swipes run.
adb_ shell perfetto -c /data/local/tmp/chart.cfg --txt -o /data/local/tmp/chart.txt \
    >/dev/null 2>&1 &
TRACE_PID=$!
sleep 2

# Ten full-screen drags with a settle between them: the list is 1000 rows and
# the drag is a follow gesture, not a fling (60 move events).
for _ in $(seq 1 20); do
    shell input swipe 960 800 960 300 60 >/dev/null 2>&1 || true
    sleep 0.7
done
wait "$TRACE_PID" || true

shell dumpsys gfxinfo "$APP_ID" framestats > "$OUT/gfxinfo.txt"
adb_ pull /data/local/tmp/chart.txt "$OUT/chart.txt" >/dev/null
python3 "$ROOT/scripts/chart-analyze.py" "$OUT/gfxinfo.txt" "$OUT/chart.txt" "$OUT" 35

echo "raw data: $OUT"
