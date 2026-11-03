#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

GRAFANA_URL="${GRAFANA_URL:-http://localhost:3000}"
ZIPKIN_URL="${ZIPKIN_URL:-http://localhost:9411}"
OUTPUT_DIR="${OUTPUT_DIR:-docs/images/observability}"
WINDOW="${WINDOW:-now-15m}"
WIDTH="${WIDTH:-1600}"
RENDER_BUDGET_MS="${RENDER_BUDGET_MS:-25000}"

DASHBOARDS=("payments-overview:2400" "jvm-app-health:1500" "database:1450")

find_browser() {
  if [[ -n "${BROWSER:-}" ]]; then
    echo "${BROWSER}"
    return
  fi
  local candidate
  for candidate in \
    "/c/Program Files/Google/Chrome/Application/chrome.exe" \
    "/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe" \
    "$(command -v google-chrome 2>/dev/null || true)" \
    "$(command -v chromium 2>/dev/null || true)" \
    "$(command -v chromium-browser 2>/dev/null || true)"; do
    if [[ -n "${candidate}" && -x "${candidate}" ]]; then
      echo "${candidate}"
      return
    fi
  done
  echo "No Chrome, Chromium or Edge found; set BROWSER" >&2
  exit 1
}

absolute_path() {
  (cd "$(dirname "$1")" && echo "$(pwd -W 2>/dev/null || pwd)/$(basename "$1")")
}

capture() {
  local url="$1" file="$2" height="$3"
  "${BROWSER_BIN}" --headless=new --disable-gpu --hide-scrollbars \
    --window-size="${WIDTH},${height}" \
    --virtual-time-budget="${RENDER_BUDGET_MS}" \
    --screenshot="$(absolute_path "${file}")" "${url}" > /dev/null 2>&1
  echo "Saved ${file}"
}

BROWSER_BIN="$(find_browser)"
mkdir -p "${OUTPUT_DIR}"

for entry in "${DASHBOARDS[@]}"; do
  uid="${entry%%:*}"
  height="${entry##*:}"
  capture "${GRAFANA_URL}/d/${uid}?orgId=1&from=${WINDOW}&to=now&kiosk" "${OUTPUT_DIR}/${uid}.png" "${height}"
done

trace_id="$(curl -s "${ZIPKIN_URL}/api/v2/traces?serviceName=payment-consumer-api&limit=1" \
  | grep -o '"traceId":"[0-9a-f]*"' | head -1 | cut -d'"' -f4 || true)"
if [[ -n "${trace_id}" ]]; then
  capture "${ZIPKIN_URL}/zipkin/traces/${trace_id}" "${OUTPUT_DIR}/zipkin-trace.png" 900
else
  echo "No trace found in Zipkin; skipping zipkin-trace.png" >&2
fi
