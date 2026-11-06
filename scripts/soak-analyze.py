#!/usr/bin/env python3
"""Read the soak CSVs and apply the two assertions the task book names.

No smoothing, no dropping outliers, no picking a window that passes. The
numbers printed here are means of the rows in the CSV; the CSV is the raw data.

Usage: soak-analyze.py <build/soak/<serial>>
"""

import csv
import math
import sys
from pathlib import Path

LIMIT_KB = 8 * 1024


def read(path: Path) -> list[dict[str, str]]:
    if not path.exists():
        return []
    with path.open(newline="") as handle:
        return list(csv.DictReader(handle))


def mean(values: list[int]) -> float:
    return sum(values) / len(values) if values else 0.0


def slope_and_r(points: list[tuple[int, int]]) -> tuple[float, float]:
    if len(points) < 2:
        return 0.0, 0.0
    n = len(points)
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    mx, my = mean(xs), mean(ys)
    sxx = sum((x - mx) ** 2 for x in xs)
    syy = sum((y - my) ** 2 for y in ys)
    sxy = sum((x - mx) * (y - my) for x, y in points)
    if sxx == 0 or syy == 0:
        return 0.0, 0.0
    slope = sxy / sxx
    return slope, sxy / math.sqrt(sxx * syy)


def stage_a(root: Path) -> list[str]:
    rows = read(root / "stage-a.csv")
    connected = [
        (int(row["cycle"]), int(row["pssKb"]))
        for row in rows
        if row.get("phase") == "connected" and row.get("pssKb")
    ]
    lines = ["stage A: connect/disconnect cycles"]
    if len(connected) < 40:
        lines.append(f"  rows={len(connected)} — not enough for first/last 20")
        return lines
    first = [pss for _, pss in connected[:20]]
    last = [pss for _, pss in connected[-20:]]
    delta = mean(last) - mean(first)
    slope, r = slope_and_r(connected)
    lines += [
        f"  samples={len(connected)}",
        f"  first20 mean={mean(first) / 1024:.1f} MB",
        f"  last20  mean={mean(last) / 1024:.1f} MB",
        f"  delta={delta / 1024:+.1f} MB (limit {LIMIT_KB / 1024:.0f} MB) "
        f"{'PASS' if abs(delta) <= LIMIT_KB else 'FAIL'}",
        f"  linear slope={slope / 1024:+.3f} MB/cycle, r={r:+.2f}",
        f"  monotonic-increase signal: {'YES' if slope > 0 and r > 0.5 else 'no'}",
    ]
    residual = root / "stage-a-residual.txt"
    if residual.exists():
        text = residual.read_text().strip().replace("\n", " | ")
        lines.append(f"  residual: {text}")
    return lines


def stage_b(root: Path) -> list[str]:
    rows = read(root / "stage-b.csv")
    samples = [
        (int(row["sample"]), int(row["pssKb"]))
        for row in rows
        if row.get("pssKb")
    ]
    lines = ["stage B: 24 h resident"]
    if len(samples) < 48:
        lines.append(f"  rows={len(samples)} — not enough for first/last 4 h")
        return lines
    first = [pss for _, pss in samples[:24]]
    last = [pss for _, pss in samples[-24:]]
    delta = mean(last) - mean(first)
    slope, r = slope_and_r(samples)
    lines += [
        f"  samples={len(samples)}",
        f"  first 4 h mean={mean(first) / 1024:.1f} MB",
        f"  last  4 h mean={mean(last) / 1024:.1f} MB",
        f"  drift={delta / 1024:+.1f} MB (limit ±{LIMIT_KB / 1024:.0f} MB) "
        f"{'PASS' if abs(delta) <= LIMIT_KB else 'FAIL'}",
        f"  linear slope={slope / 1024:+.3f} MB/sample, r={r:+.2f}",
    ]
    return lines


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__, file=sys.stderr)
        return 2
    root = Path(sys.argv[1])
    lines = stage_a(root) + [""] + stage_b(root)
    text = "\n".join(lines) + "\n"
    (root / "soak-summary.txt").write_text(text)
    print(text, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
