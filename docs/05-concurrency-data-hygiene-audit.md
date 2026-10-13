# M2 concurrency & data-hygiene audit (task 6)

Zero-tolerance grep audit over `**/*.kt`, run at the end of M2 (commit `87a49f0`).

## Forbidden patterns — must have zero *code* hits

| Pattern | Hits | Verdict |
|---|---|---|
| `GlobalScope` | 2 — both in comments/KDoc (`DashboardViewModel`, `KernelBridge`) | **0 code hits** ✅ |
| `allowMainThreadQueries` | 1 — KDoc in `DataModule` | **0 code hits** ✅ |
| `fallbackToDestructiveMigration` | 3 — KDoc in `StarveilMigrations`, `DataModule`, `StarveilDatabase` | **0 code hits** ✅ |
| `Thread.sleep` | 0 | ✅ |

`fallbackToDestructiveMigration` is not merely unused: `StarveilMigrations.ALL`
is an explicit list and `DatabaseRecoveryPolicy` has no "wipe" option in its
decision space, so a destructive fallback is structurally impossible.

## Blocking calls in main sources

| Pattern | Main-source hits |
|---|---|
| `runBlocking` | **0** — appears only in `src/test` and `src/androidTest` ✅ |
| `Thread.sleep` | 0 ✅ |

## Threading

- Room: no `allowMainThreadQueries`; DAOs are `suspend` / return `Flow`, so
  Room dispatches them itself.
- `RealKernelBridge.start/stop` wrap the kernel calls in
  `withContext(Dispatchers.IO)`.
- `RealKernelBridge.queryStats()` reads a cached `StateFlow` populated by the
  kernel's pushed `StatusMessage` — no IO on the caller's thread.
- `ConfigBuilder` is a pure function (no IO, no DB) — nothing to dispatch.
- `DashboardViewModel` owns a `SupervisorJob() + Dispatchers.Main.immediate`
  scope cancelled in `onCleared()`; its suspend calls do not block (kernel work
  hops to IO internally, and `delay` does not block the thread).

## Log storage

No `List<LogLine>` / `MutableList<...>` accumulation anywhere. Kernel logs use
`MutableSharedFlow(replay = 64, extraBufferCapacity = 64)` — a bounded buffer,
so a chatty kernel cannot grow memory without limit.

## Residual risk / not covered by this audit

- The audit is static (grep). It does not prove runtime behaviour; the
  end-to-end check is the task-7 smoke.
- `DashboardViewModel`'s Main-dispatcher scope is correct for its current
  suspend calls, but if IO is ever added there directly it would run on Main —
  worth re-checking in review.
