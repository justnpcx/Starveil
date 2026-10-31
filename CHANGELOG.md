# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
Milestone N ships as `0.N.0`; `versionCode` is derived from it.

## [0.9.9] — 2026-10-31

The navigation bar stops reacting to touch, the app learns a second language, and
the palette opens up — a little.

### Added

- **Language, in the app.** Settings → General → Language offers System default,
  简体中文 and English, through the platform's per-app locale, so Android 13's own
  "App language" page shows the same choice and the two cannot disagree.
- **Five accent presets and three text sizes.** Star blue, violet, teal, pine and
  amber, each remapping only the accent family — the brand gradient, the icon and
  the semantic colours stay put. Text can be standard, large or largest; the home
  page's one-screen promise holds at standard and is allowed to scroll above it.

### Fixed

- **The navigation bar no longer reacts to a press at all.** The slot-sized ripple
  from 0.9.8 was still a shape appearing under the finger; a tab now changes
  colour and nothing else, the way the phone's own tab bar behaves.
- **A new text size applies while you watch.** Density is read when text is
  measured, so changing it left the screen that had already laid its text out
  untouched — the setting looked broken until the app restarted. The tree is now
  rebuilt on the change, which is what the system does when its own font size
  moves; the navigation stack survives it.
- **Segment labels are no longer cut off.** An equal-third segmented control
  painted the next pill over the tail of a long label — "Follow system" showed as
  "Follow", without even an ellipsis. Segments now take width in proportion to
  their text where the caller asks for it.

## [0.9.8] — 2026-10-31

Two things the phone found in 0.9.7.

### Fixed

- **One filter, one lit chip.** Favorites and the group were two separate flags,
  and choosing a group never cleared favorites — so two chips could be on at
  once and the list showed the intersection of two filters, which was usually
  nothing. The filter is one value now (all / favorites / one group), the chips
  and the list both read it, and six filter moves are covered by a unit test.
- **The navigation bar's press is the slot, not a pill.** Material clips its
  ripple to the 64x32 indicator, so every tab tap flashed a pill-shaped block
  even though the indicator was transparent. The item is drawn here now: equal
  slot, icon and label, ripple bounded to the whole cell, selection still the
  accent colour and a heavier label.

## [0.9.7] — 2026-10-31

Polish pass: the button that was not centred, the page that was flat in daylight,
the home screen that still needed a scroll on a 714dp phone, and the message bar
that never appeared.

### Fixed

- **Button labels are centred.** Surface lays its content out from the top-left,
  so a button stretched to half a card had its text pinned to the corner. Both
  button shapes now centre their content in both directions, and a form's save
  uses the selected fill (selBg + selInk) while cancel stays a ghost outline.
- **The home page fits a 714dp phone without scrolling.** The bottom air, the
  status slot, the gap before the node card and the top gap are all sized by the
  tier now; an empty status slot costs nothing below the standard tier, and the
  first-connect hint stops repeating what the ring already says.
- **Daylight has depth.** The light page moves from #F6F7F9 to #EDF0F4, rules
  and segment troughs darken a step, and cards carry the two-shadow pair a light
  interface usually uses. Dark mode is untouched.
- **The message bar appears.** Material's SnackbarHost drew nothing on the nodes
  page — the slot was alive and the message was there for its full four seconds,
  but not one pixel was painted. The app now draws the bar from the host state
  itself; the same state class, the same timeout, the same action.

## [0.9.6] — 2026-10-31

Four things the phone found in 0.9.5.

### Fixed

- **The chip row keeps its margins.** The page margin sat inside the scrolling
  row, so a chip scrolled to the end touched the screen edge and the row no
  longer lined up with the search field above it. The margin belongs to the
  window now, with a fade on the right when there is more to scroll.
- **A rule is edited on a page, not in a dialog.** Four matcher choices in a
  narrow column, a hint that broke mid-sentence, buttons in a corner and the
  add button peeking out behind the scrim — all of it was the dialog. The rule
  editor is a full page like the node editor: equal 2×2 tiles, one-line hints,
  a three-way segmented action, and two equal buttons at the bottom.
- **Export and restore fill the card.** Two buttons sized to their own text sat
  in the left half of a wide card and read as a group that had failed to centre
  itself.
- **A group exists from the moment it is created.** The chip row only drew
  groups that already had nodes, so a new folder was invisible; it is drawn
  now, empty ones included, and creating one switches the filter to it and says
  so. Deleting a folder asks what to do with its nodes instead of deciding.

## [0.9.5] — 2026-10-31

Three things the phone found that the desk did not.

### Fixed

- **The home page fits the screen it is on.** The fixed stack overflowed a
  700dp phone by a few dozen dp, so the health card needed a scroll on a page
  with air to spare above the ring. It now compresses in two steps — 680dp and
  below — while the ring stays 204dp and an 872dp screen keeps the 0.9.4
  composition pixel for pixel. Scrolling stays as the last resort.
- **"Check for updates" can install the update.** On the about page the row
  only ever re-checked, so a phone that had found 0.9.5 could not download it.
  The row now follows the state: check, then download, then hand the file to
  the installer, with a spinner while it works and a retry when it fails.
- **Importing a subscription looks like every other import.** It was a bare row
  with a chevron that unfolded a form; it is a tile now, opening its own page
  inside the panel, and it disappears when the clipboard already holds a
  subscription — one door per import.

## [0.9.4] — 2026-10-31

复查整改：首页在短屏上够得着，导航栏只剩一种选中信号，证据页重拍。

### Fixed

- **The home page scrolls in portrait too.** It only scrolled in landscape, so a
  640dp-tall phone clipped the health card behind the navigation bar with no
  gesture that could bring it back. The column now scrolls in every
  orientation; on a tall screen the content is shorter than the window and
  nothing moves.
- **Navigation selection is colour, not a pill.** The selected item is the
  accent — icon, label and a heavier label weight — and the bar no longer draws
  a shape behind it. Press feedback is Material's own ripple, one layer.

### Removed

- Two dead row composables in the settings screen, left behind when the about
  section moved to its own page.

## [0.9.3] — 2026-10-31

Navigation settles on the platform's own bar, the pages behind it get a hub, and
the add panel stops offering the same import twice.

### Added

- **Standard navigation bar.** A full-width Material `NavigationBar`, 80dp, with
  four destinations — Home, Nodes, Rules, More — a 64x32 selected pill, 24dp
  glyphs and labels that never hide. Every page has exactly one active item;
  the secondary pages light the hub.
- **The hub.** One card, five rows — scenes, logs, diagnostics, settings, about —
  each with its own semantic colour, and the version on the about row.
- **The about page.** The mark at 84dp, the name, the version, four entries
  (updates, what it does, privacy, licences) and a colophon.
- **The plus disc.** A 56dp disc with the brand gradient drawn as a 1.6dp ring
  and a 26dp glyph, on the nodes, rules and scenes pages only. Nodes opens the
  add panel; rules and scenes open their create dialogs.
- **Health card rows.** Exit IP and a smoothed round trip, each labelled.

### Changed

- **Add panel.** The clipboard card and the clipboard tile are mutually
  exclusive: with something importable on the clipboard the card is the one-tap
  import and the tile is gone; without it the tile is the only door and opens
  the paste form. The masked preview shows protocol, host fragment and port —
  never the credentials.
- **Switches** are Material's own, with the task book's colours: a 16dp thumb
  when off, a 24dp thumb with a tick when on, and a deep-blue thumb at night.
- **Settings** no longer carries an about card, and ends with backup and restore.
- **Node rows** show the protocol on the first line, so two nodes with one name
  are told apart without opening either.

### Removed

- The floating capsule navigation, the navigation drawer, and the page-level
  insets that existed only to clear them.

## [0.9.2] — 2026-10-30

Interaction and ergonomics overhaul, critical bugfixes, power saving, and database batching.

### Added

- **Floating capsule navigation.** A modern 44dp floating pill navigation island
  at bottom-center across the three primary views (Dashboard, Nodes, Rules).
  Expands active items with fluid spring animations and haptic feedback, while
  maintaining full clearance for the corner FAB and drawer navigation.
- **Batch database APIs.** Added `upsertAll` and single-column `setCountry` in
  `ProfileDao` and `ProfileRepository` for high-throughput node imports and
  in-place GeoIP updates without rewrites or UI re-render storms.

### Fixed

- **Group filter isolation.** Selecting a node group chip in `ProfilesScreen` no
  longer causes other group chips to vanish; valid non-empty groups are preserved
  globally from the unfiltered dataset.
- **Rules list bottom clearance.** Replaced short bottom padding with
  `StarveilSpacing.fabClearance` so the final rules card is not obscured by the
  FAB or capsule.

### Changed

- **Background radio and JNI hibernation.** Dashboard ViewModel now observes
  lifecycle transitions (`ON_START` / `ON_STOP`), immediately suspending the
  1-second kernel JNI polling and 30-second exit probe HTTP requests when
  backgrounded or navigated away, eliminating idle battery drain.
- **Macrobenchmark suite.** Upgraded `BaselineProfileGenerator` and
  `NodesScrollBenchmark` with robust bilingual UI Automator selectors and
  capsule/drawer compatibility.

## [0.9.1] — 2026-10-30

The add flow, rebuilt. 0.9.0 shipped a speed dial of pills on the nodes page and
a pair of header buttons on the other two; this release replaces all three with
one panel, and works through the details the 0.9.0 screenshots turned up. The
tunnel, the routing engine and the protocol code are untouched.

### Added

- **One create entry, three pages.** A 56dp gradient FAB opens the same
  content-height sheet on Nodes, Rules and Scenes; only the sheet's content
  changes. The node page's pill stack, the rules page's header button and the
  scenes page's button are gone, and the node page no longer offers "new
  scene" — a scene is not a node.
- **Clipboard import.** The panel reads the clipboard once when it opens and
  offers what it finds as a card at the top; the nodes page reads it once on
  resume and shows an in-page bar. Content is deduplicated by hash, so the same
  link is offered once. With nothing usable the tile is greyed out and says
  why, rather than failing on tap.
- **A full-screen scanner.** CameraX preview, a 24dp viewfinder, torch, gallery
  decoding, and continuous scanning: it counts what it has read instead of
  closing on the first code, flashes the frame and gives one haptic tap. A
  scanned subscription URL asks whether to import it as a subscription.
- **Manual add in two taps.** The protocol picker dialog is gone; the editor
  opens directly and carries the protocol as a chip row. A "paste a link" row
  fills every field and collapses into an undoable receipt. Transport, TLS and
  REALITY sit behind one "advanced" row.
- **Import feedback.** The list scrolls to the row an import produced and
  flashes it for 1.2s. A batch reports how many landed and how many lines
  failed; a link that resolves to a node already in the list adds nothing and
  points at the existing row instead.
- **Empty states with both ways in.** The nodes page centres the brand star as
  an outline and offers "add node" and "import a subscription" — the second
  opens the panel with the subscription row already unfolded.

### Changed

- The editor's save action is a sticky bar that names the first missing field
  ("still missing: port") instead of greying the button out, and pressing it
  scrolls to that field and focuses it.
- Subscription imports finish inside the panel: the sheet stays open, reports
  what arrived and offers a jump to the group. An unnamed subscription is named
  after its host instead of every one being called "Subscription".
- Rules can be created in the panel — type, match value and action — with the
  full editor one row away. A new rule is highlighted in the list for 1.2s.
- Scenes are named in the panel and then handed to the existing editor.
- Diagnostics (renamed from "rule test") gains a paste button, three example
  chips and a result area that fills the page instead of leaving the bottom
  half blank.
- Settings: the Kill Switch card and the backup card have headings, and export
  and restore are outline buttons rather than bare text links.
- The drawer's selected row is a pill inset 12dp on both sides; Scenes,
  Diagnostics and Home have their own glyphs; the header carries a
  display-only connection state.

### Fixed

- **Two nodes whose names differ only past the ellipsis are now
  distinguishable.** The collision test runs on the truncated prefix, and the
  suffix prefers the port, then the protocol, then an ordinal. The second line
  elides the host in the middle and keeps the port as its own segment, so
  "which port" is never the part that gets cut.
- One filled star per row: the gradient star means "the tunnel is on this
  node", and the favourite mark is an outline on the second line.
- The exit row says "exit round trip … (measured through the node)"; the health
  line says its EMA is smoothed from node probes. Three numbers, three stated
  sources.
- The page bloom is confined to the home page and the drawer header; the
  connection card's gauze stops at the information area and no longer runs
  under the segmented control.
- The local-network note is a footer of the preset card — built in, in force
  for every preset — instead of reading as a footnote to "Global direct".
- Lists clear the gesture bar at the bottom.

## [0.9.0] — 2026-10-30

Scheme six: the navigation and the palette are new. The tunnel, the routing
engine and the node picker are unchanged.

### Added

- **A drawer, and no bottom bar.** Pages are reached from a 280dp drawer:
  Pages (Home, Nodes, Rules, Logs), Settings and diagnostics (Settings, Scenes,
  Diagnostics), About (repository, changelog, issues). Each destination keeps
  its own scroll and filter state.
- **A day and a night table.** One token set per mode, every Material role
  mapped explicitly — including the three tonal containers that used to fall
  back to Material's lavender. Follow system, day, or night, persisted in
  DataStore; the top-bar button is a shortcut to the same setting.
- **A C2 adaptive icon.** The star is the icon; the gauze is clipped inside it,
  and a monochrome layer follows the system's themed icons.
- **A node-page speed dial.** Add node, import subscription, new scene. Each
  opens a centred dialog, not a bottom sheet.
- **A persisted auto/manual node mode.** Choosing a node by hand writes manual;
  choosing auto picks now and survives a restart.
- **Four node states**: untested (hollow), online (accent dot + latency),
  timeout (red), selected (brand star + accent outline), with selected and
  timeout allowed to coexist.
- **A compact About card** with the C2 mark, version, kernel, update/privacy
  buttons and the three repository links.

### Changed

- The home ring is a fixed 204dp; connected draws the brand gradient, idle the
  grey ring. The centre is the star, the state, and one line of instruction.
- The connection card carries the gauze and a two-segment auto/manual control;
  the card is opaque, the lists stay opaque.
- Node chips are generated only for groups that hold nodes. Subscription-backed
  groups no longer produce a chip.
- Search fields, chips, switches, dialogs and the type scale follow the new
  token table. Page titles are 22/700, card titles 17/600, numbers are tabular.
- The system bars take the page colour in both modes, with the icon grade
  following the theme.

### Removed

- The bottom navigation bar and its variants.
- The first-run onboarding flow; the home ring, the Nodes empty state and the
  FAB already carry the same actions.
- The Subscription chip and the scene FAB.

### Fixed

- Auto/manual no longer sticks in one direction; the mode is persisted.
- Empty groups no longer leave a chip behind after a subscription refresh.
- Dark-mode text that had fallen back to Compose's default black on the scene,
  rule-flow, editor, empty-state, keep-alive, per-app and privacy screens.
- System-bar seams on secondary pages.

## [Unreleased]

## [0.8.7] — 2026-10-26

### Added

- **A live connection table.** Settings → Data, security and diagnostics →
  Connection details. One row per connection the kernel decided: time, app,
  domain, node, rule, bytes, duration, and whether it is still open. The
  kernel's events carry byte *deltas*, not totals, and an update event carries
  no connection object at all, so the table folds NEW/UPDATE/CLOSE into one
  row and accumulates the counters itself. Filters: app, node, rule, and a
  Live/All switch. The latency column is the exit node's measured latency —
  the kernel records no round-trip time per connection, and the screen says so.
- **A per-query DNS log.** The aggregate ring now has a 100-entry companion
  that keeps the name, the resolver, the duration and the outcome. The screen
  says "GeoIP probe, not tunnel DNS" before it shows a number, because the ring
  is fed by the app's own DoH lookups for node hostnames, not by the tunnel.
  Export is a preview then a copy: domains and timings, never the resolver.
- **A staged node test.** Long-press a node → Staged test. DNS, TCP, TLS and
  HTTP, always four rows, each with a duration or a stated reason it does not
  apply: UDP protocols are not probed with TCP, REALITY is not probed with a
  plain TLS handshake, and no proxy protocol here is HTTP. Diagnosis only —
  the health score is not touched.
- **A diagnostic bundle** for a GitHub issue. Versions, counts, DNS, the leak
  result and the last 100 log lines — never a link, password, UUID, token,
  subscription URL, node address or API token. Exact values are removed first,
  then the shapes; the preview is the only thing between the bundle and the
  clipboard, and nothing is uploaded.
- **Search in Settings.** Filters the four groups by their titles and
  descriptions, plus the words people actually type ("kill", "vpn", "backup").
- **Favorites.** Pin a node from its long-press menu; pinned nodes sort first
  and the Favorites chip shows only them. Schema v6 adds `profiles.favorite`.

### Changed

- **The home health card is a summary again.** Availability plus one sentence,
  with the curve one tap away and a legend that separates availability, latency
  EMA and loss dots. An empty reachable set says why in words instead of
  leaving a row of red dots to be read as "the tunnel is broken".
- **Scenes**: the empty state is a card with an example and a centred button,
  and creating a scene is a sheet whose save button sits above the keyboard.
  Wi-Fi names can be picked from the networks the device knows when the SSID is
  readable, with the text field still the whole input when it is not.
- **Rules**: the four matchers sit in two rows instead of a scrolling row with
  a fade, and the value field carries an example and a hint per matcher —
  written from the fields the config builder actually emits, so `domain` is
  exact and `domain_suffix` needs no leading dot. A rule can be moved to the
  top or the bottom in one action; a 35-rule list needed 34 taps before.
- **Settings' DNS row is one line of state** against the project's 30 ms p50
  budget, and taps through to the aggregate and the per-query log.
- **The exit line names both numbers** ("Exit IP … · round trip … ms") and
  taps through to address, round trip and measured-at. A first-connect hint
  ("tap the ring") appears once, in the slot that already existed, so showing
  and hiding it cannot move the ring.
- **Two nodes with the same name are told apart** by a host:port suffix, only
  when the names actually collide.
- **A selected node that is down says so** inline ("selected, unreachable").

### Fixed

- **The staged test reported "stuck at TLS" for nodes with no TLS.** A VLESS
  node with `security=none` was handshaken with a port that has no TLS, and
  the node was blamed for it.
- **Search fields had no clear button at all.** The node list passed an
  `onClear` the shared field never used; the only way out of a long query was
  the keyboard's backspace.
- **The rule test screen rendered its title twice** (app bar and page header).
- **Selecting a node changed the row's height.** The "selected, unreachable"
  warning pushed the second line onto a third one; it is one text now, with the
  warning as a red span, so the row height does not move.

## [0.8.6] — 2026-10-26

### Fixed

- **Domain rules now match anything at all.** The generated routing config had
  no `sniff` action, and a connection arriving at the tunnel carries only a
  destination address — the app resolved the name itself, or the hijacked DNS
  did it on the app's behalf. Every domain rule was therefore dead on arrival:
  the ad list sat above the preset and never fired, `geosite-cn` never fired,
  and a domain rule written by hand never fired. A sniffer now runs as the
  first rule, over TLS, HTTP and QUIC only, so a plain SSH or WireGuard
  connection is not held waiting for a name it will never send. On the Fire TV
  this turned `doubleclick.net` from a 301 through the proxy into a local
  rejection in 66 ms, and moved `www.baidu.com` from the proxy to
  `outbound/direct`.
- **Every input field has a border before you touch it.** Fields sat on a
  transparent fill with a white 75% outline, which is invisible on both the
  page and the dialog surface: the resting border was zero pixels. They now
  share one component with a visible `ink3` resting border and the accent
  border on focus, across six dialogs and three page-level fields.
- **A region group shows the nodes that are in it.** "United States" was an
  empty list because region groups are derived and own no nodes; the filter
  now matches by resolved region instead of by group id.
- **The matcher row says it can scroll.** A chip row cut off at the right edge
  looked complete; it now fades out when there is more to the right.
- **A rate never breaks its unit.** The value and the unit are separate
  texts, and `B/s` wrapped to `B/` + `s` on a narrow column. Both are
  single-line and non-wrapping now.

## [0.8.5] — 2026-10-26

### Added

- **The subscription's own quota, when the provider publishes one.** The nodes
  page shows `used / total · days left` from the `subscription-userinfo`
  header. Providers that do not send it get no line at all — a "no data" banner
  would be a permanent apology for something the user cannot act on.
- **Where traffic leaves, and how long the round trip takes.** The dashboard
  ends with one line: the address a third party sees, and the HTTPS round trip
  behind it. A proxy that silently fell back to the local connection looks
  exactly like one that works until that line says otherwise.
- **A wake that finds a dead tunnel rebuilds it.** Screen-off closes the sockets
  the kernel cannot see; on unlock the exit is probed twice, and only two
  failures trigger a rebuild.

### Changed

- **A new install starts on a policy that works**: mainland direct, everything
  else through the tunnel, ads blocked — stated in three lines at the top of
  the rules page, dismissed in one tap. The mode is derived from "no choice
  stored yet", so it cannot disagree with the routing actually in force.
- **The rules page numbers its rules and ends with "everything else"**, so the
  order is visible and the list has a bottom instead of stopping at the last
  rule the user happened to add.
- **Auto-select explains itself.** Every decision writes the probe, the
  cooldown, the stickiness margin, the challenger's score and the delta to the
  log — including the ones that kept the current node.
- **Nodes that just timed out are folded out of the list** rather than listed
  as if they were candidates.

### Fixed

- **The kill switch no longer claims a block it is not enforcing.** A failed
  rebuild left the "blocking" flag set, so the dashboard said 阻断中 with no
  tunnel and traffic going out directly.
- **The kill switch survives the process.** Being killed took the kernel with
  it and left the chosen policy unenforced; it is now put back on the next
  start.
- **The kill switch description** no longer says the modes end with the process,
  because they no longer do.

## [0.8.4] — 2026-10-25

### Fixed

- **The first screen has the depth the rest of the app has.** Onboarding
  painted its own flat page colour, so the ambient gradient stopped at it —
  the one screen in the app with no material behind the glass. It now sits on
  the same background as everything else.
- **Nothing inside the onboarding ring.** A translucent disc filled it, which
  was invisible on a flat page and reads as a smudge once there is a gradient
  behind it. The ring is a line, and the page shows through it.

## [0.8.3] — 2026-10-25

### Changed

- **The app has a new face: frosted glass.** A light page with an ambient
  gradient, translucent white cards with a 1dp highlight edge, one blue for
  marks, and no glow anywhere. The old face — pure black under a saturated
  cyan ring with a halo — was too harsh to look at for long; this one caps the
  page at 14.3:1 instead of 21:1, and spends the accent on lines and dots
  rather than fills.
- **The ring is a line.** Same size and position in every state, no halo:
  connected is one blue stroke, connecting adds a rotating highlight on the
  ring, warning is amber, blocked is red.
- **The health curve fills at 14% and fades to nothing**, instead of a
  saturated block under the line.
- **The node list reserves 72dp on the right**, so the latency column never
  passes under the add button while scrolling.

### Fixed

- **The connecting sweep was drawn outside the ring.** `drawArc` defaults to
  the canvas's radius, which is 22dp wider than the ring; the arc now shares
  the ring's radius. It was there on the dark face too, invisible against
  black.
- **Accent and error text use their own grades** (`#0066CC`, `#BE3238`).
  The mark colours are 4.01:1 and 3.33:1 as body text on the new page, which
  is under AA.

### Notes

- The ambient background is rendered into a bitmap once per size and blitted
  afterwards. Shading it every frame cost 97% janky frames on a Fire TV; the
  baked version measures 4.7%, the same as 0.8.1.

## [0.8.2] — 2026-10-24

### Added

- **DNS sampling, in developer options.** Every lookup is timed — DoH, system
  fallback, cache hit, literal address, failure — into a 512-sample ring that
  costs 4 KB, and the row under the switch reads `n · p50 · p95 · fallback% ·
  cache%` with a clear action beside it. Turning the switch off stops the
  recording without discarding what is already there. Measured on a real
  network: 224 samples, p50 51 ms, p95 138 ms, 0% fallback.

### Fixed

- **The header icon said it tested every node, and tested one.** Its
  accessibility label was "Test all nodes"; the action is
  `testSelectedNode()`, and with nothing selected it answers "Pick a node
  first." The label now says what happens: "Test the selected node".
- **The health curve's first frame comes from what is already stored.** The
  window is applied at read time, but the write path pruned by age inside the
  insert transaction, so the first probe after a night away deleted the very
  history the curve was about to draw — every cold start began at "collecting".
  (This was listed under 0.8.1, but the fix landed after that tag; it ships
  here.)

## [0.8.1] — 2026-10-24

### Fixed

- **The auto-pick is silent when nothing changes.** It compared the card's
  label (flag included) with the stored name, so the same node always looked
  like a change and every press raised a notice. It compares ids now, says
  nothing when the pick is the node already in force, and lets the card carry
  the result. The badge states the mode — Auto or Manual — and the action is a
  text link under the node rather than a pill that looked like a label.
- **No red block behind the node rows.** `SwipeToDismissBox` composes its
  background at rest, so a filled background painted a red rectangle behind
  every row and tinted the list warm. It is drawn only once a drag commits.
- **The latency is a fixed 76dp column, right-aligned**, so the unit is never
  clipped by a long node name.
- **The rules page's empty state is a card**, like every other block on it.
- **Settings groups are 16dp apart** instead of 32, and the caption sits 8dp
  above its card.
- **The rule market's subtitle is one line**, and the Kill Switch link has a
  line of its own instead of squeezing the paragraph beside it.

## [0.8.0] — 2026-10-23

### Changed

- **The whole app has one face: 曜.** The light theme is gone, and so is the
  picker. A pure black page, near-black glass cards with a 1dp edge and a 20dp
  corner, and one cyan-to-violet accent spent at most three times per screen —
  the ring, a selected marker, an on switch. Type is one sans family (display
  numbers at Light 54sp) plus the system mono for units and protocol names; the
  serif is gone with the second palette.

- **The dashboard is an instrument.** The ring is 240dp with a 4dp stroke in
  every state and never moves, so nothing below it shifts when the tunnel
  changes. The two rate columns share a baseline on each of their three rows,
  and the unit sits on the digits' baseline instead of hanging at the top
  right. The node and the health curve each get a glass card; the curve is
  1.5dp of accent over an 18% fade, not a green block.

- **Lists are one card.** Nodes, rules and the user's own rules group their
  rows inside a single glass card with hairlines instead of a stack of tinted
  blocks. A selected node is a 12% cyan wash with a 2dp leading edge.

- **Settings has groups.** Connection, per-app routing, background, backup,
  leak self-check, developer options and about — each a caption and one card,
  with the value or the control on the right.

- **The log is bare.** Mono 12.5sp on black, no card and no row rules; a level
  is a word colour, not a block.

### Fixed

- The bottom navigation clears the system navigation inset, so its labels are no
  longer under the gesture handle and a tap in that strip is no longer taken by
  the system.
- A floating button sits 16dp above the bar again; the content was being inset
  by the navigation bar twice.

### Removed

- The theme preference (`ThemeMode`, `dynamicColor`) and the appearance group
  in Settings, along with their strings. There is one theme.

## [0.7.14] — 2026-10-21

### Added

- **Share links open the app**: `ss://`, `vless://`, `vmess://`, `trojan://`,
  `hysteria2://`/`hy2://`, `tuic://` and `wireguard://` are registered, so a
  node can be handed to the app instead of copied, switched over and pasted.
  `http`/`https` are deliberately not registered — a subscription URL is one of
  those, and claiming them would put the app in the chooser for every link
  tapped in a browser.

  The link is imported on arrival and the app switches to the Nodes tab, where
  the result is shown. It goes through the same import path as a pasted link,
  so the outcome reads the same and there is no second copy of the behaviour to
  drift.

  `singleTask` comes with it: without it, tapping a link while the app is open
  starts a second copy whose tunnel belongs to the first.

### Fixed

- **Build**: `:core:data` declared the `ACCESS_NETWORK_STATE` permission it uses.
  The permission was already in `:app`'s manifest, so nothing was broken on a
  device — but lint checks the module that makes the call, and the module that
  reads the current link's addresses was new in 0.7.10 and had never been
  linted on its own. Declared rather than suppressed: a module's manifest is
  what tells its consumer what it needs.

## [0.7.13] — 2026-10-21

### Added

- **Search on the node list**: a subscription of a few hundred nodes could only
  be narrowed by group, which is useless when the name you half-remember is the
  only thing you have. The field matches the name and the address, ignoring
  case, and it narrows within the selected group rather than replacing it. "No
  node matches X" is its own sentence, separate from "this group is empty" —
  the two failures need different things from the user.

## [0.7.12] — 2026-10-21

### Added

- **A tunnel that is up but carries nothing is called out**: "connected" only
  means the kernel accepted the config — a dead node, a wrong credential and a
  route into nowhere all look identical from there, and all three leave the
  phone with no internet while the icon says connected. Ten seconds after a
  connect, if the kernel has not reported a single proxied connection, the ring
  turns amber and says so, with a line in the Logs screen for the bug report.
  The signal is the kernel's own routing decisions, so it costs nothing and
  measures the node rather than the tunnel.

## [0.7.11] — 2026-10-21

### Changed

- **A failed start stays on screen**: the reason used to arrive as a Snackbar,
  which was gone in three seconds and truncated to one line — so the one piece
  of information a bug report needs had to be retyped from memory. It now holds
  the context slot until the next attempt, and tapping it opens the whole
  message with a Copy button. The same treatment for "pick a node first" and
  "cannot build the config": those are conditions, not confirmations. Notices
  that describe something happening — dropping, reconnecting, auto-selected —
  stay one-off Snackbars, so a transient event cannot push the layout around.

## [0.7.10] — 2026-10-21

### Fixed

- **A tunnel that outlived its process**: the kernel's state lives in memory,
  so a new process starts believing nothing is connected — but the tun does
  not. The system keeps the VpnService across a process death (`START_STICKY`,
  and always-on VPN restarts it at boot), and while it is up every app is
  routed into a tunnel nothing is reading. The app said "not connected" and
  offered to connect, and connecting then failed on the cache database the dead
  daemon still held. Start-up now reconciles the two: a tunnel with no kernel is
  released, a kernel with no tunnel is stopped, and a tunnel with a kernel is
  left alone. When the release dropped a connection the user had asked to keep,
  it is restored through the ordinary connect path — a reboot with always-on VPN
  now looks like a reconnect instead of a phone with no network.

## [0.7.9] — 2026-10-21

### Fixed

- **`initialize cache-file: timeout`**: after a failed start the previous
  daemon can keep the cache database open for seconds, and every retry — the
  next tap on connect included — died the same way. A start that fails on the
  cache database now unlinks it and retries: the old holder keeps its inode and
  writes to a file nobody opens again, the new instance creates a fresh one.
  Up to three attempts, and the first connect after a failed one gets the same
  treatment instead of failing outright.

## [0.7.8] — 2026-10-21

### Fixed

- **A restart mistaken for a crash**: every in-place restart closes the command
  client on purpose, and that callback was indistinguishable from the kernel
  dying. It was read as an unexpected drop, which scheduled a second teardown
  and start on top of the first — the pair then failed with
  `initialize cache-file: timeout` while the tunnel from the first half was
  still up, so the app said "kernel failed to start" and the VPN was still
  connected. Each client now carries a generation, and a replaced client's
  disconnect is ignored.

## [0.7.7] — 2026-10-21

### Changed

- **Only the network you are on is kept direct.** The routing rule used to keep
  every private range — 10/8, 172.16/12, 192.168/16, fc00::/7 — out of the
  tunnel, which is right at home and wrong everywhere else: `192.168.50.10` is
  the NAS when the phone is in that house, and only reachable through the node
  when it is not. The rule is now built from the phone's own interface
  addresses, so the network it is standing in goes direct and every other
  private range goes through the node. No setting: moving networks rebuilds the
  tunnel once, which is the only way a static config can follow the phone.
  Loopback, multicast and link-local stay direct in every case.

  The switch added in 0.7.6 is gone. It asked the user to answer a question the
  phone can answer itself, and the wrong answer fails silently.

## [0.7.6] — 2026-10-21

### Added

- **Reach a private network through the node**: the routing page gained a
  switch for private addresses. Off, the default, keeps 10/8, 172.16/12,
  192.168/16 and fc00::/7 direct — right when the phone is on the same network
  as those devices. On, they take the tunnel, which is how a phone away from
  home reaches a NAS or a router that sits behind the node. Loopback and
  multicast stay direct either way, since a proxy cannot carry either, and the
  tunnel rebuilds when the switch changes.

## [0.7.5] — 2026-10-21

### Added

- **Subscriptions, wider**: a sing-box JSON subscription no longer drops
  REALITY outbounds — `public_key` and `short_id` map onto the node, and the
  SNI, ALPN and uTLS fingerprint come along. A Clash YAML subscription now
  reads vmess, hysteria2 and tuic instead of counting them as unsupported;
  hysteria2 keeps its obfs settings and its port range is converted into the
  form the kernel takes.

### Changed

- **Update failures are localised**: the update dialog used to append an
  internal English string ("no published release", "checksum mismatch") to a
  translated sentence. The data layer now returns a closed set of reasons and
  the screen owns the wording, so both languages get a sentence — and a new
  reason cannot ship without one.
- **XHTTP carries what the kernel reads**: a 3x-ui link's `extra` is no longer
  ignored. The fields sing-box 1.14 actually decodes — the uplink method,
  `no_grpc_header`, the three padding keys, and a downlink block that does not
  bring its own TLS — reach the config and are written back on export. Keys the
  kernel does not know are deliberately not written: options are decoded
  strictly, so an unknown key fails the whole config instead of being ignored.
- **Export keeps headers**: WebSocket headers were parsed from Clash and then
  dropped on export. They now round-trip, and `host` merges with them rather
  than replacing them.

### Fixed

- **VPN service leak**: the default-network callback was registered on every
  start and never unregistered, so each connect/disconnect cycle left one
  behind, holding the service. Both termination paths release it now, and the
  Logs screen says whether the release happened.
- **Kernel lifecycle**: start, stop, block and block-keeping-tunnel run one at a
  time. Tapping connect and disconnect in quick succession could interleave a
  teardown with a start.
- **Crash reports**: a REALITY public key is base64url, and the redaction rule
  only knew standard base64 — a key containing `-` or `_` was split into
  fragments shorter than the threshold and left the device verbatim.
- **Latency probe**: node hostnames are resolved over DoH, never through the
  system resolver, which the rest of the app already avoided. A DoH failure
  fails the probe rather than falling back to cleartext.
- **Backup restore**: the three writes share one transaction. A failure
  part-way used to leave groups and rules restored and the nodes missing.
- **Node flags**: a failed country lookup is retried after a minute instead of
  being remembered for the life of the process, so a list first read while
  offline gets its flags back without a restart.
- **Clash VLESS**: an entry that does not declare TLS is imported as plaintext.
  It used to be given a handshake it could not complete.
- **Group list**: membership comes from the profiles already in hand instead of
  one query per group per update. **Health samples** are written in one
  transaction instead of three.
- **IPv6-only nodes**: a node whose hostname has only an AAAA record could not
  be used. `dns.strategy` is `ipv4_only` so apps never receive an address the
  v4-only tunnel cannot carry, but that rule was also being applied to the
  proxy's own name — which the kernel dials on the underlying network, where
  IPv6 works. Node addresses now resolve `prefer_ipv4` (A first, AAAA when
  there is no A) while app-facing answers stay IPv4-only, and the DoH resolver
  behind the latency probe and the flags asks the second question too.

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
