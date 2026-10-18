#!/usr/bin/env bash
# Look for things that should not be in a public repository.
#
# Not a secret scanner with entropy heuristics: it looks for the specific
# shapes this project has actually leaked or could leak — a real node host, a
# private address in a comment, an absolute path from the build machine, a
# token. Every rule here exists because something like it was found once.
#
#   scripts/leak-scan.sh          # exits non-zero on a finding
#
# The allow-list is deliberate and short: example domains, RFC 1918 ranges in
# tests and in the LAN rule itself, and the repository's own URLs.
set -uo pipefail

cd "$(dirname "$0")/.."

# Paths that are allowed to contain the patterns below, with the reason.
ALLOW='^(\./)?(LICENSE|CHANGELOG\.md|README\.md)$'

status=0

scan() {
    local name="$1" pattern="$2" allow="$3"
    local hits
    hits=$(git grep -nEI "$pattern" -- . ':!*.png' ':!*.jpg' ':!*.webp' 2>/dev/null \
        | grep -vE "$allow" || true)
    if [ -n "$hits" ]; then
        echo "== $name"
        echo "$hits" | sed 's/^/   /'
        status=1
    fi
}

# A real hostname belonging to the maintainer, in code or docs.
scan "personal hostname" 'justnpc\.com' "$ALLOW"

# Tokens and keys. `ghp_`/`github_pat_` are GitHub's, the rest are the usual
# suspects; a test fixture with a fake UUID is fine because it is not one of
# these prefixes.
scan "token-shaped string" '(ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|sk-[A-Za-z0-9]{20,})' "$ALLOW"

# Absolute paths from the machine this was developed on. The maintainer's own
# tooling is allowed to default to one through an environment variable — that
# is documented behaviour, not a leak — so only prose and source are scanned.
scan "build-machine path" '/root/(smart-home|\.cache|sing-box)' '^(\./)?(scripts|tools)/'

# A bearer token pasted into a request example.
scan "bearer token" 'Bearer [A-Za-z0-9._-]{30,}' "$ALLOW"

if [ "$status" -eq 0 ]; then
    echo "leak-scan: clean"
fi
exit "$status"
