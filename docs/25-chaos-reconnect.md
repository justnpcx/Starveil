# Chaos reconnect — harness, and the run that did not finish

**Status: harness delivered; the 50-round acceptance run did not finish.**
No threshold was changed, no round is reported as passed, and the numbers below
are the ones the device produced.

## What was built

| file | what it is |
|---|---|
| `benchmark/.../ChaosReconnectTest.kt` | The regression test: 50 rounds, one CSV row per round, the 1.2 s gate, the consistency check, the leak probe, and the 10 s abort rule. |
| `app/src/benchmark/.../LabNodeReceiver.kt` | Benchmark-variant only. Writes one **real** test node from broadcast extras (address, port, method, password) and sets the selection, `GLOBAL_PROXY`, auto-reconnect and the Kill Switch mode. Nothing real is in the source. |
| `app/src/benchmark/.../ChaosStateReceiver.kt` | Subscribes to `KernelBridge.state` / `blocking` and writes every transition with `elapsedRealtime`; also records `MARK` broadcasts so the drop and the radio's return land in the same clock as the kernel. Logcat and the app's external files directory. |
| `scripts/chaos-reconnect.sh` | Installs the benchmark variant + test APK, seeds the lab node, runs the instrumentation test, pulls the data. |
| `scripts/chaos-run.sh` | Host-driven fallback for a device whose adb link rides the same radio the test switches off: one detached one-shot per round brings the radio back, MARKs bracket the round, the host only reconnects and reads. |
| `scripts/chaos-parse.py` | Turns the logcat into `chaos-rounds.csv`, `chaos-summary.csv` and `kernel-state.csv`. |

The test is the regression. The host script exists because the lab TV cannot
keep an `am instrument -w` transport alive across a radio drop.

## Method

- **Stimulus:** Wi-Fi radio off, hold 2.5 s, radio on. The TV has no cellular
  radio, so the other two stimuli in the task book are not available on it. A
  stopped node was rejected: the kernel stays `Running`, and a kernel that
  never noticed a drop is not a reconnect test.
- **Round:** steady connected → drop → wait for the Dashboard to say
  `CONNECTED`, the `StarveilVpnService` to exist and its notification to exist
  → record → settle 1.5 s.
- **Self-heal:** drop start → `KernelState.Running` **and** Dashboard
  `CONNECTED`. Gate **1.2 s**; a round that has not recovered in **10 s** is a
  failure and stops the run, with logcat kept.
- **Consistency:** the three surfaces above are read again after the poll loop;
  a round is inconsistent when they disagree.
- **Leak:** the instrumentation test opens a fresh TCP connection to
  `1.1.1.1:443` every 150 ms for the whole run. A successful connection whose
  route for **the test process's own uid** is not `tun0` counts as one plaintext
  escape. This is a flow-level count, not a packet capture; the report says why
  the endpoint method is unavailable below.

## Device and setup

- Fire TV Stick 4K Max (AFTKRT), Android 11 / SDK 30, `armeabi-v7a`, 1920×1080.
- App: benchmark variant of **0.9.26** (versionCode 926), debug-signed. It was
  installed after uninstalling the release build; the release build had no
  nodes (`No node selected`), so no user data was lost.
- Node: a Shadowsocks `aes-256-gcm` server on the lab host, port 18388. The
  address and password were passed as broadcast extras and are not recorded
  here. `192.0.2.10` is used as the placeholder in comments.
- VPN consent: the system dialog was rejected once; the test device then used
  `cmd appops set com.justnpcx.starveil ACTIVATE_VPN allow`.
- Kill Switch: `BLOCK_ON_DISCONNECT`, so a plaintext escape during the window
  is a real escape and not the product's expected "direct while disconnected".

## What actually happened

| attempt | result |
|---|---|
| Instrumentation smoke, 1 round, before consent | `setUp` failed: the tunnel was not connected. No data. |
| Instrumentation smoke after consent | The app was connected; the test started. When the radio went down, the `am instrument -w` transport died and the command exited 255; no `StarveilChaos` line was produced. The test process did not survive the transport loss. |
| `am instrument` without `-w` | Returned without starting a test process: no `com.justnpcx.starveil.benchmark` process and no log. |
| Host-driven round (`chaos-run.sh`, 1 round) | The MARKs landed (`r1:start`, `r1:wifi_enable`), but no kernel transition followed and the UI check did not succeed inside the 10 s abort window. A screenshot taken after the abort shows the app **CONNECTED** (146 KB down / 165 KB up). The round is not counted. |
| Device stability | The TV lost Wi-Fi twice during the session; after the second restore `am start` reported the activity resumed but `mCurrentFocus` stayed on the Fire TV launcher, so the app could no longer be observed. The experiments stopped there. |

The raw timeline is in `docs/recordings/chaos-2026-11-06/timeline.md`; the two
screenshots are `connected-before-drop.png` and `recovered-after-abort.png`.

## Results

| item | result |
|---|---|
| rounds completed | **0 / 50** |
| self-heal p50 / p95 / max | not measured |
| rounds over 1.2 s | not measured |
| state inconsistencies | not evaluated (no completed round) |
| plaintext escapes | not measured (the probe never ran to completion) |
| acceptance | **not met** |

The one attempt that reached the drop did **not** recover inside the task
book's 10 s window as observed by the harness, and the app was connected
afterwards. That is an observation failure, not a pass; it is recorded as such.

## Why the leak endpoint method is unavailable on this LAN

- `ConfigBuilder` routes the local subnets direct (the `lan` rule built from the
  interface's own addresses), so a probe to the lab host never enters the
  tunnel.
- The lab host and the TV leave through the same home IP, so an exit-IP echo
  cannot tell the tunnel from a direct connection. This is the same limitation
  recorded in `docs/11-device-verification.md` for the exit footer.
- There is no external test endpoint: §0.4 rules out the production VPS links,
  and this device is not rooted, so device-side packet capture is not
  available either.

The implemented probe is the fallback the task book allows: device-side,
flow-level, route-for-uid. It needs a completed run to produce a count.

## How to run it on a suitable device

A device whose adb link is not the radio under test (USB or Ethernet), or a
phone with a cellular radio to switch to, removes both failures above.

```bash
./gradlew :app:assembleBenchmark :benchmark:assembleBenchmark

# instrumentation regression (the real one)
CHAOS_HOST=… CHAOS_PORT=… CHAOS_METHOD=… CHAOS_PASSWORD=… \
  ./scripts/chaos-reconnect.sh <serial>

# host-driven fallback for a radio-coupled adb
CHAOS_ROUNDS=50 ./scripts/chaos-run.sh <serial>
```

Preconditions: the benchmark variant installed, the lab node selected and
connected, VPN consent granted, Kill Switch `BLOCK_ON_DISCONNECT`.

## What was not touched

No production file changed. The only app-side files are in
`app/src/benchmark` (benchmark variant only). `closeKernelFds`,
`StarveilVpnService`'s fd ownership, `DropRecovery`, `ConnectionReconciler`,
`KernelState` / `ConnectionState`, `docs/14`, GOGC, `ConnectionCore` and the
design tokens are untouched. The diff is the benchmark module, the benchmark
source set, `scripts/` and this document.
