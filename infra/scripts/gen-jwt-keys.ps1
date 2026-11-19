# =========================================================================
# RS256 JWT kulcspár generálása a secrets/ mappába (D12 / 8. szakasz) – Windows.
# openssl szükséges (pl. Git for Windows hozza). A secrets/ nincs verziókezelve.
# =========================================================================
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$secretsDir = Join-Path $scriptDir "..\..\secrets"
New-Item -ItemType Directory -Force -Path $secretsDir | Out-Null

$privateKey = Join-Path $secretsDir "jwt-private.pem"
$publicKey = Join-Path $secretsDir "jwt-public.pem"

if (Test-Path $privateKey) {
    Write-Output "JWT kulcsok mar leteznek a secrets/ mappaban. Kilepes."
    exit 0
}

Write-Output "RS256 kulcspar generalasa..."
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out $privateKey
openssl rsa -in $privateKey -pubout -out $publicKey

Write-Output "Kesz:"
Write-Output "  $privateKey  (csak Identity)"
Write-Output "  $publicKey   (Gateway + minden service)"
