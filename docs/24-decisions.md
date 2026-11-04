# Decisions

Short records of things deliberately **not** built, and of gaps that were
checked and turned out to be closed. Each entry is one conclusion and one
reason, so a future audit does not reopen a question that already has an
answer.

## 2026-11-04 — Competitive gap review

### No fallback or load-balance outbound groups

**Conclusion:** not built, and not planned.

**Why:** sing-box's outbound groups are selector and urltest only; there is no
fallback or load-balance type in the kernel, and the client does not invent
protocol the core cannot execute. Availability is already handled above the
core: urltest picks a healthy node inside a group, and `DropRecovery` switches
after a drop.

### Rule dry run — already present

**Conclusion:** no work needed; the diagnostics page has it.

**Why:** `RouteEvaluator` walks the exact rule list `ConfigBuilder` emits (via
`RuleDryRun`) and reports the first match with its rule text and outbound.

### Subscription override — already present

**Conclusion:** no work needed.

**Why:** per-group subscription fetch options (proxy vs direct, user agent) are
in the group editor, and the config builder reads them at connect time.

### Quick-settings tile and home-screen widget — already present

**Conclusion:** no work needed.

**Why:** `StarveilTileService` and `StarveilWidgetReceiver` are shipped, both
driving `ConnectionController` rather than a second connect path.

### Wi-Fi SSID scenes — already present

**Conclusion:** no work needed.

**Why:** `SceneController` evaluates SSID and mobile-data conditions and applies
a scene's route preset and target; the location permission it needs is declared
and documented.
