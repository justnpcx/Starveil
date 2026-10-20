# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
Milestone N ships as `0.N.0`; `versionCode` is derived from it.

## [0.7.4] — 2026-10-20

### Changed

- **Update channel**: releases are read from this repository
  (`justnpcx/Starveil`) instead of the artifact-only mirror, which no longer
  exists. The source is public now, so `releases/latest` answers without a
  token. Only 0.7.2 and 0.7.3 point at the deleted mirror and will report
  "no published release" until installed by hand; 0.7.0 and 0.7.1 named
  `justnpcx/Starveil`, which is this repository today, so they can see it.
- **Node page**: group chips are one fixed 16dp shape instead of a circle for
  a short label and a capsule for a long one, an idle node card no longer
  draws an outline, and the chip row sits against the list it filters. The
  card and the chip are now shared components (`StarveilCard`,
  `StarveilChip`) rather than one screen's private drawing.

### Fixed

- **Unreadable latency badge in dark mode**: "untested" and "timeout" were
  drawn in a fixed slate meant for light surfaces, which on a dark raised card
  measured 1.2:1 — invisible, and worse with a wallpaper-derived palette. The
  dimming now follows the scheme (5.5:1 dark, 7.2:1 light, measured on device).

## [Unreleased]

### Changed

- **Update failures are localised**: the update dialog used to append an
  internal English string ("no published release", "checksum mismatch") to a
  translated sentence. The data layer now returns a closed set of reasons and
  the screen owns the wording, so both languages get a sentence — and a new
  reason cannot ship without one.

## [0.7.3] — 2026-10-19

### Fixed

- **Hysteria2 throughput**: sing-box 1.14 turns ChromeParrot on by default, so
  the QUIC client imitates Chrome's transport behaviour. On the same node that
  cost roughly half the throughput — 12.9 MB/s single-stream and 26.5 MB/s
  across four streams, against 20–25 and ~36 with it off. NekoBox's 1.12 core
  has no such feature, which is why it was faster on the same node. Starveil
  now sets `disable_chrome_parrot`. The trade-off is camouflage: the parrot
  exists to look like Chrome on the wire.

## [0.7.2] — 2026-10-19

### Added

- **Subscription formats**: SIP008 and full sing-box configs join base64 and
  Clash YAML. A malformed document now names the line it could not read
  ("Malformed YAML at line 4"), and a partially usable one imports the good
  nodes and reports how many were skipped.
- **Rule market**: five curated packs — ads and telemetry, telemetry only,
  streaming, developer, AI — pinned to a commit and verified by SHA-256.
  See [docs/15-rule-market.md](docs/15-rule-market.md).
- **Local Clash-compatible API** (off by default): loopback only, Bearer token
  held in the Android Keystore, endpoints for traffic, proxies, connections
  and proxy selection. See [docs/14-local-api.md](docs/14-local-api.md).
- **First-run flow**: three steps — welcome, import, auto-select and connect —
  skippable at every step and shown once.
- **Scenes**: the network decides the policy. A known SSID or mobile data picks
  a profile, and the decision is written down; coarse location is requested
  because the platform requires it to read the SSID.
- **Background health**: every node is probed, not only the selected one, so
  auto-select chooses from measurements instead of a guess.
- **Keep-alive page**: the three switches a ROM hides.
- **Node hostnames over DNS-over-HTTPS**, resolved without the system resolver.
- **Rule sets pinned to a commit and verified by SHA-256**; the local network is
  always direct.
- **Time to first proxied byte** is measured and recorded.

### Changed

- **Update channel**: releases are read from the public artifact mirror
  `justnpcx/Starveil-releases`. The source repository is private, where
  `releases/latest` answers 404 without a token, and the app must not ship one.
- The drop-and-reconnect policy moved out of the dashboard view model into
  `DropRecovery`; the probe, sampler, leak-check and flow-log classes moved
  into `:core:probe`. No behaviour change, and both are testable on their own.
- Latency result copy moved into `:core:probe`, with the probe that produces
  it.
- **Dashboard, rebuilt**: one screen with fixed slots and an elastic chart; the
  connection state lives in the ring, the telemetry block is built around the
  number, auto-select is a chip, and the speed ribbons are gone.
- **APK size**: native libraries are stored compressed — 85.7 MB → 31.2 MB.

### Fixed

- **Mixed tun stack**: TCP flows again. The process is no longer excluded from
  the VPN by UID; outbound sockets are protected individually, so the kernel
  stack's listener SYN-ACKs re-enter the tunnel instead of leaking out of it.
- **Local DNS**: lookups are bounded and resolved on the underlying network,
  and no longer race the tunnel. First connection after a cold start drops
  from ~7 s to ~0.5 s.
- **IPv4-only tunnel**: AAAA destinations are no longer routed into a tunnel
  that cannot carry them, which Chrome surfaced as `ERR_CONNECTION_RESET` on
  domestic sites.
- **HY2 port hopping**: `mport` is parsed and emitted as `server_ports` /
  `hop_interval`; hopping nodes are reachable instead of refusing on the
  advertised port.
- **Dashboard**: `BLOCKED` is only claimed when a tunnel is actually dropping
  packets — on connect, at launch, and after an external stop.
- **Profiles**: deleting a group is one transaction, and the node row menu
  asks for confirmation before deleting a node.
- The mainland preset's name and description were Chinese in the default
  (English) resource file.
- **Snackbar**: the dark HUD colour in both schemes, unchanged by dynamic
  colour, and lifted a step above the cards it covers.
- **Auto-select chip**: the press is visible, and the chip no longer competes
  with the node name beside it.
- **Kill Switch**: blocks with a config instead of a dangling descriptor.
- **Tun descriptors**: every one handed to the kernel is closed.
- **Health curve**: the first point lands in half a minute, the skeleton follows
  the theme, the curve sits on the card with the same spline as the ribbons,
  and no chart draws a baseline under itself.
- **Rules**: the full flow list scrolls, the paused state is visible, and the
  screen shows one count against one baseline.
- **Profiles**: one scan entry, and the filter chips belong to the page.
- **Onboarding**: VPN consent is asked before the first connect, not after it
  fails.
- **Update**: the APK is chosen by the device's ABI rather than a hardcoded
  "arm64".

## [0.7.1] — 2026-10-18

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

- The node page has one add entry (a sheet with manual entry, clipboard, QR
  scan and a subscription URL) and its actions live in the app bar, so the
  filter row holds filters and nothing else.
- The kernel version reads `1.14.3`, without the fork's commit hash.
- **Kernel**: libbox rebuilt without the outbounds this app cannot use
  (tailscale, naive, openvpn, openconnect, usbip) — 19.5 MB of the binary,
  and the arm64 APK goes from 134.7 MB to 115.2 MB.

### Fixed

- The node label keeps its flag when any other setting changes. The label was
  rebuilt from the stored name on every preference write, while the connect
  path used the flagged name, so the two disagreed until the screen reloaded.
- Changing a routing preset, or the ads switch, while connected now rebuilds
  the tunnel. The rules page said the policy was in force while the kernel
  kept running the config it was started with.
- **Grouping by region** answers every time and says what it needs, instead of
  leaving a strip of text where a dialog belongs.
- **Update**: the dialog says whether the check or the download failed.

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
- **Node editor**: all seven protocols, with the fields each transport needs.
- **Sharing**: a node as a link, and as a QR code.
- **Subscriptions**: re-pull on demand, refresh on a schedule, and fall back to
  the last good copy instead of dropping the nodes when a refresh fails.
- **Groups**: create, rename and delete them, move nodes between them, and
  group the list by subscription as well as by region.
- **Routing**: ordered rules by domain, domain suffix, domain keyword or CIDR,
  with proxy / direct / block actions, plus a one-tap mainland-China preset
  that sends domestic sites direct and everything else through the tunnel. The
  rules screen also shows where traffic actually went, not only what the rules
  say.
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
- **Leak check**: says whether traffic is leaving outside the tunnel.
- **Dashboard**: live downlink and uplink rates and totals from the kernel's
  own counters, the selected node's 30-minute health curve, and a sparkline
  that pinch-zooms.
- **Diagnostics**: the kernel's log lines on the device, searchable and
  copyable, and a failed connect that names its reason instead of a generic
  error.
- **Theme**: light, dark, or follow the system.
- **Privacy**: the database is encrypted with SQLCipher, and the privacy policy
  is a screen rather than a link out.
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

### Changed

- Release builds are minified by R8 (arm64 APK 131 MB → 90 MB). The
  `benchmark` build type inherits it, so performance numbers now describe the
  shipping configuration; the recorded cold-start baseline predates it.

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
