#!/usr/bin/env bash
#
# Copyright 2026 DoorDash, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Generates an RSA identity-token signing key as a JWK, ready to drop into
# `authz.signing.keys`. Output is a single-line private JWK JSON (kty/kid/use/alg
# + n,e,d,p,q,dp,dq,qi) that NimbusSpinnakerTokenMinter signs with (RS256).
#
# Only the minter services (Gate + Front50) need the PRIVATE JWK. Every verifier
# fetches the public half from the minters' /auth/jwks endpoints, so you normally
# never deploy the public JWK by hand; --show-public is provided for inspection.
#
# Usage:
#   ./generate-signing-key.sh [--kid KID] [--bits N] [--pem FILE]
#                             [--show-public] [--keep-pem FILE] [--pretty]
#
#   --kid KID        Key id (defaults to spinnaker-<UTC timestamp>). Must be unique
#                    across the keys in authz.signing.keys (required for rotation).
#   --bits N         RSA key size (default 2048). 2048 or 4096 recommended.
#   --pem FILE       Convert an existing RSA private key PEM instead of generating one.
#   --show-public    Also print the public JWK and a JWKS document to stderr.
#   --keep-pem FILE  Write the generated PEM private key to FILE (mode 600).
#   --pretty         Pretty-print the private JWK instead of a single line.
#
# Examples:
#   # Generate and capture for an env var / secret:
#   SPINNAKER_SIGNING_KEY_NEW="$(./generate-signing-key.sh --kid spinnaker-2026-07)"
#
#   # Inspect the public half too:
#   ./generate-signing-key.sh --kid spinnaker-2026-07 --show-public >/dev/null
#
set -euo pipefail

KID=""
BITS=2048
PEM_IN=""
SHOW_PUBLIC=0
KEEP_PEM=""
PRETTY=0

die() { echo "error: $*" >&2; exit 1; }

usage() {
  cat <<'USAGE'
Generates an RSA identity-token signing key as a JWK for authz.signing.keys.
Prints a single-line private JWK (kty/kid/use/alg + n,e,d,p,q,dp,dq,qi) to stdout.

Usage:
  generate-signing-key.sh [--kid KID] [--bits N] [--pem FILE]
                          [--show-public] [--keep-pem FILE] [--pretty]

  --kid KID        Key id (default: spinnaker-<UTC timestamp>). Must be unique
                   across authz.signing.keys (required for rotation).
  --bits N         RSA key size (default 2048; 2048 or 4096 recommended).
  --pem FILE       Convert an existing RSA private-key PEM instead of generating.
  --show-public    Also print the public JWK + JWKS document to stderr.
  --keep-pem FILE  Write the generated PEM private key to FILE (mode 600).
  --pretty         Pretty-print the private JWK instead of a single line.
  -h, --help       Show this help.

Example:
  SPINNAKER_SIGNING_KEY_NEW="$(generate-signing-key.sh --kid spinnaker-2026-07)"
USAGE
  exit "${1:-0}"
}

while [ $# -gt 0 ]; do
  case "$1" in
    --kid) KID="${2:-}"; shift 2 ;;
    --bits) BITS="${2:-}"; shift 2 ;;
    --pem) PEM_IN="${2:-}"; shift 2 ;;
    --show-public) SHOW_PUBLIC=1; shift ;;
    --keep-pem) KEEP_PEM="${2:-}"; shift 2 ;;
    --pretty) PRETTY=1; shift ;;
    -h|--help) usage 0 ;;
    *) die "unknown argument: $1 (try --help)" ;;
  esac
done

command -v openssl >/dev/null 2>&1 || die "openssl is required but not on PATH"
command -v xxd >/dev/null 2>&1 || die "xxd is required but not on PATH"

if [ -z "$KID" ]; then
  KID="spinnaker-$(date -u +%Y%m%d%H%M%S)"
fi

# Working dir for transient key material; cleaned up on exit.
WORKDIR="$(mktemp -d "${TMPDIR:-/tmp}/spin-signing-key.XXXXXX")"
trap 'rm -rf "$WORKDIR"' EXIT
PEM="$WORKDIR/key.pem"
TXT="$WORKDIR/key.txt"

if [ -n "$PEM_IN" ]; then
  [ -f "$PEM_IN" ] || die "--pem file not found: $PEM_IN"
  cp "$PEM_IN" "$PEM"
else
  ( umask 077; openssl genrsa -out "$PEM" "$BITS" >/dev/null 2>&1 ) \
    || die "failed to generate $BITS-bit RSA key"
fi

# Dump the key components in OpenSSL's text form, then parse each field.
openssl rsa -in "$PEM" -text -noout > "$TXT" 2>/dev/null \
  || die "failed to read RSA key (is it a valid RSA private key PEM?)"

# Convert a contiguous hex string to base64url, stripping OpenSSL's sign-padding
# leading zero byte(s) so the JWK carries the minimal big-endian unsigned integer.
hex_to_b64url() {
  local hex="$1"
  while [ "${hex:0:2}" = "00" ] && [ "${#hex}" -gt 2 ]; do hex="${hex:2}"; done
  # Ensure even length for xxd.
  if [ $(( ${#hex} % 2 )) -ne 0 ]; then hex="0$hex"; fi
  printf '%s' "$hex" | xxd -r -p | openssl base64 -A | tr '+/' '-_' | tr -d '='
}

# Extract a multi-line indented hex block that follows a "<label>:" line.
extract_block() {
  awk -v label="$1:" '
    $0 == label { capture = 1; next }
    capture == 1 {
      if ($0 ~ /^[[:space:]]/) { gsub(/[[:space:]:]/, "", $0); printf "%s", $0 }
      else { exit }
    }
  ' "$TXT"
}

N_HEX="$(extract_block modulus)"
D_HEX="$(extract_block privateExponent)"
P_HEX="$(extract_block prime1)"
Q_HEX="$(extract_block prime2)"
DP_HEX="$(extract_block exponent1)"
DQ_HEX="$(extract_block exponent2)"
QI_HEX="$(extract_block coefficient)"

# publicExponent is inline, e.g. "publicExponent: 65537 (0x10001)".
E_HEX="$(awk '/^publicExponent:/ { if (match($0, /0x[0-9a-fA-F]+/)) print substr($0, RSTART + 2, RLENGTH - 2) }' "$TXT")"
[ -n "$E_HEX" ] || die "could not parse public exponent"
if [ $(( ${#E_HEX} % 2 )) -ne 0 ]; then E_HEX="0$E_HEX"; fi

[ -n "$N_HEX" ] && [ -n "$D_HEX" ] && [ -n "$P_HEX" ] || die "could not parse RSA key components"

N="$(hex_to_b64url "$N_HEX")"
E="$(hex_to_b64url "$E_HEX")"
D="$(hex_to_b64url "$D_HEX")"
P="$(hex_to_b64url "$P_HEX")"
Q="$(hex_to_b64url "$Q_HEX")"
DP="$(hex_to_b64url "$DP_HEX")"
DQ="$(hex_to_b64url "$DQ_HEX")"
QI="$(hex_to_b64url "$QI_HEX")"

private_jwk_pretty() {
  cat <<EOF
{
  "kty": "RSA",
  "kid": "$KID",
  "use": "sig",
  "alg": "RS256",
  "n": "$N",
  "e": "$E",
  "d": "$D",
  "p": "$P",
  "q": "$Q",
  "dp": "$DP",
  "dq": "$DQ",
  "qi": "$QI"
}
EOF
}

private_jwk_oneline() {
  printf '{"kty":"RSA","kid":"%s","use":"sig","alg":"RS256","n":"%s","e":"%s","d":"%s","p":"%s","q":"%s","dp":"%s","dq":"%s","qi":"%s"}\n' \
    "$KID" "$N" "$E" "$D" "$P" "$Q" "$DP" "$DQ" "$QI"
}

if [ -n "$KEEP_PEM" ]; then
  ( umask 077; cp "$PEM" "$KEEP_PEM" )
  echo "wrote PEM private key to $KEEP_PEM (mode 600)" >&2
fi

if [ "$SHOW_PUBLIC" -eq 1 ]; then
  PUBLIC_JWK="$(printf '{"kty":"RSA","kid":"%s","use":"sig","alg":"RS256","n":"%s","e":"%s"}' "$KID" "$N" "$E")"
  {
    echo "# public JWK (published automatically by minters at /auth/jwks):"
    echo "$PUBLIC_JWK"
    echo "# JWKS document:"
    printf '{"keys":[%s]}\n' "$PUBLIC_JWK"
  } >&2
fi

if [ "$PRETTY" -eq 1 ]; then
  private_jwk_pretty
else
  private_jwk_oneline
fi
