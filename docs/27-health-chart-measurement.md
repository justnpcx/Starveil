# Node health chart — measurement plan, and the no-go that stands without it

**Status: measurement not executed; the go/no-go is no-go.** No UI code was
changed and no number below is invented.

## The first finding is about the premise

In 0.9.26, `NodeHealthChart` is not on the node cards. It is used once, in the
dashboard's health-details dialog (`feature/dashboard/.../DashboardScreen.kt`,
the `AlertDialog`). The per-row curve the node list scrolls across is
`LatencySparkline`, defined beside it in
`core/ui/.../NodeHealthChart.kt`; both draw the same trace with the same
`DrawScope` helpers. There is no card in the list that contains
`NodeHealthChart`.

So the task book's scenario one ("节点页滚动经过带 NodeHealthChart 的卡片") cannot
be measured as written. The script measures the composable that is actually
there: the per-row sparkline. The report says so instead of silently measuring a
different thing and calling it the same.

## What was built

| file | what it is |
|---|---|
| `scripts/chart-measure.sh` | Seeds 1000 rows (with or without a latency history), opens Nodes, starts a 35 s Perfetto trace with `gfx`/`view`/`input` + the app's atrace tags, drives 20 full-screen drags, then reads `dumpsys gfxinfo framestats`. |
| `scripts/chart-analyze.py` | Frame intervals from `IntendedVsync`, jank over 20 ms and over 16.7 ms, p50/p90/p95/p99/max, and the `Recomposer:recompose` / `Compose:recompose` slice counts per second. |
| `SeedReceiver` (`samples` extra) | The benchmark-variant seeder now accepts `--ei samples 0`: the same 1000 rows, no history. The row still composes; the Canvas has nothing to draw. That is the controlled "chart hidden" side without touching production UI. |

## Method

1. `pm clear`, launch, broadcast `SEED` with 1000 rows and `samples=6`.
2. Open Nodes; wait for the rows.
3. `dumpsys gfxinfo reset`; start a 35 s Perfetto trace; drag the list 20 times
   with a settle between drags (a drag, not a fling, so the list follows).
4. `dumpsys gfxinfo framestats` over the same window.
5. Repeat with `samples=0`; the difference is the curve's cost.
6. Recomposition: Compose's runtime wraps the pass in
   `android.os.Trace` sections named `Recomposer:recompose` and
   `Compose:recompose` (verified in the 1.9.3 runtime bytecode). `perfetto
   --txt` writes them as text, so they are counted without `trace_processor`.
   These are recomposition **passes**, not per-composable counts; the delta
   between the two seeds is the attribution the task asks for.

## Why it did not run

The app could not be kept in the foreground on the lab TV after the chaos work.
`am start -W` reported `MainActivity` resumed (`TotalTime 1137 ms`) while
`dumpsys window` still reported
`mCurrentFocus=com.amazon.tv.launcher/...HomeActivity_vNext`; a screenshot
confirmed the launcher. A 35 s scroll measurement on a screen that is not the
app would measure the launcher. No trace was taken, so there is no frame or
recomposition number to report.

This is the same device/launcher instability that stopped T1, not a property of
the chart.

## Go / no-go

**No-go.** The only defensible decision without a measurement is not to add an
off-screen `graphicsLayer` cache to a UI that has no measured problem. The
existing 1000-row numbers in `docs/11` (steady scroll 0% jank, p99 10 ms on this
TV) are about the list as a whole; they do not isolate the curve, but they also
do not show a budget being exceeded. A `graphicsLayer` would add a bitmap, a
cache-invalidation rule and a new failure mode for a benefit that has not been
demonstrated.

If a later run shows the curve's delta is material (for example, p95 frame time
or jank rises by more than the run-to-run noise on the same device), the
decision should be revisited on those numbers, not on this paragraph.

## How to run it

```bash
./gradlew :app:assembleBenchmark
./scripts/chart-measure.sh <serial> 6 with-curve
./scripts/chart-measure.sh <serial> 0 no-curve
python3 scripts/chart-analyze.py build/chart/<serial>/with-curve/gfxinfo.txt \
    build/chart/<serial>/with-curve/chart.txt \
    build/chart/<serial>/with-curve 35
```

The two runs must be the same device, the same window, and the same drag script.
Report the difference, not one run on its own.

## What was not touched

No production file. The only app-side change is the benchmark-variant seeder's
`samples` extra. `ConnectionCore`, the design tokens, `NodeHealthChart` and
`LatencySparkline` are byte-for-byte unchanged; no `graphicsLayer` was added.

## Acceptance

| item | result |
|---|---|
| 35 s Perfetto trace, both seeds | not executed |
| frame distribution / jank | not measured |
| recomposition counts per second | not measured |
| go / no-go | **no-go**, on absence of measured benefit |
| acceptance | **not met** |
