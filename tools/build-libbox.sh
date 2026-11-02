#!/usr/bin/env bash
#
# Build sing-box `libbox` AARs for Android from the Starveil fork
# (justnpcx/sing-box, starveil-1.14.4 — the tree at $SINGBOX_DIR).
#
# Reproducibility notes (learned the hard way — see docs/04-libbox-build.md):
#   * Build ONE ABI at a time. `gomobile bind` compiles every ABI in parallel
#     and each one compiles the whole tree in parallel; on this 4-core box the
#     all-ABI build reached a load average of 138 and took the machine down.
#     One ABI peaks at ~1 GB and a load of ~3.4.
#   * An interrupted build leaves build/<abi>/ behind and the next run fails
#     with "open .../go_libboxmain.go: file exists". `rm -rf build/` and retry.
#   * gomobile looks for `gobind` next to itself or on PATH. If $GOPATH/bin is
#     not on PATH you get: "gobind was not found. Please run gomobile init".
#   * The naive outbound pulls the prebuilt cronet-go static libs, whose
#     relocations need NDK r28's lld. On NDK 25 the link fails with
#     "unknown relocation (315) ... libcronet.a". Use NDK 28.2.13676358.
#
# Usage:
#   PLATFORM=android/arm64 tools/build-libbox.sh   # one ABI — the normal way
#   tools/build-libbox.sh                          # every ABI at once — read
#                                                  # the first note before this
#
set -euo pipefail

SINGBOX_DIR="${SINGBOX_DIR:-/root/sing-box}"
GO_BIN="${GO_BIN:-/opt/go/bin}"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/opt/android-sdk}"
NDK_VERSION="28.2.13676358"
GOMOBILE_VERSION="v0.1.13"
PLATFORM="${PLATFORM:-}"
readonly LIBBOX_TAGS="with_gvisor,with_quic,with_wireguard,with_utls,with_clash_api,badlinkname,tfogo_checklinkname0"
export LIBBOX_TAGS

NDK_DIR="$ANDROID_SDK_ROOT/ndk/$NDK_VERSION"

die() { echo "error: $*" >&2; exit 1; }

[ -d "$SINGBOX_DIR" ] || die "sing-box fork not found at $SINGBOX_DIR"
[ -x "$GO_BIN/go" ] || die "Go not found at $GO_BIN/go (need Go 1.25.5)"
[ -d "$NDK_DIR" ] || die "NDK $NDK_VERSION not found at $NDK_DIR"

# gomobile shells out to gobind, so both go and $GOPATH/bin must be on PATH.
export PATH="$GO_BIN:$("$GO_BIN/go" env GOPATH)/bin:$PATH"
export ANDROID_NDK_HOME="$NDK_DIR"
export ANDROID_NDK_ROOT="$NDK_DIR"

echo "go:        $("$GO_BIN/go" version)"
echo "ndk:       $NDK_DIR"
echo "gomobile:  $GOMOBILE_VERSION"
echo "tags:      $LIBBOX_TAGS"
echo "fork HEAD: $(git -C "$SINGBOX_DIR" rev-parse --short HEAD)"

echo "==> installing gomobile/gobind $GOMOBILE_VERSION"
go install -v "github.com/sagernet/gomobile/cmd/gomobile@$GOMOBILE_VERSION"
go install -v "github.com/sagernet/gomobile/cmd/gobind@$GOMOBILE_VERSION"

cd "$SINGBOX_DIR"
if [ -n "$PLATFORM" ]; then
  echo "==> building libbox (platform=$PLATFORM)"
  go run ./cmd/internal/build_libbox -target android -platform "$PLATFORM"
else
  echo "==> building libbox (all ABIs)"
  go run ./cmd/internal/build_libbox -target android
fi

echo "==> artifacts"
sha256sum libbox.aar libbox-legacy.aar
