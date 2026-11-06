# Rerun checklist — T1 / T2 / T3

**Status: waiting for a device whose adb link is not the radio under test.**
This is the run order and the acceptance gates for PRs
[#50](https://github.com/justnpcx/Starveil/pull/50),
[#51](https://github.com/justnpcx/Starveil/pull/51) and
[#52](https://github.com/justnpcx/Starveil/pull/52). The harnesses are in
`benchmark/`, `app/src/benchmark/` and `scripts/`; the reports are
`docs/25`–`docs/27`.

The previous attempt stopped at **T1 0/50, T2 not run, T3 not run** because
the lab Fire TV's adb rides the Wi-Fi radio the chaos test switches off, and
after the second radio loss the launcher kept window focus. None of that is a
property of the app; it is a property of that device.

## 0. Device and compliance — do not start without these

- [ ] Dedicated test device, **not** the daily phone/TV. §0.4 of the task book
      outranks "a device is available".
- [ ] adb is **not** on the radio under test: USB / Ethernet adb, or a phone
      with cellular for a Wi-Fi↔cellular switch. A Wi-Fi-only TV is no longer
      used for T1.
- [ ] The app can stay in the foreground: `am start -W` then
      `dumpsys window | grep mCurrentFocus` must name
      `com.justnpcx.starveil`, not the launcher.
- [ ] Test subscription or lab node only; no production VPS and no production
      node. Address and password travel as environment variables, never in the
      repository.
- [ ] Record model, SDK, ABI, resolution and build fingerprint for the report.
- [ ] Screen stay-on, battery optimisation off, device plugged in.

## 1. Build and install

- [ ] `./gradlew :app:assembleBenchmark :benchmark:assembleBenchmark`
- [ ] Install the app benchmark APK for the device's ABI:
  - `arm64-v8a`: `app/build/outputs/apk/benchmark/app-arm64-v8a-benchmark.apk`
  - `armeabi-v7a`: `app/build/outputs/apk/benchmark/app-armeabi-v7a-benchmark.apk`
  - `x86_64`: `app/build/outputs/apk/benchmark/app-x86_64-benchmark.apk`
- [ ] Install the test APK:
      `benchmark/build/outputs/apk/benchmark/benchmark-benchmark.apk`
- [ ] **ABI caveat:** `scripts/chaos-reconnect.sh` currently hard-codes the
      `armeabi-v7a` app path. On an arm64 device either edit `APP_APK` or
      install the correct APK by hand and run the instrumentation command
      directly. `scripts/chaos-run.sh` does not install anything and is the
      safer runner once the app is in place.
- [ ] Grant VPN consent: the system dialog, or on the test device
      `cmd appops set com.justnpcx.starveil ACTIVATE_VPN allow`.
- [ ] Benchmark variant only; a release build has no seeder and no lab-node
      receiver.

## 2. Smoke gate — a long run does not start until these pass

- [ ] T3: one 35 s trace with `samples=6` and one with `samples=0`; `gfxinfo`
      must contain frames and `chart.txt` must contain
      `Recomposer:recompose` lines.
- [ ] T2: `SOAK_CYCLES=3 ./scripts/soak.sh a <serial>`; six rows in
      `stage-a.csv`, and no residual tun/VpnService afterwards.
- [ ] T1: `-e rounds 1` for the instrumentation test, or
      `CHAOS_ROUNDS=1 ./scripts/chaos-run.sh <serial>`; the round must produce
      a `selfHealMs` row and a `kernel-state.csv` with the MARKs. Only then run
      50.

## 3. Order when one device must carry all three

T3 wipes app data with `pm clear`; T1's radio drops can take the device off the
network. Run in this order:

1. **T3** (no VPN, data wipe first)
2. **T2 stage A** (100 cycles, Kill Switch OFF)
3. **T1** (radio drops, Kill Switch ON) — last, because it is the one that can
   lose the device
4. **T2 stage B** (24 h, unattended) — after T1, so a radio experiment does not
   interrupt the long baseline

If two devices are available, T1 and T2-B can run in parallel on separate
devices.

## 4. T3 — health chart

- [ ] The app benchmark variant is enough; the test APK is not needed.
- [ ] Two runs, same device, same window, same drag script:
  ```bash
  ./scripts/chart-measure.sh <serial> 6 with-curve
  ./scripts/chart-measure.sh <serial> 0 no-curve
  ```
- [ ] Repeat each side three times and take the median; one 35 s trace per run.
- [ ] `chart-analyze.py` over `gfxinfo.txt` + `chart.txt`:
  - frames, fps, jank over 20 ms and over 16.7 ms, p50/p90/p95/p99/max;
  - `Recomposer:recompose` and `Compose:recompose` per second;
  - the difference between the two seeds.
- [ ] State the 0.9.26 fact in the report: `NodeHealthChart` is in the dashboard
      details dialog; the node cards use `LatencySparkline`. The measurement is
      of the per-row curve the list actually crosses.
- [ ] Decision: a difference inside run-to-run noise is **no-go**; a material
      difference is go, but this book only delivers the conclusion and changes
      no UI.
- [ ] Update `docs/27-health-chart-measurement.md`: status, table, go/no-go.

## 5. T2 — memory soak

- [ ] Kill Switch must be **OFF** for both stages: stage A asserts no tun and
      no VpnService remain after the last disconnect, and
      `BLOCK_ON_DISCONNECT` deliberately keeps a tun.
- [ ] Re-seed the lab node with `killSwitch=off`:
  ```bash
  adb -s <serial> shell am broadcast \
    -a com.justnpcx.starveil.benchmark.LAB_NODE \
    -n com.justnpcx.starveil/.benchmark.LabNodeReceiver \
    --es host <lab-host> --ei port <port> \
    --es method aes-256-gcm --es password <password> \
    --es name LabSS --es killSwitch off
  ```
- [ ] Stage A: `./scripts/soak.sh a <serial>` (100 cycles, about 15 minutes).
- [ ] Check `build/soak/<serial>/stage-a-residual.txt`: no `tun0`, VpnService
      absent, notification count 0.
- [ ] Stage B: `./scripts/soak.sh b <serial>` (24 h; one sample every 10 min,
      one ping per sample). A kill/reboot invalidates the round: rerun it and
      say so in the report.
- [ ] Analyze: `./scripts/soak-analyze.py build/soak/<serial>`.
- [ ] Assertions: stage A first/last 20 mean difference ≤ 8 MB and no monotonic
      increase; stage B first/last 4 h drift within ±8 MB.
- [ ] Update `docs/26-memory-soak.md` with the CSV summary; if a curve fails,
      attach the curve.

## 6. T1 — chaos reconnect

- [ ] Re-seed the lab node with `killSwitch=block_on_disconnect`: the leak
      gate needs the block, otherwise direct traffic while disconnected is the
      product's expected behaviour, not a leak.
- [ ] Decide the leak measurement before the run and write it down:
  - an external controlled endpoint that records the source IP → the task
    book's original definition;
  - no endpoint → the instrumentation probe's flow-level count (a successful
    connection whose route for this process's uid is not `tun0`), explicitly
    labelled as not a packet capture.
- [ ] Run 50 rounds:
  ```bash
  # host-driven runner (recommended; does not depend on am instrument)
  CHAOS_ROUNDS=50 ./scripts/chaos-run.sh <serial>

  # instrumentation regression (install the correct ABI first)
  CHAOS_HOST=... CHAOS_PORT=... CHAOS_METHOD=... CHAOS_PASSWORD=... \
    ./scripts/chaos-reconnect.sh <serial>
  ```
- [ ] Pull and parse: `logcat-chaos.txt` → `chaos-parse.py` →
      `chaos-rounds.csv`, `chaos-summary.csv`, `kernel-state.csv`.
- [ ] Assertions: 50 rounds; every `selfHealMs ≤ 1200`; inconsistent count 0;
      leaks 0; any round over 10 s aborts the run with logcat kept.
- [ ] Record `appRecoveryMs` (network back → CONNECTED) beside `selfHealMs`
      (drop command → CONNECTED). The gate stays on `selfHealMs`; the second
      number tells a slow radio from a slow app.
- [ ] Update `docs/25-chaos-reconnect.md`: status, result table, failed-round
      notes.

## 7. Data and report discipline

- [ ] Raw CSV/trace stays under `build/...` (gitignored); the repository gets
      sanitized `docs/recordings/...` and the report tables.
- [ ] `./scripts/leak-scan.sh` must be clean: no node address, password,
      subscription link or node name in the commit.
- [ ] The report states device, version, protocol, measurement definition,
      failed rounds and any unmet gate. **A failed number is a result; do not
      change the threshold, the sampling window or the round selection.**
- [ ] Update the PR body from "not executed" to the measured summary; push the
      same branch; wait for `build` to be green.
- [ ] Keep the PRs open. Merge is a separate decision after the data is in.

## 8. Do not

- [ ] Run on the daily phone, a production node or a production VPS.
- [ ] Relax the 1.2 s / 8 MB / 10 s gates or the sampling windows to make a run
      green.
- [ ] Touch `DropRecovery`, `ConnectionReconciler`, `KernelState` /
      `ConnectionState`, `StarveilVpnService`, `closeKernelFds`, `docs/14`,
      GOGC or the design tokens while measuring T1/T2.
- [ ] Add `graphicsLayer` in T3; this book only delivers the conclusion.
