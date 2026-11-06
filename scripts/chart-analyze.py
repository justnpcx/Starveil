#!/usr/bin/env python3
"""Frame distribution and Compose recomposition counts for one chart run.

Inputs are the two files the measurement script leaves behind:
  - `dumpsys gfxinfo <pkg> framestats` (the raw table, not the summary);
  - `perfetto --txt` output for the same 35 s window.

The frame interval is the difference between consecutive `IntendedVsync`
values, the same definition `docs/17-benchmark-report.md` and
`docs/11-device-verification.md` use. A frame over 20 ms is counted as jank;
the 16.7 ms count is printed beside it because the older reports use it and the
two numbers are not interchangeable.

Usage: chart-analyze.py <gfxinfo.txt> <chart.txt> <out-dir> <window-seconds>
"""

import csv
import re
import statistics
import sys
from pathlib import Path

MARK = "---PROFILEDATA---"


def frames(path: Path) -> list[float]:
    lines = path.read_text(errors="replace").splitlines()
    try:
        start = lines.index(MARK) + 1
        end = lines.index(MARK, start)
    except ValueError:
        return []
    reader = csv.DictReader(lines[start:end])
    rows = [row for row in reader if row.get("IntendedVsync", "").isdigit()]
    vsync = [int(row["IntendedVsync"]) for row in rows]
    return [(b - a) / 1_000_000 for a, b in zip(vsync, vsync[1:]) if b > a]


def slices(path: Path) -> tuple[int, int]:
    text = path.read_text(errors="replace")
    return (
        len(re.findall(r"Recomposer:recompose", text)),
        len(re.findall(r"Compose:recompose", text)),
    )


def percentile(values: list[float], p: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, int((len(ordered) - 1) * p))]


def main() -> int:
    if len(sys.argv) != 5:
        print(__doc__, file=sys.stderr)
        return 2
    gfx, trace, out = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])
    window = float(sys.argv[4])
    intervals = frames(gfx)
    recomposer, compose = slices(trace)

    jank20 = sum(1 for value in intervals if value > 20.0)
    jank167 = sum(1 for value in intervals if value > 16.7)
    summary = {
        "frames": len(intervals),
        "windowSeconds": window,
        "fps": round(len(intervals) / window, 1) if window else 0,
        "jankOver20ms": jank20,
        "jankOver20Pct": round(100 * jank20 / len(intervals), 1) if intervals else 0,
        "jankOver16_7ms": jank167,
        "jankOver16_7Pct": round(100 * jank167 / len(intervals), 1) if intervals else 0,
        "p50Ms": round(percentile(intervals, 0.50), 1),
        "p90Ms": round(percentile(intervals, 0.90), 1),
        "p95Ms": round(percentile(intervals, 0.95), 1),
        "p99Ms": round(percentile(intervals, 0.99), 1),
        "maxMs": round(max(intervals), 1) if intervals else 0,
        "meanMs": round(statistics.fmean(intervals), 1) if intervals else 0,
        "recomposerRecompose": recomposer,
        "composeRecompose": compose,
        "recomposerPerSecond": round(recomposer / window, 2) if window else 0,
        "composePerSecond": round(compose / window, 2) if window else 0,
    }

    out.mkdir(parents=True, exist_ok=True)
    with (out / "summary.csv").open("w") as handle:
        handle.write("metric,value\n")
        for key, value in summary.items():
            handle.write(f"{key},{value}\n")
    for key, value in summary.items():
        print(f"{key}: {value}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
