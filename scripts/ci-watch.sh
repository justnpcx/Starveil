#!/usr/bin/env bash
#
# Watch Starveil's CI and print one line per newly-failed run.
#
# Silent while everything is green: this is meant to sit behind a monitor that
# only wakes someone when there is something to report, so a heartbeat would
# defeat the point. Every completed run that did not succeed gets exactly one
# line, once — the ids already reported are kept in a state file, so a restart
# does not re-alert for an old failure.
#
# The token is read from the shared env file and never echoed.
#
# Env overrides: CI_WATCH_REPO, CI_WATCH_INTERVAL, CI_WATCH_STATE_DIR,
# CI_WATCH_ENV.
set -uo pipefail

REPO="${CI_WATCH_REPO:-justnpcx/Starveil}"
INTERVAL="${CI_WATCH_INTERVAL:-60}"
STATE_DIR="${CI_WATCH_STATE_DIR:-/root/.cache/starveil-ci-watch}"
ENV_FILE="${CI_WATCH_ENV:-/root/smart-home/.env}"
STATE="$STATE_DIR/alerted-runs"

mkdir -p "$STATE_DIR"

GH_TOKEN="$(grep -E '^GH_TOKEN=' "$ENV_FILE" 2>/dev/null | head -1 | cut -d= -f2- | tr -d '\r"'"'"'')"
if [ -z "${GH_TOKEN:-}" ]; then
    echo "ci-watch: no GH_TOKEN found in $ENV_FILE" >&2
    exit 1
fi

# The run list arrives on stdin; one alert line is printed per new failure.
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

# First run: record what is already there and stay quiet, so the monitor does
# not open with a wall of historical failures.
seeding = not os.path.exists(state_path)

alerted = set()
if not seeding:
    with open(state_path) as fh:
        alerted = {line.strip() for line in fh if line.strip()}

new_alerts = []
# Only real failures wake someone. Cancelled is usually a superseded push, and
# neutral/stale/skipped are not failures either — so this is an explicit list
# of bad conclusions rather than "anything that is not success".
BAD = {"failure", "timed_out", "startup_failure", "action_required"}
for run in sorted(runs, key=lambda r: r["id"]):
    if run["status"] != "completed":
        continue
    if run["conclusion"] not in BAD:
        continue
    run_id = str(run["id"])
    if run_id in alerted:
        continue
    alerted.add(run_id)
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
    for run_id in sorted(alerted, key=int)[-200:]:
        fh.write(run_id + "\n")

for line in new_alerts:
    print(line, flush=True)
' "$STATE"
}

while true; do
    # A transient network or API failure is not worth waking anyone for.
    curl -sS --max-time 30 \
        -H "Authorization: Bearer $GH_TOKEN" \
        -H "Accept: application/vnd.github+json" \
        "https://api.github.com/repos/$REPO/actions/runs?per_page=20" 2>/dev/null \
        | read_runs
    sleep "$INTERVAL"
done
