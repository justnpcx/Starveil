# M2 closure

Status: **closed** (code complete, non-device verification green, CI green).

- main HEAD at closure: `c4a5d27` (merge of `feature/kernel-real`).
- CI: green. Unit tests: 66, 0 failures. Instrumented (real device, vivo
  V2238A / API 36): 4, 0 failures.
- Clean build + **all-module** `lintDebug`: 0 errors.

## Scope ruling

**V4 ("Dashboard 内 2 步切节点；切换后 1s 内流量通") is deferred to M3.**

Reason: V4 needs a real outbound in the kernel config, which needs a selected
node — and node selection is M3 scope (PRD D5 "节点选择器内置" / P1 "节点列表").
The task book also says explicitly not to do editor UI in M2. Carrying a
temporary "use the first node" path would only add code M3 has to rewrite.

Consequence, stated plainly: **M2 has no end-to-end "real traffic" proof.** What
M2 does prove is that every layer compiles, is unit-tested, and that the
encrypted storage path works on a real device.

## What M2 delivered

| Area | Evidence |
|---|---|
| Config builder + VLESS/Trojan/Shadowsocks | `7083f14`; 26 tests incl. 500-node <100ms |
| Subscription: base64 + Clash YAML, WorkManager refresh | `59eb96b`, `d129364` |
| SQLCipher storage + encrypted backup | `77e9cb2`; **4 real-device DB tests** `1019ec7` |
| Kill Switch three modes + leak readout | `b9f4395`, `2bcaf55` |
| Concurrency / data-hygiene audit | `1961dbf`, docs/05 |
| libbox + RealKernelBridge | `aa360fd`, `694a23c`, `87a49f0`, `3b985ad`, `bea6dd7` |

## Open items carried into M3

1. **Node selection** — required to unblock V4/V5.
2. **`urlTest` has no `linkToTag` supplier** — returns `Unreachable` until node
   selection exists.
3. **Connect uses the skeleton config** `{"log":{"level":"info"}}` — no outbound.
4. **VpnService consent path untested on device** (UI written in `3b985ad`).
5. **`libbox.aar` is arm64-v8a only** — re-run `tools/build-libbox.sh` with no
   PLATFORM and replace it before any release (docs/04).
6. **`gh` auth / PAT rotation** — `gh` installed, awaiting `gh auth login`.

## Review rulings to carry forward

- The kernel-dependency rule now reads: **no kernel dependency outside
  `:core:kernel` except the `VpnConsent` port** (docs/06).
- libbox is consumed as a **checked-in binary**, not built in CI (docs/04).
