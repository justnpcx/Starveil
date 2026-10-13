# libbox (sing-box core) build — M2 task 1

How Starveil gets the sing-box core it runs: the Go `libbox` shared library,
built from the Mirage fork and consumed as an Android AAR.

## Source

- Fork: `justnpcx/sing-box` (Mirage), branch `nb-next-1.14.x`, local tree `/root/sing-box`.
- Pinned commit used for the spike: **`027f5d1`** (`feat(xhttp): add stream-up mode and downloadSettings`), version string `1.14.0-mirage-1`.
- XHTTP / REALITY ML-KEM are fork patches kept as their own commits — do not squash them into sync commits.

## Toolchain (exact — these are load-bearing)

| Component | Version | Why pinned |
|---|---|---|
| Go | **1.25.5** | `go.mod` requires `go 1.25.5` |
| gomobile / gobind | **v0.1.13** (`github.com/sagernet/gomobile`) | the fork's `make lib_install` |
| Android NDK | **28.2.13676358 (r28)** | the fork's CI uses `ndk-version: r28`; see gotcha 2 |
| JDK | 17 | `build_libbox` calls `checkJavaVersion()` and requires `openjdk 17` |

## Two gotchas that cost real time

1. **`gobind was not found. Please run gomobile init`** — `gomobile` locates
   `gobind` on `PATH`. `$GOPATH/bin` (default `/root/go/bin`) must be on `PATH`,
   not just Go's `bin`. The build script sets it.
2. **`ld: error: ... unknown relocation (315) against symbol typeinfo for std::exception`**
   from `cronet-go/lib/android_arm64/libcronet.a` — the `with_naive_outbound`
   tag pulls a prebuilt static lib whose relocations the **NDK 25** linker
   cannot handle. **NDK r28 fixes it.** (The fork's legacy variant drops
   `with_naive_outbound`, which is why only the main variant failed on NDK 25.)

## Build

```bash
tools/build-libbox.sh                       # all ABIs
PLATFORM=android/arm64 tools/build-libbox.sh  # one ABI (fast)
```

Produces, in the fork tree:

| Artifact | API | Tags | sha256 | Size |
|---|---|---|---|---|
| `libbox.aar` | 24 | full (`with_naive_outbound`, gvisor, quic, wireguard, utls, clash_api, usbip, openvpn, openconnect, tailscale, …) | `9907beee8cf2b6d65f58fcb62be5168a220d56707af9288a4d7b9a72563d5039` | 28.5 MB |
| `libbox-legacy.aar` | 21 | full **minus** `with_naive_outbound` | `1851bb4b4752beda2bd094d61c303993a4626a1336dc4552d8bdbd022c3c74be` | 23.5 MB |

Checksums are for commit `027f5d1`, `android/arm64` only. A full multi-ABI build
changes the AAR contents (and therefore the hashes) — record the hash of
whatever artifact is actually consumed.

### Which AAR does Starveil use, and why

**`libbox.aar`** (the API-24 full variant). Starveil's `minSdk` is 29, so the
API-24 floor is irrelevant, and we want the complete outbound set. The
`-legacy` variant exists only for apps that still support API 21 and must drop
the naive outbound (cronet) to stay small — Starveil does not need it.

## AAR consumption strategy — decided in task 1

Three options; the spike deliberately did **not** commit to one yet:

| Option | Reproducible in CI | Cost |
|---|---|---|
| (a) CI builds libbox from source | Yes, but needs Go 1.25.5 + gomobile + NDK r28 in the runner, and a ~10 min build per CI run | workflow change required |
| (b) Check the AAR into the repo (`app/libs/libbox.aar`) | Yes, trivially | ~28 MB binary in git; upstream sing-box-for-android does exactly this |
| (c) Publish the AAR to GitHub Packages, consume by coordinate | Yes | needs a publish step + auth for a private package |

Recommendation to lock in task 1: **(c) if the publish plumbing is acceptable,
otherwise (b)**. (a) makes every CI run pay a Go/NDK toolchain cost that has
nothing to do with the app. If (a) is chosen, the workflow must install Go
1.25.5, gomobile v0.1.13 and NDK r28 and cache `$GOPATH/pkg/mod`.

Until this is decided, `RealKernelBridge` cannot be wired (it needs the
`io.nekohasekai.libbox` classes at compile time).
