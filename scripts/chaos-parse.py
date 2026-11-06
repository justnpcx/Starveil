#!/usr/bin/env python3
"""Turn the chaos logcat into the CSVs the report cites.

The device writes to its own external files directory too, but Android 11's
scoped storage hides that from the shell on this TV. Logcat is readable, so it
is the transport; this script only reshapes it. The raw log is kept next to the
CSVs, so every number can be traced back to one line.

Two producers are understood:

  - ChaosReconnectTest logs one `StarveilChaosRound: round=...` line per round;
  - scripts/chaos-run.sh logs MARKs through ChaosStateReceiver, which appear as
    `StarveilChaos: mark=rN:start elapsed=...` beside the kernel transitions.

Usage: chaos-parse.py <logcat.txt> <out-dir>
"""

import re
import sys
from pathlib import Path

LINE = re.compile(
    r"^\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d+\s+\d+\s+\d+\s+[A-Z]\s+([^:]+):\s?(.*)$"
)
GATE_MS = 1200


def fields(message: str) -> dict[str, str]:
    out: dict[str, str] = {}
    for part in message.split(","):
        if "=" not in part:
            continue
        key, value = part.split("=", 1)
        out[key.strip()] = value.strip()
    return out


def read_host_rows(out: Path) -> dict[int, dict[str, str]]:
    path = out / "host-rounds.csv"
    if not path.exists():
        return {}
    rows: dict[int, dict[str, str]] = {}
    for line in path.read_text().splitlines()[1:]:
        cells = line.split(",")
        if len(cells) >= 4 and cells[0].isdigit():
            rows[int(cells[0])] = {
                "ui": cells[1], "service": cells[2], "notification": cells[3],
            }
    return rows


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__, file=sys.stderr)
        return 2
    src = Path(sys.argv[1])
    out = Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)

    rounds: list[dict[str, str]] = []
    summary: dict[str, str] = {}
    state: list[tuple[int, str, str]] = []
    host = read_host_rows(out)

    for raw in src.read_text(errors="replace").splitlines():
        match = LINE.match(raw)
        if not match:
            continue
        tag, message = match.group(1).strip(), match.group(2).strip()
        if tag == "StarveilChaosRound":
            data = fields(message)
            if "round" in data:
                rounds.append(data)
        elif tag == "StarveilChaosSummary":
            summary.update(fields(message))
        elif tag == "StarveilChaos":
            data = fields(message)
            for event in ("kernel", "blocking", "mark"):
                if event in data and "elapsed" in data:
                    state.append((int(data["elapsed"]), event, data[event]))

    state.sort()
    if rounds:
        columns = [
            "round", "start", "enable", "connected", "selfHeal",
            "consistent", "service", "notification", "leaks",
        ]
        with (out / "chaos-rounds.csv").open("w") as handle:
            handle.write(",".join(columns) + "\n")
            for row in rounds:
                handle.write(",".join(row.get(column, "") for column in columns) + "\n")
        with (out / "chaos-summary.csv").open("w") as handle:
            handle.write("metric,value\n")
            for key, value in summary.items():
                handle.write(f"{key},{value}\n")
        with (out / "kernel-state.csv").open("w") as handle:
            handle.write("elapsedMs,event,value\n")
            for elapsed, event, value in state:
                handle.write(f"{elapsed},{event},{value}\n")
        print(f"rounds={len(rounds)} summary={len(summary)} stateLines={len(state)}")
        return 0

    # Host-driven run: derive the round rows from MARKs and kernel transitions.
    starts: dict[int, int] = {}
    enables: dict[int, int] = {}
    connected: dict[int, int] = {}
    for elapsed, event, value in state:
        if event != "mark":
            continue
        match = re.fullmatch(r"r(\d+):(start|wifi_enable|connected)", value)
        if not match:
            continue
        number, kind = int(match.group(1)), match.group(2)
        {"start": starts, "wifi_enable": enables, "connected": connected}[kind][number] = elapsed

    rows = []
    for number in sorted(starts):
        start = starts[number]
        running = next(
            (elapsed for elapsed, event, value in state
             if event == "kernel" and value == "Running" and elapsed >= start),
            None,
        )
        enable = enables.get(number)
        seen = connected.get(number)
        consistency = host.get(number, {})
        ui = consistency.get("ui", "1" if seen else "0")
        service = consistency.get("service", "")
        notification = consistency.get("notification", "")
        consistent = ui == service == notification if service and notification else ""
        rows.append({
            "round": number,
            "startMs": start,
            "wifiEnableMs": enable if enable is not None else "",
            "kernelRunningMs": running if running is not None else "",
            "selfHealMs": (running - start) if running is not None else "",
            "appRecoveryMs": (running - enable) if running is not None and enable is not None else "",
            "uiSeenMs": (seen - start) if seen is not None else "",
            "consistent": consistent,
            "service": service,
            "notification": notification,
        })

    columns = [
        "round", "startMs", "wifiEnableMs", "kernelRunningMs", "selfHealMs",
        "appRecoveryMs", "uiSeenMs", "consistent", "service", "notification",
    ]
    with (out / "chaos-rounds.csv").open("w") as handle:
        handle.write(",".join(columns) + "\n")
        for row in rows:
            handle.write(",".join(str(row[column]) for column in columns) + "\n")

    values = sorted(row["selfHealMs"] for row in rows if row["selfHealMs"] != "")
    recovery = sorted(row["appRecoveryMs"] for row in rows if row["appRecoveryMs"] != "")
    def pct(items: list[int], p: float) -> int:
        return items[min(len(items) - 1, int((len(items) - 1) * p))] if items else 0

    over = [row["round"] for row in rows if row["selfHealMs"] != "" and row["selfHealMs"] > GATE_MS]
    with (out / "chaos-summary.csv").open("w") as handle:
        handle.write("metric,value\n")
        handle.write(f"rounds,{len(rows)}\n")
        handle.write(f"gateMs,{GATE_MS}\n")
        handle.write(f"overGate,{len(over)}\n")
        handle.write(f"overGateRounds,{' '.join(map(str, over))}\n")
        handle.write(f"p50Ms,{pct(values, 0.50)}\n")
        handle.write(f"p95Ms,{pct(values, 0.95)}\n")
        handle.write(f"maxMs,{values[-1] if values else 0}\n")
        handle.write(f"appRecoveryP50Ms,{pct(recovery, 0.50)}\n")
        handle.write(f"appRecoveryP95Ms,{pct(recovery, 0.95)}\n")
        handle.write("leaks,not-measured-by-host-run\n")

    with (out / "kernel-state.csv").open("w") as handle:
        handle.write("elapsedMs,event,value\n")
        for elapsed, event, value in state:
            handle.write(f"{elapsed},{event},{value}\n")

    print(f"rounds={len(rows)} p50={pct(values, 0.50)} p95={pct(values, 0.95)} overGate={len(over)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
