# Contributing

Starveil is a single-maintainer project. Issues and pull requests are read, but
a change that arrives without a reason in it is hard to judge — the sections
below are what a reviewer looks for.

## Build

JDK 17 and the Android SDK (platform 36, build-tools 36.0.0). No other setup:

```bash
./gradlew :app:assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/`, one per ABI.

A release build additionally needs the signing key, which is not in the
repository:

```bash
export STARVEIL_KEYSTORE=/path/to/release.jks
export STARVEIL_KEYSTORE_PASSWORD=…
export STARVEIL_KEY_ALIAS=starveil
export STARVEIL_KEY_PASSWORD=…
./gradlew :app:assembleRelease
```

Without those the build still succeeds and produces an **unsigned** APK. That
is deliberate: CI without the key must not fall back to some other key.

## The gates CI runs

Every push runs, in this order:

| Gate | What it catches |
|---|---|
| `./gradlew checkTranslations` | a key present in one locale and not the other, or a format specifier dropped in translation |
| `./scripts/leak-scan.sh` | a personal hostname, a token, a build-machine path |
| `./gradlew :app:assembleDebug` | the app compiles |
| `./gradlew testDebugUnitTest` | behaviour, including the config the kernel is handed |
| `./gradlew :app:lintDebug` | platform rules, permissions, unused resources |

Run the same commands locally before opening a pull request. A PR whose CI is
red is not reviewed.

## Commits

- Subject in the imperative, under 72 characters: `fix(rules): the local
  network is always direct`, not `Fixed the LAN rule`.
- The body says **why**, not what. The diff already says what.
- When a change is verified on a device, say how — the sequence of taps, the
  log line, the number. "Verified on the V2520A" alone is not verification.
- When a change is a pure move, say so in as many words: "behaviour
  unchanged", plus the tests that prove it.
- No secrets, no real hostnames, no absolute paths from your machine. The leak
  scan is the mechanical half of that rule; the other half is reading your own
  diff.

## What this project will not take

- A new protocol or a new kernel feature: the kernel is a self-maintained fork
  of sing-box pinned to 1.14.x, and moving it is a release-sized decision.
- A dependency that replaces something written here in under a hundred lines
  unless it fixes a correctness problem.
- Anything that makes the app reach the network on its own initiative. It is a
  proxy client: the only traffic it starts is a TCP handshake to a node the
  user configured, and a rule-set download the user asked for.

## Licensing

Starveil is GPL-3.0 (see `LICENSE`). By contributing you agree that your change
is licensed the same way. Source files do not carry per-file headers: the
licence is stated once at the root, and a header on every file would be a
maintenance tax that the GPL does not require.

## F-Droid

Not submitted, and not buildable there as it stands. The kernel is a prebuilt
`core/kernel/libs/libbox.aar`, produced by `tools/build-libbox.sh` from
`justnpcx/sing-box`; F-Droid builds from source and does not accept prebuilt
binaries. The fork itself is public, so the gap is a build recipe inside this
repository — a Gradle task that builds the `.aar` from a pinned commit — not a
licensing one. Everything else is free software: Compose, Hilt, Room,
SQLCipher, Sentry's SDK, and the rule sets are downloaded at runtime rather
than vendored.

Before a submission: reproducible build, a version tag that matches the app's
version name, and no proprietary dependency anywhere in the graph.
