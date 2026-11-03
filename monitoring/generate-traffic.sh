#!/usr/bin/env bash
set -euo pipefail

PRODUCER_URL="${PRODUCER_URL:-http://localhost:8081}"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-postgres_db}"
PAYMENTS="${PAYMENTS:-80}"
PAUSE_SECONDS="${PAUSE_SECONDS:-0.2}"
DEAD_LETTERS="${DEAD_LETTERS:-4}"

JSON="Content-Type: application/json"
ACCOUNTS=(910001 910002 910003 910004 910005 910006)
METHODS=(credit_card debit_card pix bank_transfer crypto)
CURRENCIES=(usd brl eur)

http_status() {
  curl -s -o /dev/null -w "%{http_code}" "$@"
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

echo "Creating demo accounts"
for account in "${ACCOUNTS[@]}"; do
  create_account "${account}"
done

echo "Sending ${PAYMENTS} payments"
for ((i = 1; i <= PAYMENTS; i++)); do
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
