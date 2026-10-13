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

## AAR consumption strategy — decided: checked-in binary

Decision (task 1): the built AAR is **checked into the repo at
`app/libs/libbox.aar`** and consumed with
`implementation(files("libs/libbox.aar"))` in `app/build.gradle.kts`.

Rationale, against the "which keeps CI green with the least churn" standard:

- **vs. CI builds from source**: that would make every CI run install Go 1.25.5,
  gomobile v0.1.13 and NDK r28 and spend ~10 min compiling — a toolchain cost
  with nothing to do with the app.
- **vs. GitHub Packages**: works, but adds a publish step and a second set of
  package read/write credentials to CI. Not worth it for one private repo.

Checking in the binary keeps CI unchanged and fully reproducible. Cost: a
~28 MB blob in git. The AAR is not code-reviewed; `tools/build-libbox.sh` plus
the sha256s above are how it is reproduced.

> Current artifact note: the checked-in AAR was built with
> `PLATFORM=android/arm64`, so it carries **arm64-v8a only** (enough for the
> test device, an arm64 vivo). A shippable build needs all ABIs — re-run
> `tools/build-libbox.sh` with no PLATFORM and replace the file before any
> release. Tracked as a task-1 follow-up.

## libbox Java API as this fork actually exposes it

Reconnaissance from the checked-in AAR's `classes.jar` (authoritative for what
to code against — the Go tree does not show the gobind-generated names):

```
Libbox.setup(SetupOptions)                                        // global init
Libbox.newCommandServer(CommandServerHandler, PlatformInterface) -> CommandServer
CommandServer.start()                                             // start the command server
CommandServer.startOrReloadService(configJson, OverrideOptions)   // start / hot-reload the box
CommandServer.closeService()                                      // stop the box
CommandServer.close()                                             // tear down
Libbox.newStandaloneCommandClient() -> CommandClient              // stats / urlTest
Libbox.version() / goVersion()
```

Two findings that shape the implementation:

1. **There is no `BoxService` in this fork.** Upstream sing-box's
   `Libbox.newService(...)` model is absent; the box is driven through
   `CommandServer.startOrReloadService(config, ...)`. `RealKernelBridge` must be
   written against the CommandServer model, not the upstream one.
2. **The app must implement `PlatformInterface` and `CommandServerHandler`**
   (both large interfaces). sing-box-for-android has a reference
   implementation; ours should stay minimal and must never log credentials.

`CommandClient.URLTest(outboundTag)` takes an **outbound tag, not a URL** — so
`KernelBridge.urlTest(link)` has to resolve the link to an outbound tag first,
rather than passing the URL through.

### CommandClient / TunOptions (for the VpnService + stats work)

```
Libbox.newStandaloneCommandClient() -> CommandClient
CommandClient.connect() / connectWithFD(fd) / disconnect()
CommandClient.urlTest(outboundTag)
CommandClient.selectOutbound(groupTag, outboundTag)
CommandClient.serviceReload() / serviceClose() / setClashMode(mode)
```

`TunOptions` is an **interface the kernel fills in**; the VpnService reads it to
build the tun: `getMTU()`, `getAutoRoute()`, `getStrictRoute()`,
`getInet4Address()`, `getInet6Address()`, `getDNSMode()`,
`getIncludePackage()/getExcludePackage()`, `getHTTPProxyServer()`, …

### Resolved: where traffic stats come from, and who owns the tun fd

**1. Traffic stats are pushed, not polled.** The fork has no
`getTrafficStats`. Stats arrive through
`CommandClientHandler.writeStatus(StatusMessage)`, and `StatusMessage` carries
exactly what `KernelBridge.queryStats()` needs:

| StatusMessage | TrafficStats |
|---|---|
| `getUplink()` / `getDownlink()` | `uplinkRate` / `downlinkRate` |
| `getUplinkTotal()` / `getDownlinkTotal()` | `uplinkBytes` / `downlinkBytes` |
| `getTrafficAvailable()` | whether the numbers are real |

This matches the `KernelBridge` contract's "pushed, never polled" wording: the
bridge should implement a `CommandClientHandler`, keep the latest
`StatusMessage` in a `StateFlow<TrafficStats>`, and have `queryStats()` return
that cached value instead of blocking on a call.

**2. The app owns the tun fd; the kernel asks for it.**
`CommandClient.connectWithFD(fd)` is *not* the tun path — the Go side wraps that
fd as a `net.Conn` and dials the command server over it
(`passthrough:///xpc`), i.e. it is the remote/XPC command channel. The tun fd
comes from the other direction: the kernel calls
`PlatformInterface.openTun(TunOptions)`, and the **app's VpnService** calls
`VpnService.Builder.establish()` and returns the resulting fd.

So the remaining task-1 shape is:

```
StarveilVpnService (foreground, VpnService)
  ├─ on start: Builder.establish() -> fd
  ├─ implements openTun(TunOptions) -> that fd
  └─ RealKernelBridge.start(config)
        ├─ Libbox.setup(...) -> newCommandServer(StarveilCommandServerHandler, platform)
        ├─ newCommandClient(StarveilCommandClientHandler, ...) -> connect()
        │     └─ writeStatus(StatusMessage) -> StateFlow<TrafficStats>
        └─ server.startOrReloadService(config, ...)
```

VPN consent denied maps to `KernelError.PermissionDenied`; `urlTest` resolves a
link to an outbound tag and calls `CommandClient.urlTest(tag)`.
