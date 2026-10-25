# The rule market

The rules page has a **Rule market** entry beside the ad switch. It offers five
curated rule packs; subscribing to one adds its domains to the routing table,
unsubscribing removes them. There is no account, no server of ours, and no
telemetry about what anyone subscribes to.

## What is in phase one

| Pack | Source (`SagerNet/sing-geosite`) | Action | Why |
|---|---|---|---|
| Ads and telemetry | `geosite-category-ads-and-telemetry.srs` | reject | The built-in ad switch covers `category-ads-all`; this adds software telemetry. |
| Telemetry only | `geosite-category-telemetry.srs` | reject | Analytics and crash reporting blocked without blocking ads. |
| Streaming | `geosite-category-entertainment.srs` | proxy | The list is explicitly "outside China mainland", so domestic video is not dragged through the tunnel with it. |
| Developer | `geosite-category-dev.srs` | proxy | GitHub, npm, PyPI, Docker and distro mirrors. |
| AI services | `geosite-category-ai-!cn.srs` | proxy | AI services outside the mainland. |

All five are pinned to the same commit as the mainland preset:
`4f2dea911f1dbc3fd7516dbc2c82e03a0add71e6`. Each file's SHA-256 is in
`core/data/.../ruleset/RulePacks.kt`; a file that does not match is never
written, and the pack is left out of the config.

The list is closed on purpose. "Market" here means there is something to
choose from, not that anything can be submitted: user-supplied sources are
phase two, and the extension point is the same object — a pack is data, so
adding one is an entry and a hash, not a new code path.

## How a subscription takes effect

Subscribing writes a preference. The dashboard already watches routing policy
preferences and rebuilds the running tunnel when they change, so a pack takes
effect the same way a preset change does — no separate "apply" step, and no
state where the page says a pack is on while the kernel was never told.

The config builder emits, in this order:

1. per-app exceptions,
2. the built-in ad rule,
3. subscribed packs,
4. the LAN direct rule,
5. the user's own rules,
6. the mainland preset (if on).

So a pack cannot override an app exception, and a hand-written rule can
override a pack. A proxy pack names the proxy outbound rather than the route's
`final`; under "global direct" the latter would send a streaming pack direct,
which is the opposite of what subscribing means.

## Adding a pack

1. Pick a `.srs` file and pin the commit it comes from.
2. Download it at that commit, record its SHA-256, and add an entry to
   `RulePacks.ALL` in `core/data/.../ruleset/RulePacks.kt`.
3. Add the name and one-line description to `values/` and `values-zh/` in
   `feature:rules` (the screen maps ids to resources; the model carries only
   the id and the log name).
4. Add it to the store test's fixture list if the file is unusual.

Updating a pack means updating the commit and the hash in the same change —
that pairing is the whole point of the pin.

## Verification

Covered by unit tests: fetch-once and reuse, only subscribed packs fetched,
tamper rejection, one bad pack not taking the others down, an unknown id from
a newer build ignored, offline, a stale file on disk replaced, the rule's
position and action in the config, and the subscribe/unsubscribe round trip
through the view model.

Not yet verified on a device: subscribing to *Ads and telemetry* and seeing a
`reject` decision in the logs for a known ad domain, then unsubscribing and
seeing it resolve again. That is the acceptance test for the next device
session.
