#!/usr/bin/env bash
#
# Tests for ci-watch.sh.
#
# Everything interesting about this script is what it does NOT say: a failed
# run is reported once and then never again, a blind spell is reported once
# and then never again, and both re-arm after a healthy poll. That state lives
# in files precisely so it survives process exit, so these tests drive real
# `--once` invocations — each one a fresh process, exactly as cron runs it —
# rather than sourcing the script and poking at variables.
#
# No network and no credentials: a curl shim on PATH serves canned responses
# and captures ntfy publishes instead of sending them.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WATCH="$SCRIPT_DIR/ci-watch.sh"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

BIN="$WORK/bin"
STATE="$WORK/state"
NTFY="$WORK/ntfy.json"
TOKEN_FILE="$WORK/env"
mkdir -p "$BIN"
printf 'GH_TOKEN=not-a-real-token\n' > "$TOKEN_FILE"

# API calls replay the canned body plus a status line; ntfy publishes are
# recorded rather than sent, so a test run can never notify anyone.
#
# FAKE_BODY is the response *content*, so it is printed, not `cat`ed — passing
# JSON to cat makes it a filename, which silently yields an empty body and
# quietly skips every case that has to parse one.
cat > "$BIN/curl" <<'SHIM'
#!/usr/bin/env bash
if printf '%s\n' "$@" | grep -q "127.0.0.1:2586"; then
    prev=""
    for arg in "$@"; do
        [ "$prev" = "-d" ] && printf '%s\n' "$arg" >> "$FAKE_NTFY"
        prev="$arg"
    done
    exit 0
fi
printf '%s' "$FAKE_BODY"
printf '\n%s %s %s' "$FAKE_CODE" "$FAKE_RL" "$FAKE_RETRY"
SHIM
chmod +x "$BIN/curl"

BODY_OK='{"workflow_runs":[]}'
BODY_FAIL='{"workflow_runs":[{"id":7001,"status":"completed","conclusion":"failure","head_branch":"main","head_sha":"aaaa1112223","html_url":"https://example.invalid/7001"}]}'
BODY_ERR='{"message":"nope"}'

PASS=0
FAIL=0
ok() { PASS=$((PASS + 1)); printf '  ok    %s\n' "$1"; }
bad() { FAIL=$((FAIL + 1)); printf '  FAIL  %s\n' "$1"; }

# assert <description> <actual> <expected>
assert() {
    if [ "$2" = "$3" ]; then ok "$1"; else bad "$1 — expected '$3', got '$2'"; fi
}
yes_no() { [ -n "$1" ] && echo yes || echo no; }
flag() { [ -f "$STATE/blind" ] && echo set || echo absent; }
notified() { [ -f "$NTFY" ] && echo yes || echo no; }

# A state dir whose reported-runs file already exists, so the run is not a
# silent first-run seed.
reset_state() {
    rm -rf "$STATE"
    mkdir -p "$STATE"
    : > "$STATE/reported-runs"
}

# poll <body> <http-code> [ratelimit-remaining] [retry-after]
# Leaves stdout in $OUT.
poll() {
    rm -f "$NTFY"
    OUT="$(PATH="$BIN:$PATH" \
        FAKE_BODY="$1" FAKE_CODE="$2" FAKE_RL="${3:-}" FAKE_RETRY="${4:-}" FAKE_NTFY="$NTFY" \
        CI_WATCH_ENV="$TOKEN_FILE" CI_WATCH_STATE_DIR="$STATE" CI_WATCH_NOTIFY=1 \
        bash "$WATCH" --once 2>&1)"
}

printf '\nblind flag is reported once per outage\n'
reset_state
poll "$BODY_ERR" 401
assert "first 401 reports"        "$(yes_no "$OUT")" yes
assert "first 401 sets the flag"  "$(flag)"          set
assert "first 401 notifies"       "$(notified)"      yes

poll "$BODY_ERR" 401
assert "second 401 is silent"               "$OUT"       ""
assert "second 401 does not notify again"   "$(notified)" no
assert "flag is still set"                  "$(flag)"     set

poll "$BODY_ERR" 401
assert "third 401 is still silent" "$OUT" ""

printf '\nblind flag clears on a healthy poll and re-arms\n'
poll "$BODY_OK" 200 4999
assert "healthy poll is silent"    "$OUT"       ""
assert "healthy poll clears flag"  "$(flag)"    absent
assert "healthy poll does not notify" "$(notified)" no

poll "$BODY_ERR" 401
assert "a later 401 reports again"  "$(yes_no "$OUT")" yes
assert "and sets the flag again"    "$(flag)"          set

printf '\nblind flag is not cleared by a poll that is still blind\n'
reset_state
: > "$STATE/blind"
poll "$BODY_ERR" 404
assert "404 leaves the flag set" "$(flag)" set

reset_state
poll "$BODY_ERR" 404
assert "404 with no flag reports" "$(yes_no "$OUT")" yes
assert "404 sets the flag"        "$(flag)"          set

printf '\nrate limiting is not treated as blind\n'
reset_state
poll "$BODY_ERR" 403 0
assert "403 with remaining=0 is silent" "$OUT"    ""
assert "403 with remaining=0 sets no flag" "$(flag)" absent

poll "$BODY_ERR" 403 42 60
assert "403 with retry-after is silent" "$OUT"    ""
assert "403 with retry-after sets no flag" "$(flag)" absent

poll "$BODY_ERR" 403 4999
assert "403 with no rate signal reports" "$(yes_no "$OUT")" yes

printf '\nfailed run ids are reported once\n'
reset_state
poll "$BODY_FAIL" 200 4999
assert "a failure is reported"      "$(yes_no "$OUT")" yes
assert "and notified"               "$(notified)"      yes

poll "$BODY_FAIL" 200 4999
assert "the same failure is silent on replay" "$OUT"       ""
assert "and does not notify again"            "$(notified)" no

printf '\nthe first run seeds instead of replaying history\n'
rm -rf "$STATE"
mkdir -p "$STATE"
poll "$BODY_FAIL" 200 4999
assert "seeding run is silent"    "$OUT"                  ""
assert "seeding records the id"   "$(cat "$STATE/reported-runs")" "7001"

printf '\ntransient errors stay quiet\n'
reset_state
poll "$BODY_ERR" 500
assert "500 is silent"    "$OUT"       ""
assert "500 sets no flag" "$(flag)"    absent

printf '\n%d passed, %d failed\n\n' "$PASS" "$FAIL"
[ "$FAIL" -eq 0 ]
