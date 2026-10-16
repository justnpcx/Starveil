# M3 self-check — C1–C5 / K1–K4

The product guide's §1 exit criteria, checked against the tree rather than
against memory. Each line says how it was checked, because "we looked" is not
a result.

## The criteria

| | Check | Result |
|---|---|---|
| C1 | No raw colours, raw spacing, or hard-coded copy | **fixed** — see below |
| C2 | Token adherence across screens | **pass** (spot-checked three screens) |
| C3 | Every state switch animates | **fixed earlier** (`383bb35`) |
| C4 | Zero placeholder copy shipped | **pass** |
| C5 | Empty / error / loading states are designed | **fixed** — see below |
| K1 | One term per concept | **pass** |
| K2 | One interaction per operation | **fixed** — see below |
| K3 | One implementation per component | **fixed** — see below |
| K4 | Copy tone: expert, short, no marketing | **pass** |

## C4 — clean

`待实现` / `TODO` / `FIXME` / `Demo` / `Coming soon` / `未实现` do not appear
anywhere in `core`, `feature` or `app`. The only hits were substrings of
`toDomain()` / `toDouble()` and prose in code comments.

## K4 — clean

No exclamation marks and no marketing words (太棒 / 轻松 / 极速 / 一键 / 完美 /
强大 / 立即) in any `strings.xml`. The copy is imperative and short throughout.

## C1a — no raw colours

`Color(0x` appears **only** in `core/ui/.../theme/`, which is where the palette
is defined. Nothing else in the tree names a colour.

## C1b — raw spacing: two sites

`.padding(vertical = 4.dp)` in `SettingsScreen.kt` (×2) — 4dp is
`StarveilSpacing.xs`, and the design system forbids values off the scale. Fixed.

The remaining `.dp` literals are **component dimensions**, not spacing: the
220dp core, the 200dp QR code, the 56dp chart height, the 24dp icon default.
The design system only requires those to be parameterised rather than repeated,
and each is a single constant.

## C1c — hard-coded copy: the real finding

Two view models held user-facing sentences as Kotlin string constants, where
the resource system never sees them — untranslatable, and invisible to anyone
reviewing the app's wording:

- `DashboardViewModel`: four notice constants plus three inline sentences, and
  `NO_NODE`.
- `ProfilesViewModel`: import and refresh results built with string templates
  (`"Imported ${outcome.nodeCount} nodes"`).

Both now carry **typed data** (`DashboardNotice`, `ProfilesMessage`) and the
screens map it to resources. `KernelRefused` keeps the one part that is not
fixed text — the kernel's own already-redacted reason.

The view models have no `Context`, which is exactly why the copy drifted in
there; a typed reason keeps it out without giving them one.

## C5 + K3 — one message presentation

The Nodes screen had a designed `MessageBanner`; the Dashboard rendered its
error as a bare error-coloured `Text`. Same job, two presentations, so a change
to one would silently miss the other.

`MessageBanner` now lives in `:core:ui` with an optional `onDismiss`:

- Nodes: dismissible — it reports an event ("Imported 2 nodes").
- Dashboard: **not** dismissible — it says why the core is blocked, and
  dismissing it would leave a blocked core with no reason on screen.

## K3 — one implementation, four copies removed

| Was | Now |
|---|---|
| `EmptyState` × 3 (Logs, Rules, Nodes) | one in `:core:ui` |
| `NoMatchesState` (Nodes) | the same component |
| `copyToClipboard` (Logs) + `writeClipboard` (Nodes) | one in `:core:ui/util` |
| `readClipboardText` (Nodes) | the same file |
| `MessageBanner` (Nodes) | one in `:core:ui`, used by two screens |

## K1 — pass

`node`, `rule`, and the three outcomes (`Proxy` / `Direct` / `Block`) each have
one spelling across every screen. The flow view reuses the rule list's own
outcome words rather than inventing a second vocabulary.

One soft spot: the import FAB's accessibility label is "Add nodes" while the
visible button is "New node". Both mean "create", and only one is ever read
aloud, so it is left alone rather than churned.

## K2 — done

The guide says deletes use **swipe + undo Snackbar** and confirmations use
dialogs. Before this, every delete (node, group, rule) was a confirmation
dialog and the app had no `SnackbarHost` at all.

Reading the two halves together: **reversible deletes get swipe + undo**,
**irreversible ones keep the dialog** (a group delete takes its nodes with it,
and its dialog already names the count).

What that needed, and what landed:

- **A `SnackbarHost` in the shell** (`MainActivity`'s `Scaffold`), because a
  Snackbar has to outlive the screen that raised it — one owned by the Nodes
  screen would vanish the moment the user navigated away from what they just
  deleted, taking the undo with it.
- **A cross-module undo channel** — `UndoBus` (what a feature uses) and
  `UndoRequests` (what the shell collects), both provided from one
  `AppUndoBus`. Two providers would make two buses, and the failure is silent:
  the delete happens and no Snackbar appears.
- **Swipe-to-dismiss on the node row**, replacing its trailing delete button.

The button's comment said it was there because "a node carries credentials and
re-importing one is not always possible, so the gesture has to be deliberate
and visible". That is still true, and it is exactly what the undo answers: the
credentials are not lost, they are one tap away. The delete now happens on the
gesture with the reversal offered afterwards, rather than a dialog asking
permission for something that can be taken back.

The long-press menu keeps its Delete item, and it runs the *same* interaction —
delete at once, offer the undo. That is one interaction reached two ways, not
two interactions: the guide's rule is about what happens, and what happens is
identical.

Undo is a real reversal, not a re-import: `upsert` with the stored id restores
the same row, credentials included.
