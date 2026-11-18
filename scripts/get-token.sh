#!/usr/bin/env bash
#
# Prints an access token (JWT) for a user of the payment platform, using the OAuth2 authorization code flow with
# PKCE against the authorization server. It does what a browser would do, without one: authorize, sign in on the
# login form, follow the redirect, and exchange the code for the token.
#
# Usage:   scripts/get-token.sh <username> <password> [issuer-url]
# Example: TOKEN=$(scripts/get-token.sh producer-client producer-client-demo)
#          curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/v1/accounts
#
# The demonstration users are seeded by the Flyway migrations (see payment-auth-server/README.md). The password is
# given as an argument, which other local users can see in the process list: use this script with the demonstration
# users only.
set -euo pipefail

if [ "$#" -lt 2 ] || [ "$#" -gt 3 ]; then
  echo "Usage: $0 <username> <password> [issuer-url]" >&2
  exit 2
fi

USERNAME="$1"
PASSWORD="$2"
ISSUER="${3:-${AUTH_ISSUER:-http://localhost:9000}}"
CLIENT_ID="payments-cli"
REDIRECT_URI="http://127.0.0.1:8085/callback"

COOKIES="$(mktemp)"
HEADERS="$(mktemp)"
trap 'rm -f "$COOKIES" "$HEADERS"' EXIT

fail() {
  echo "get-token: $1" >&2
  exit 1
}

# The Location header of the last request, without the trailing carriage return.
location() {
  grep -i '^location:' "$HEADERS" | tail -n 1 | cut -d' ' -f2- | tr -d '\r\n'
}

# PKCE: a random verifier and its S256 challenge.
VERIFIER="$(openssl rand -base64 48 | tr -d '=+/\n' | cut -c1-64)"
CHALLENGE="$(printf '%s' "$VERIFIER" | openssl dgst -sha256 -binary | openssl base64 -A | tr '+/' '-_' | tr -d '=')"

AUTHORIZE_URL="$ISSUER/oauth2/authorize?response_type=code&client_id=$CLIENT_ID&redirect_uri=$REDIRECT_URI&code_challenge=$CHALLENGE&code_challenge_method=S256"

# 1. Ask for authorization. Without a session the server sends the browser to the login page.
curl -s -o /dev/null -D "$HEADERS" -c "$COOKIES" -b "$COOKIES" -H 'Accept: text/html' "$AUTHORIZE_URL" \
  || fail "could not reach the authorization server at $ISSUER"
location | grep -q '/login' || fail "the authorization server did not ask for a login (is $CLIENT_ID registered?)"

# 2. Read the CSRF token of the login form and sign in.
CSRF="$(curl -s -c "$COOKIES" -b "$COOKIES" "$ISSUER/login" \
  | grep -o 'name="_csrf"[^>]*' | sed -n 's/.*value="\([^"]*\)".*/\1/p' | head -n 1)"
[ -n "$CSRF" ] || fail "could not find the CSRF token on the login page"

curl -s -o /dev/null -D "$HEADERS" -c "$COOKIES" -b "$COOKIES" "$ISSUER/login" \
  --data-urlencode "username=$USERNAME" --data-urlencode "password=$PASSWORD" --data-urlencode "_csrf=$CSRF"
SIGNED_IN_REDIRECT="$(location)"
case "$SIGNED_IN_REDIRECT" in
  *error*|'') fail "login failed: wrong username or password, or the user is disabled" ;;
esac

# 3. Follow the redirect back to the authorization endpoint; it answers with the code on the client's callback.
curl -s -o /dev/null -D "$HEADERS" -c "$COOKIES" -b "$COOKIES" "$SIGNED_IN_REDIRECT"
CODE="$(location | sed -n 's/.*[?&]code=\([^&]*\).*/\1/p')"
[ -n "$CODE" ] || fail "the authorization server did not return a code"

# 4. Exchange the code and the PKCE verifier for the access token.
RESPONSE="$(curl -s "$ISSUER/oauth2/token" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "client_id=$CLIENT_ID" \
  --data-urlencode "redirect_uri=$REDIRECT_URI" \
  --data-urlencode "code=$CODE" \
  --data-urlencode "code_verifier=$VERIFIER")"
TOKEN="$(printf '%s' "$RESPONSE" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')"
[ -n "$TOKEN" ] || fail "the token endpoint did not return an access token: $RESPONSE"

printf '%s\n' "$TOKEN"
