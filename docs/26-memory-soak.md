# Memory soak — harness, and why there is no curve

**Status: harness delivered; neither stage was executed on the lab device.**
No numbers are invented below. The M4 baseline in
`docs/11-device-verification.md` is 82–84 MB PSS with a 1.2–1.3 MB drift over
ten minutes, so the task book's 8 MB window is generous — but a generous window
is not data.

## What was built

| file | what it is |
|---|---|
| `scripts/soak.sh` | Stage A (100 connect/disconnect cycles) and stage B (24 h resident, one sample every 10 min, one ping every 10 min). Writes `stage-a.csv`, `stage-a-residual.txt`, `stage-b.csv` under `build/soak/<serial>/`. |
| `scripts/soak-analyze.py` | Reads those CSVs and applies the two assertions: first/last 20 mean ≤ 8 MB, first/last 4 h drift ≤ ±8 MB. Prints the slope and correlation as well, so a slow monotonic rise is visible even when the endpoint delta is inside the window. |

## Method

- **Stage A:** 100 cycles of `AutomationActivity.START` → wait for
  `StarveilVpnService` → settle 3 s → `dumpsys meminfo` (TOTAL PSS/RSS) →
  `STOP` → wait for the service to go away → settle 2 s → sample again. After
  the last disconnect: `ip link show tun0`, `dumpsys activity services`, and a
  notification count, written to `stage-a-residual.txt`.
- **Stage B:** connect once, then sample every 10 minutes for 24 h, with one
  `ping -c 1 -W 3 1.1.1.1` per sample as the small traffic probe.
- **Assertions:** stage A last-20 mean minus first-20 mean ≤ **8 MB**, and no
  monotonic increase; stage B last-4 h mean minus first-4 h mean within
  **±8 MB**. The analyzer prints the linear slope and Pearson r beside the
  means because a 7 MB rise spread evenly across 100 cycles is exactly the
  shape the "no monotonic increase" clause is for.
- **Preconditions:** benchmark variant with `LabNodeReceiver`, a selected node,
  Kill Switch **OFF** (stage A asserts no tun and no VpnService remain after
  the last disconnect; `BLOCK_ON_DISCONNECT` deliberately keeps a tun, so the
  two cannot be asserted in the same configuration), device plugged in,
  battery optimisation off, screen kept on.

## Why it did not run

The same device problem as T1, and one of scope:

1. **The adb transport is coupled to the tunnel.** Every connect/disconnect
   cycle rebuilds the VpnService and the tun; on this TV the adb TCP session
   dropped during several of those rebuilds. A 100-cycle run needs the host to
   issue two automation intents per cycle and read `dumpsys` between them; with
   the transport dropping, the run cannot be scripted reliably.
2. **The app's consent/focus path was unstable after the reinstall.** The VPN
   consent was rejected once, then granted through `cmd appops`; after the
   second radio loss the launcher kept window focus even when `am start`
   reported the activity resumed. A soak that samples the wrong process is
   worse than no soak.
3. **Stage B is 24 hours.** That is longer than this session by design; the
   task book calls it a lab long run, not CI. Even with a stable device it
   would have to be started and read in a later session.

The harness is written so that a stable device needs no code change: run
`./scripts/soak.sh a`, then `./scripts/soak.sh b` (or `both`), then
`./scripts/soak-analyze.py build/soak/<serial>`.

## What the report would contain

- The two CSVs, untouched.
- First/last window means, delta, slope, r, and the residual check.
- A pass/fail per assertion. A failed curve is a result; the analyzer does not
  hide it.

## Acceptance

| item | result |
|---|---|
| stage A 100 cycles | not executed |
| stage A ≤ 8 MB | not measured |
| stage A no residual tun/service | not measured |
| stage B 24 h | not executed |
| stage B ±8 MB | not measured |
| acceptance | **not met** |

## What was not touched

No production file. The scripts are host-side; `build/soak/` is gitignored raw
data. `DropRecovery`, `ConnectionReconciler`, `KernelState` / `ConnectionState`,
`closeKernelFds`, `StarveilVpnService`, `docs/14`, GOGC and the design tokens
are untouched.
