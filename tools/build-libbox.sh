#!/usr/bin/env bash
#
# Build sing-box `libbox` AARs for Android from the Mirage fork
# (justnpcx/sing-box, nb-next-1.14.x — the tree at $SINGBOX_DIR).
#
# Reproducibility notes (learned the hard way — see docs/04-libbox-build.md):
#   * gomobile looks for `gobind` next to itself or on PATH. If $GOPATH/bin is
#     not on PATH you get: "gobind was not found. Please run gomobile init".
#   * The naive outbound pulls the prebuilt cronet-go static libs, whose
#     relocations need NDK r28's lld. On NDK 25 the link fails with
#     "unknown relocation (315) ... libcronet.a". Use NDK 28.2.13676358.
#
# Usage:
#   tools/build-libbox.sh                 # all ABIs (slow)
#   PLATFORM=android/arm64 tools/build-libbox.sh   # one ABI (fast, for checks)
#
set -euo pipefail

SINGBOX_DIR="${SINGBOX_DIR:-/root/sing-box}"
GO_BIN="${GO_BIN:-/opt/go/bin}"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/opt/android-sdk}"
NDK_VERSION="28.2.13676358"
GOMOBILE_VERSION="v0.1.13"
PLATFORM="${PLATFORM:-}"

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
