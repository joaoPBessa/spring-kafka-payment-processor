#!/usr/bin/env bash
set -euo pipefail

PRODUCER_URL="${PRODUCER_URL:-http://localhost:8081}"
AUTH_URL="${AUTH_URL:-http://localhost:9000}"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-postgres_db}"
VAULT_CONTAINER="${VAULT_CONTAINER:-payment-vault}"
VAULT_TOKEN="${VAULT_TOKEN:-root-token-local}"
TOKEN_CLIENT_ID="traffic-generator"
TOKEN_REFRESH_SECONDS=600
PAYMENTS="${PAYMENTS:-80}"
PAUSE_SECONDS="${PAUSE_SECONDS:-0.2}"
DEAD_LETTERS="${DEAD_LETTERS:-4}"

JSON="Content-Type: application/json"
ACCOUNTS=(910001 910002 910003 910004 910005 910006)
METHODS=(credit_card debit_card pix bank_transfer crypto)
CURRENCIES=(usd brl eur)

# The secret of the traffic-generator client lives in the Vault secret of the authorization server; it can also be
# given in TRAFFIC_GENERATOR_SECRET. It goes to curl through stdin, so it is not on a command line.
client_secret() {
  if [[ -n "${TRAFFIC_GENERATOR_SECRET:-}" ]]; then
    printf '%s' "${TRAFFIC_GENERATOR_SECRET}"
    return
  fi
  docker exec -e VAULT_ADDR=http://127.0.0.1:8200 -e VAULT_TOKEN="${VAULT_TOKEN}" "${VAULT_CONTAINER}" \
    vault kv get -field=app.auth.clients.traffic-generator.secret secret/payment-auth-server
}

# Client credentials: the producer needs a token with the roles of this client (accounts and payments).
refresh_token() {
  local response
  response="$(printf 'user = "%s:%s"\n' "${TOKEN_CLIENT_ID}" "$(client_secret)" |
    curl -s -K - -d grant_type=client_credentials "${AUTH_URL}/oauth2/token")" || true
  TOKEN="$(printf '%s' "${response}" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')"
  if [[ -z "${TOKEN}" ]]; then
    echo "Could not get a token from ${AUTH_URL}: is payment-auth-server running, and is its secret in Vault?" >&2
    exit 1
  fi
  TOKEN_FETCHED_AT="${SECONDS}"
}

refresh_token_when_old() {
  if ((SECONDS - TOKEN_FETCHED_AT > TOKEN_REFRESH_SECONDS)); then
    refresh_token
  fi
}

http_status() {
  curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer ${TOKEN}" "$@"
}

quiet() {
  local ignored
  ignored="$("$@")" || true
}

create_account() {
  quiet http_status -X POST "${PRODUCER_URL}/api/v1/accounts/" -H "${JSON}" \
    -d "{\"account_number\":\"$1\",\"account_name\":\"Demo account $1\"}"
}

post_payment() {
  local source="$1" target="$2" amount="$3" currency="$4" method="$5"
  http_status -X POST "${PRODUCER_URL}/api/v1/payments" -H "${JSON}" \
    -d "{\"source_account\":\"${source}\",\"target_account\":\"${target}\",\"amount\":${amount},\"currency\":\"${currency}\",\"payment_method\":\"${method}\"}"
}

random_amount() {
  awk -v seed="$RANDOM" 'BEGIN { srand(seed); printf "%.2f", 5 + rand() * 995 }'
}

pick() {
  local -n values=$1
  echo "${values[RANDOM % ${#values[@]}]}"
}

echo "Getting a token for the ${TOKEN_CLIENT_ID} client"
refresh_token

echo "Creating demo accounts"
for account in "${ACCOUNTS[@]}"; do
  create_account "${account}"
done

echo "Sending ${PAYMENTS} payments"
for ((i = 1; i <= PAYMENTS; i++)); do
  refresh_token_when_old
  source="$(pick ACCOUNTS)"
  target="$(pick ACCOUNTS)"
  while [[ "${target}" == "${source}" ]]; do target="$(pick ACCOUNTS)"; done
  quiet post_payment "${source}" "${target}" "$(random_amount)" "$(pick CURRENCIES)" "$(pick METHODS)"
  quiet http_status "${PRODUCER_URL}/api/v1/accounts/${source}"
  if ((i % 15 == 0)); then
    quiet post_payment "999999" "${target}" "10.00" "usd" "pix"
    quiet post_payment "${source}" "${target}" "-1" "usd" "pix"
  fi
  sleep "${PAUSE_SECONDS}"
done

echo "Forcing ${DEAD_LETTERS} events into the dead letter topic"
refresh_token_when_old
doomed="$(date +%s | tail -c 9)"
create_account "${doomed}"
quiet http_status "${PRODUCER_URL}/api/v1/accounts/${doomed}"
docker exec "${POSTGRES_CONTAINER}" psql -U postgres -d payments_db -q \
  -c "delete from payment.account where number = '${doomed}'"
for ((i = 1; i <= DEAD_LETTERS; i++)); do
  quiet post_payment "${ACCOUNTS[0]}" "${doomed}" "$(random_amount)" "usd" "pix"
  sleep "${PAUSE_SECONDS}"
done

echo "Done"
