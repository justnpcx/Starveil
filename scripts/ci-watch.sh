#!/usr/bin/env bash
#
# Watch Starveil's CI and report newly-failed runs.
#
# Silent while everything is green: this is meant to sit behind a monitor or a
# cron entry that only speaks up when there is something to say, so a heartbeat
# would defeat the point. Every failed run is reported exactly once — the ids
# already reported are kept in a state file, so a restart does not re-report an
# old failure.
#
# Usage:
#   ci-watch.sh          poll forever (for a long-running monitor)
#   ci-watch.sh --once   poll once and exit (for cron)
#
# Env:
#   CI_WATCH_REPO        default justnpcx/Starveil
#   CI_WATCH_INTERVAL    seconds between polls in loop mode, default 60
#   CI_WATCH_STATE_DIR   where the reported-run ids live
#   CI_WATCH_ENV         file holding GH_TOKEN, default /root/smart-home/.env
#   CI_WATCH_NOTIFY=1    also push each alert to ntfy (cron wants this; an
#                        in-session monitor already wakes someone)
#   CI_WATCH_NTFY_URL    default http://127.0.0.1:2586
#   CI_WATCH_NTFY_TOPIC  default homelab
#
# The token is read from the env file and never echoed.
set -uo pipefail

REPO="${CI_WATCH_REPO:-justnpcx/Starveil}"
INTERVAL="${CI_WATCH_INTERVAL:-60}"
STATE_DIR="${CI_WATCH_STATE_DIR:-/root/.cache/starveil-ci-watch}"
ENV_FILE="${CI_WATCH_ENV:-/root/smart-home/.env}"
STATE="$STATE_DIR/reported-runs"
NOTIFY="${CI_WATCH_NOTIFY:-0}"
NTFY_URL="${CI_WATCH_NTFY_URL:-http://127.0.0.1:2586}"
NTFY_TOPIC="${CI_WATCH_NTFY_TOPIC:-homelab}"

ONCE=0
[ "${1:-}" = "--once" ] && ONCE=1

mkdir -p "$STATE_DIR"

GH_TOKEN="$(grep -E '^GH_TOKEN=' "$ENV_FILE" 2>/dev/null | head -1 | cut -d= -f2- | tr -d '\r"'"'"'')"
if [ -z "${GH_TOKEN:-}" ]; then
    echo "ci-watch: no GH_TOKEN found in $ENV_FILE" >&2
    exit 1
fi

# The run list arrives on stdin; one line is printed per newly-failed run.
#
# Written as `-c` rather than a heredoc on purpose: a heredoc would take over
# stdin and the piped JSON would never reach json.load.
read_runs() {
    python3 -c '
import json, os, sys

state_path = sys.argv[1]
try:
    runs = json.load(sys.stdin)["workflow_runs"]
except Exception:
    sys.exit(0)

# First run: record what is already there and stay quiet, so this does not
# open with a wall of historical failures.
seeding = not os.path.exists(state_path)

reported = set()
if not seeding:
    with open(state_path) as fh:
        reported = {line.strip() for line in fh if line.strip()}

new_alerts = []
# Only real failures speak up. Cancelled is usually a superseded push on this
# repo, and neutral/stale/skipped are not failures either — so this is an
# explicit list of bad conclusions rather than "anything that is not success".
BAD = {"failure", "timed_out", "startup_failure", "action_required"}
for run in sorted(runs, key=lambda r: r["id"]):
    if run["status"] != "completed":
        continue
    if run["conclusion"] not in BAD:
        continue
    run_id = str(run["id"])
    if run_id in reported:
        continue
    reported.add(run_id)
    if not seeding:
        new_alerts.append(
            "CI {conclusion}: {branch}@{sha}  {url}".format(
                conclusion=run["conclusion"],
                branch=run["head_branch"],
                sha=run["head_sha"][:7],
                url=run["html_url"],
            )
        )

# Bounded: only the most recent ids need remembering.
with open(state_path, "w") as fh:
    for run_id in sorted(reported, key=int)[-200:]:
        fh.write(run_id + "\n")

for line in new_alerts:
    print(line, flush=True)
' "$STATE"
}

# ntfy is how this homelab reports things (same as ipan-watch.py). JSON publish
# so the body is not limited to latin-1.
notify_ntfy() {
    local payload
    payload="$(python3 -c '
import json, sys
print(json.dumps({
    "topic": sys.argv[1],
    "title": "Starveil CI failure",
    "message": sys.argv[2],
    "priority": 4,
    "tags": ["rotating_light"],
}))
' "$NTFY_TOPIC" "$1")"
    if ! curl -sS --max-time 15 -H "Content-Type: application/json" \
        -d "$payload" "$NTFY_URL/" >/dev/null 2>&1; then
        echo "ci-watch: ntfy publish failed" >&2
    fi
}

poll_once() {
    local alerts line stamped
    alerts="$(curl -sS --max-time 30 \
        -H "Authorization: Bearer $GH_TOKEN" \
        -H "Accept: application/vnd.github+json" \
        "https://api.github.com/repos/$REPO/actions/runs?per_page=20" 2>/dev/null \
        | read_runs)"

    [ -z "$alerts" ] && return 0

    while IFS= read -r line; do
        [ -z "$line" ] && continue
        stamped="[$(date '+%F %T')] $line"
        echo "$stamped"
        [ "$NOTIFY" = "1" ] && notify_ntfy "$stamped"
    done <<< "$alerts"
}

if [ "$ONCE" = "1" ]; then
    poll_once
    exit 0
fi

while true; do
    # A transient network or API failure is not worth waking anyone for.
    poll_once
    sleep "$INTERVAL"
done
