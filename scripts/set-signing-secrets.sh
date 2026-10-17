#!/usr/bin/env bash
# Push the release signing key to the repository's Actions secrets.
#
# The token needs permission to manage Actions secrets; the one in
# /root/smart-home/.env does not have it, which is why this is a script the
# owner runs rather than something CI can do for itself.
#
#   GH_TOKEN=<token with secrets:write> scripts/set-signing-secrets.sh
#
# Everything it reads lives outside the repository: the keystore, and the four
# values in the environment file.
set -euo pipefail

REPO="${REPO:-justnpcx/Starveil}"
ENV_FILE="${ENV_FILE:-/root/smart-home/.env}"

: "${GH_TOKEN:?set GH_TOKEN to a token that can manage Actions secrets}"

read_value() {
    grep -E "^$1=" "$ENV_FILE" | head -1 | cut -d= -f2-
}

KEYSTORE="$(read_value STARVEIL_KEYSTORE)"
STORE_PASSWORD="$(read_value STARVEIL_KEYSTORE_PASSWORD)"
KEY_ALIAS="$(read_value STARVEIL_KEY_ALIAS)"
KEY_PASSWORD="$(read_value STARVEIL_KEY_PASSWORD)"

for value in "$KEYSTORE" "$STORE_PASSWORD" "$KEY_ALIAS" "$KEY_PASSWORD"; do
    if [ -z "$value" ]; then
        echo "missing signing value in $ENV_FILE" >&2
        exit 1
    fi
done

base64 -w0 "$KEYSTORE" | gh secret set STARVEIL_KEYSTORE_BASE64 --repo "$REPO"
printf '%s' "$STORE_PASSWORD" | gh secret set STARVEIL_KEYSTORE_PASSWORD --repo "$REPO"
printf '%s' "$KEY_ALIAS" | gh secret set STARVEIL_KEY_ALIAS --repo "$REPO"
printf '%s' "$KEY_PASSWORD" | gh secret set STARVEIL_KEY_PASSWORD --repo "$REPO"

echo "signing secrets set on $REPO"
