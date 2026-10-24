#!/usr/bin/env bash
# End-to-end smoke: build the api and ui images from this checkout, start them
# with MongoDB, MailHog and the OpenAI stub, run Playwright, tear it all down.
# CI runs exactly this (.github/workflows/e2e.yml).
#
#   e2e/run.sh                                   # ports 7830 and 7835
#   E2E_UI_PORT=18780 E2E_MAILHOG_PORT=18725 e2e/run.sh   # other ports
#
# Each run is a compose project of its own (receipts-e2e-<run>), with its own
# keys and results under certs/<run> and test-results/<run>, so two runs side by
# side do not tear each other down; give the second one other ports.
#   E2E_KEEP=1 e2e/run.sh                        # leave the stack up afterwards
#
# Needs Docker, Java 25 and Node 22. First run: (cd e2e && npm ci &&
# npx playwright install chromium).
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(dirname "$here")"

export VERSION="$(tr -d '[:space:]' < "$root/VERSION")"
export E2E_UI_PORT="${E2E_UI_PORT:-7830}"
export E2E_MAILHOG_PORT="${E2E_MAILHOG_PORT:-7835}"
export E2E_RUN="${E2E_RUN:-$(date +%s)-$$}"
results="$here/test-results/$E2E_RUN"

dc() { docker compose -p "receipts-e2e-$E2E_RUN" -f "$here/compose.yaml" "$@"; }

# A throwaway JWT key pair per run, so no key is ever committed or reused.
certs="$here/certs/$E2E_RUN"
mkdir -p "$certs" "$results"
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
  -out "$certs/private.pem" 2>/dev/null
openssl rsa -in "$certs/private.pem" -pubout -out "$certs/public.pem" 2>/dev/null
chmod 644 "$certs/"*.pem

# The api image copies a prebuilt jar. Tests run in api-checks, not here.
(cd "$root/api" && ./gradlew bootJar --console=plain -q)

finish() {
  local status=$?
  if [[ $status -ne 0 ]]; then
    dc logs --no-color > "$results/compose.log" 2>&1 || true
    echo "stack logs: e2e/test-results/$E2E_RUN/compose.log" >&2
  fi
  if [[ -z "${E2E_KEEP:-}" ]]; then
    dc down -v --remove-orphans > /dev/null 2>&1 || true
    rm -rf "$certs"
  else
    echo "left up as compose project receipts-e2e-$E2E_RUN" >&2
  fi
  exit "$status"
}
trap finish EXIT

dc up -d --build --wait --wait-timeout 300
(cd "$here" && npx playwright test)
