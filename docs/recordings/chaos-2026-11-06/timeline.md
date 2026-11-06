# Timeline of the one host-driven round — 2026-11-06

Device: Fire TV Stick 4K Max (AFTKRT), Android 11 / SDK 30, armeabi-v7a,
1920×1080. App: benchmark variant of 0.9.26 (versionCode 926). Node: the lab
Shadowsocks server; its address and password are deliberately not recorded
here. Kill Switch: `BLOCK_ON_DISCONNECT`. The app was CONNECTED before the
drop (see `connected-before-drop.png`).

Lines are copied from `adb logcat -s StarveilChaos`, with the wall clock and the
app's `elapsedRealtime` exactly as emitted:

```
11-06 10:02:37.749 StarveilChaos: state log started: .../files/chaos/kernel-state.csv
11-06 10:02:37.753 StarveilChaos: blocking=false elapsed=212357594
11-06 10:02:37.754 StarveilChaos: kernel=p elapsed=212357596
11-06 10:02:37.827 StarveilChaos: mark=r1:start elapsed=212357668
11-06 10:02:39.932 StarveilChaos: mark=r1:wifi_enable elapsed=212359773
```

`kernel=p` is the pre-fix line: the benchmark variant is R8-minified and
`KernelState::class.simpleName` had been renamed to `p`. The receiver now maps
the state through a `when` and logs `Running`/`Starting`/… instead. No kernel
transition follows `wifi_enable` in this run, which means the kernel never left
`Running`; the state the harness was waiting for is the Dashboard's
`ConnectionState`, not a kernel transition.

The host script gave up at `r1:start + 10 s` with `round 1: not recovered
within 10000ms`. The screenshot `recovered-after-abort.png` was taken after
that abort and shows `CONNECTED`, `LabSS`, total 146 KB down / 165 KB up. So the
app did come back; the harness did not observe it inside the task book's 10 s
abort window. That is a failed observation, not a pass, and it is not recorded
as a round.

`host-rounds.csv` has no rows: the consistency columns are written only after
the UI check succeeds, and it never did inside the window.
