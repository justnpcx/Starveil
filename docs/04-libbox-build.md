# libbox (sing-box core) build

How Starveil gets the sing-box core it runs: the Go `libbox` shared library,
built from the Starveil fork and consumed as an Android AAR.

## Source

| | |
|---|---|
| Upstream | `SagerNet/sing-box`, tag **`v1.14.3`** (`7054cac`) |
| Fork | `justnpcx/sing-box`, branch **`starveil-1.14.x`** |
| Pinned commit | **`647c447`** |
| Reported version | **`1.14.3-647c447`** |
| Local tree | `/root/sing-box` |

The branch is upstream v1.14.3 plus four commits, and nothing else:

| Commit | What | Why it exists |
|---|---|---|
| `a267ba4` | XHTTP `stream-one` transport | not in upstream |
| `74f4725` | XHTTP `stream-up` + `downloadSettings` | not in upstream |
| `c7e5523` | REALITY `support_x25519mlkem768` | upstream still strips X25519MLKEM768 from the ClientHello unconditionally |
| `647c447` | VLESS: multiplex wins over flow | upstream sends both when a node carries both |

**The reported version is derived, not written down.** `cmd/internal/build_libbox`
runs `git describe --tags` and injects the result as
`-X github.com/sagernet/sing-box/constant.Version=…`, overriding the literal in
`constant/version.go`. Because the four commits sit on top of the tag,
`ReadTag()` returns the tag plus the short sha — which is exactly what a bug
report needs. This is also why an untagged tree builds a core that reports
`unknown`: the tag is the source of truth, and a tree without one has nothing
to report. (`ReadTag` is in `cmd/internal/build_shared/tag.go`.)

### The branch has no history in common with the old one

`starveil-1.14.x` was not rebased from `nb-next-1.14.x`; it was started from
upstream `v1.14.3` and the four commits were replayed onto it. The old branch
descended from the MatsuriDayo/neko fork's 1.14.x, which is a *different* base —
so a rebase would have carried that layer along. Replaying meant deciding about
it, and the answer was that almost none of it was ours to keep: see
"Upgrading the core" below.

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
Libbox.newStandaloneCommandClient() -> CommandClient              // stats / logs
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

`CommandClient.URLTest(outboundTag)` takes an **outbound tag, not a URL**, and
the kernel has to be running for it to mean anything. We therefore do **not**
use it: node latency is probed directly from the app (a TCP handshake to the
node's `server:port`), so it works before connecting and for any node rather
than only the connected one. See `NodeLatencyTester`.

### CommandClient / TunOptions (for the VpnService + stats work)

```
Libbox.newStandaloneCommandClient() -> CommandClient
CommandClient.connect() / connectWithFD(fd) / disconnect()
CommandClient.urlTest(outboundTag)          // unused: see the note above
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

VPN consent denied maps to `KernelError.PermissionDenied`. Node latency is not
asked of the kernel at all — `NodeLatencyTester` probes the node's
`server:port` directly.

## Rebuilding libbox for all ABIs (release blocker)

`core/kernel/libs/libbox.aar` is a checked-in artifact. The one that shipped
first was **arm64-v8a only**, which made the app installable on exactly one
ABI — the app carried `ndk { abiFilters += "arm64-v8a" }` to make that honest
rather than let a non-arm64 device install and then die on `loadLibrary`.

To ship to armeabi-v7a and x86_64 as well, rebuild the AAR and let the app
emit one APK per ABI.

### Building — one ABI at a time, always

```sh
# All three env vars matter — see the traps below.
export PATH=/opt/jdk17/bin:/opt/go/bin:$PATH
export JAVA_HOME=/opt/jdk17
export ANDROID_HOME=/opt/android-sdk

cd /root/Starveil
PLATFORM=android/arm64 bash tools/build-libbox.sh   # then arm, amd64, 386
```

**Do not run it with no `PLATFORM`.** `gomobile bind` compiles every ABI in
parallel and each one compiles the whole sing-box tree in parallel, so the
all-ABI build on this 4-core / 12 GiB box reached a load average of **138** and
took the machine down. One ABI peaks at ~1 GB and a load of ~3.4, which is
comfortable, and four of those in sequence cost wall time and nothing else.

The per-ABI build also fails cleanly where the parallel one did not: an
interrupted run leaves `build/<abi>/` behind and the next run refuses to start
with `open …/go_libboxmain.go: file exists`. `rm -rf build/` and go again.

`/root/build-one-abi.sh <abi>` wraps a single-ABI build with a memory guard
that kills it if available memory falls below 1.5 GB, and logs to disk rather
than to `/tmp` (which is tmpfs here, so a build log is RAM).

It produces `libbox.aar` (main variant) and `libbox-legacy.aar` (drops
`with_naive_outbound`). Starveil uses the **main** one.

### Two traps, both silent until late

1. **`ANDROID_HOME`, not `ANDROID_SDK_ROOT`.** `gomobile`'s `internal/sdkpath`
   reads only `ANDROID_HOME`. Setting just `ANDROID_SDK_ROOT` fails
   immediately with `FATAL[0000] android SDK not found`.
2. **`javac` must be on `PATH`.** Setting `JAVA_HOME` alone is not enough.
   The Go side compiles every ABI first and only then does `gomobile` need
   `javac` for the Java bindings — so the build dies ~15 minutes in with
   `exec: "javac": executable file not found in $PATH`. Hence the
   `/opt/jdk17/bin` prefix above.

Also note the script **overwrites** `libbox.aar` in place, and truncates it to
zero bytes when a step fails, so a failed run destroys the previous artifact.

### Trimming to the shipped ABIs

The build emits four ABIs (it includes x86 32-bit). Starveil ships three:
**armeabi-v7a, arm64-v8a, x86_64** — x86 is emulator-only and there is no
emulator here. Dropping it also matters for size: the four-ABI AAR is 112 MB,
which exceeds **GitHub's hard 100 MB per-file limit** and cannot be pushed.
Three ABIs is 80 MB and does push.

An AAR is a zip, so:

```sh
python3 - <<'PY'
import zipfile
zin = zipfile.ZipFile("/root/sing-box/libbox.aar")
zout = zipfile.ZipFile("core/kernel/libs/libbox.aar", "w", zipfile.ZIP_DEFLATED)
for item in zin.infolist():
    if not item.filename.startswith("jni/x86/"):
        zout.writestr(item, zin.read(item.filename))
zout.close()
PY
```

## Upgrading the core

Written from the v1.14.0 → v1.14.3 upgrade, which is the one to copy.

```sh
cd /root/sing-box
git fetch upstream tag vX.Y.Z              # upstream = SagerNet/sing-box
git checkout -b starveil-X.Y.x vX.Y.Z
git cherry-pick <our xhttp commits>        # usually clean
# then re-apply whatever upstream still has not taken, as new commits
```

**Do not rebase the old branch.** The old branch descends from a different
base, so a rebase drags that base's changes in silently. Start from the tag and
replay, which forces every carried change to be looked at and justified.

### Deciding what to carry

The method matters more than the answer, because the answer changes every
upgrade. For each candidate:

1. **Does Starveil emit a config key that depends on it?** If the app writes
   `support_x25519mlkem768` or an XHTTP `mode`, the core must understand it —
   grep the app for the key, not the fork for the patch.
2. **Is anything in the build actually reachable?** One patch looked essential
   until the constructor it hung off turned out never to be registered, which
   made the whole package dead code. Check for a caller before carrying
   anything.
3. **Is it already upstream?** Two of the four candidates from the old base
   had been fixed upstream between 1.14.0 and 1.14.3, and one of them
   (`protocol/group/selector.go`) had been a *regression* in the fork — upstream
   was strictly better.

From the v1.14.3 upgrade the answers were: carry XHTTP, REALITY ML-KEM and the
VLESS mux fix; drop the rest, including a flag that was declared and read but
never assigned anywhere in the tree.

### After the upgrade, check these three

```sh
# 1. the version the core will report — must be tag + short sha
git describe --tags                       # v1.14.3-4-g647c447 -> "1.14.3-647c447"

# 2. the patches really are in the binary
unzip -o -q libbox.aar -d /tmp/aar && strings /tmp/aar/jni/arm64-v8a/libbox.so \
  | grep -oE "1\.14\.[0-9]+-[0-9a-f]{7,8}"

# 3. no stale naming in the artifact
strings /tmp/aar/jni/arm64-v8a/libbox.so | grep -c mirage      # must be 0
```

Then: build one ABI, put it in `core/kernel/libs/`, and connect a
VLESS + REALITY + XHTTP node on a device. A core that builds is not a core that
carries traffic, and the patches above are exactly the parts no unit test
reaches.

### Packaging

The app uses ABI splits (`app/build.gradle.kts`), so a build emits one APK per
ABI rather than a single universal one — the three ABIs together are ~230 MB
of native code, which is not a sensible APK. CI uploads all of them under the
`starveil-debug-apk` artifact, and `tools/install-device.sh` defaults to the
arm64 split.
