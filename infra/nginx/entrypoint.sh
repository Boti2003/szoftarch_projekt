#!/bin/sh
# Self-signed tanúsítvány generálása induláskor, ha még nincs (3.2).
# A böngészőben egyszer el kell fogadni a bemutató/fejlesztői környezetben.
set -e

CERT_DIR=/etc/nginx/certs
mkdir -p "$CERT_DIR"

if [ ! -f "$CERT_DIR/dev-cert.pem" ]; then
    echo "Generating self-signed TLS certificate for localhost..."
    openssl req -x509 -nodes -newkey rsa:2048 \
        -keyout "$CERT_DIR/dev-key.pem" \
        -out "$CERT_DIR/dev-cert.pem" \
        -days 365 \
        -subj "/CN=localhost" \
        -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"
fi
