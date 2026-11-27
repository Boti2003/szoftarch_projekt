#!/bin/bash
# =========================================================================
# RS256 JWT kulcspár generálása a secrets/ mappába (D12 / 8. szakasz).
# A privát kulcs CSAK az Identity service secretje; a többi a publikust kapja.
# A secrets/ nincs verziókezelve (.gitignore).
# =========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SECRETS_DIR="$SCRIPT_DIR/../../secrets"
mkdir -p "$SECRETS_DIR"

if [ -f "$SECRETS_DIR/jwt-private.pem" ]; then
    echo "JWT kulcsok már léteznek a secrets/ mappában. Kilépés."
    exit 0
fi

echo "RS256 kulcspár generálása..."
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
    -out "$SECRETS_DIR/jwt-private.pem"
chmod 644 "$SECRETS_DIR/jwt-private.pem"
openssl rsa -in "$SECRETS_DIR/jwt-private.pem" -pubout \
    -out "$SECRETS_DIR/jwt-public.pem"

echo "Kész:"
echo "  $SECRETS_DIR/jwt-private.pem  (csak Identity)"
echo "  $SECRETS_DIR/jwt-public.pem   (Gateway + minden service)"
