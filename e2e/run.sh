#!/usr/bin/env bash
# End-to-end smoke: build the api and ui images from this checkout, start them
# with MongoDB, MailHog and the OpenAI stub, run Playwright, tear it all down.
# CI runs exactly this (.github/workflows/e2e.yml).
#
#   e2e/run.sh                                   # ports 8080 and 8025
#   E2E_UI_PORT=18780 E2E_MAILHOG_PORT=18725 e2e/run.sh   # next to the dev stack
#   E2E_KEEP=1 e2e/run.sh                        # leave the stack up afterwards
#
# Needs Docker, Java 25 and Node 22. First run: (cd e2e && npm ci &&
# npx playwright install chromium).
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(dirname "$here")"

export VERSION="$(tr -d '[:space:]' < "$root/VERSION")"
export E2E_UI_PORT="${E2E_UI_PORT:-8080}"
export E2E_MAILHOG_PORT="${E2E_MAILHOG_PORT:-8025}"

dc() { docker compose -f "$here/compose.yaml" "$@"; }

# A throwaway JWT key pair per run, so no key is ever committed or reused.
mkdir -p "$here/certs" "$here/test-results"
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
  -out "$here/certs/private.pem" 2>/dev/null
openssl rsa -in "$here/certs/private.pem" -pubout -out "$here/certs/public.pem" 2>/dev/null
chmod 644 "$here/certs/"*.pem

# The api image copies a prebuilt jar. Tests run in api-checks, not here.
(cd "$root/api" && ./gradlew bootJar --console=plain -q)

finish() {
  local status=$?
  if [[ $status -ne 0 ]]; then
    dc logs --no-color > "$here/test-results/compose.log" 2>&1 || true
    echo "stack logs: e2e/test-results/compose.log" >&2
  fi
  if [[ -z "${E2E_KEEP:-}" ]]; then
    dc down -v --remove-orphans > /dev/null 2>&1 || true
  fi
  exit "$status"
}
trap finish EXIT

dc up -d --build --wait --wait-timeout 300
(cd "$here" && npx playwright test)
