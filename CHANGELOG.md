# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
Milestone N ships as `0.N.0`; `versionCode` is derived from it.

## [Unreleased]

### Added

- **Routing presets**: the single "bypass mainland" switch becomes three
  policies — bypass mainland, global proxy (default), global direct — and
  advertising domains become a separate switch that stacks on any of them.
  The rules page lists the preset's own rules in evaluation order instead of
  only counting them, and its empty state names the policy in force.
- **Node flags**: a node is placed by the address it connects to, not by what
  its name happens to say. An offline GeoIP database (`ip2region`, bundled)
  supplies the country, so imported nodes get a flag and region grouping
  without being renamed by hand. Hong Kong, Macao and Taiwan are split out of
  the database's `CN` by province.

### Changed

- Release builds are minified by R8 (arm64 APK 131 MB → 90 MB). The
  `benchmark` build type inherits it, so performance numbers now describe the
  shipping configuration; the recorded cold-start baseline predates it.
- The node page has one add entry (a sheet with import, subscription,
  clipboard and QR) and its actions live in the app bar, so the filter row
  holds filters and nothing else.
- The kernel version reads `1.14.3`, without the fork's commit hash.

## [0.7.0] — 2026-10-17

First beta. There was no earlier public release, so this entry describes what
the app does rather than what changed.

### Added

- **Connection**: sing-box 1.14.3 core (self-maintained fork) behind a Compose
  UI, with VLESS, VMess, Trojan, Shadowsocks, Hysteria2, TUIC and WireGuard.
  REALITY, uTLS fingerprints, WebSocket, gRPC, HTTP and XHTTP transports.
- **Nodes**: import from share links, subscription URLs, clipboard or QR;
  latency probing with a health curve over the last 30 minutes; groups with
  folder, selector and fastest-node modes.
- **Routing**: ordered rules by domain, domain suffix, domain keyword or CIDR,
  with proxy / direct / block actions, plus a one-tap mainland-China preset
  that sends domestic sites direct and everything else through the tunnel.
- **Per-app routing**: send an individual app direct, or through a specific
  node, independently of the tunnel's default.
- **Auto select**: picks the healthiest node, breaking ties by preferred
  region, and parks a node that failed three times in a row for 30 minutes.
  With the tunnel up, an unexpected drop can switch to the next-best node
  instead of retrying the one that just failed.
- **Grouping by region**: derives groups from node names (Chinese names, city
  names, flags, and ISO codes as whole tokens) and keeps membership derived, so
  a subscription refresh cannot undo it.
- **Kill Switch**: off, block-on-disconnect, or always-block, enforced in the
  kernel rather than in the UI.
- **Backup**: export and restore nodes, groups and rules as one file encrypted
  with scrypt and AES-256-GCM.
- **Surfaces**: home-screen widget, quick-settings tile, and a notification
  with a Disconnect action — all three go through the same connection path, so
  the Kill Switch and per-app rules behave identically from any of them.
- **Crash reporting**: opt-out Sentry reports. Node addresses, UUIDs, share
  links, subscription URLs and keys are replaced before the report leaves the
  device; Settings → Diagnostics turns it off.
- **Node country**: every node is placed by its address — resolved once,
  looked up in an offline GeoIP database that ships with the app, and shown
  as a flag before its name. Grouping by region uses the same answer, so a
  subscription with names like `HY2+Reality` still gets a 美国 group.
- **Update check**: Settings → About fetches the latest GitHub release,
  downloads the APK, and verifies it two ways before offering it to the
  installer — SHA-256 against the digest GitHub publishes, and the signing
  certificate against the installed app. Nothing is installed silently.
- **Languages**: English and Simplified Chinese.
- **Accessibility**: content descriptions on every chart and icon, 48dp touch
  targets, and a contrast test in CI.

### Known issues

These are unverified on a device, not known-broken. They are listed because a
beta should say what it has not proven.

- Frame rate at 1000 nodes, resident memory, reconnect timing, interaction
  latency and DNS cache p50 have not been measured on hardware.
- The mainland preset and per-app routing have been verified against the real
  kernel off-device (routing decisions, config acceptance) but not end-to-end
  on a phone.
- REALITY over the XHTTP transport was fixed in the kernel and starts, but the
  full data path needs a real xhttp server to confirm.
- The database migration from schema 2 to 3 is covered by an instrumented test
  that CI does not run.
- Traditional Chinese is not translated; `values-zh` is Simplified.
