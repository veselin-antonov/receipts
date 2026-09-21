#!/usr/bin/env bash
#
# STATUS: PARKED - nothing runs this automatically. Run it by hand when
# touching nginx config or the upload limit. It is wired into CI at M1.
#
# Why it cannot be wired yet: it reads receipts-api/application.yaml AND
# receipts-ui/nginx/nginx.conf.template, so it spans both repositories and
# neither one's CI can run it. The monorepo gives it a home. Separately, D8
# means neither repo runs any tests in CI at all, so wiring this before that
# is fixed would achieve nothing.
#
# Verify the upload limit end to end across the layers that enforce it.
#
# The limit lives in three places and the smallest wins:
#   1. nginx client_max_body_size   (UI container)
#   2. spring.servlet.multipart     (API config)
#   3. ReceiptScanService           (bound from 2, covered by unit tests)
#
# This script covers layer 1, which unit tests cannot reach, and checks that it
# agrees with layer 2. It needs no API, no database and no LLM call: nginx
# answers 413 when it rejects a body on size, and anything else means the body
# got through. A dead upstream is therefore a perfectly good upstream here.
#
# This is the test that would have caught nginx's 1 MB default, which no amount
# of config-sharing would have flagged, because the value was never written
# down at all.

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -d "$root/api" && -d "$root/ui" ]]; then
  API="$root/api"; UI="$root/ui"
else
  API="$(cd "$root/../receipts-api" && pwd)"; UI="$(cd "$root/../receipts-ui" && pwd)"
fi

# pick a free port rather than a fixed one, so the test cannot collide with
# whatever else is running on the machine
PORT="${PORT:-$(python3 -c "import socket;s=socket.socket();s.bind(('',0));print(s.getsockname()[1]);s.close()")}"
NAME="upload-limit-test-$$"
pass=0; fail=0
ok()   { printf '  \033[32m✓\033[0m %s\n' "$*"; pass=$((pass+1)); }
bad()  { printf '  \033[31m✗\033[0m %s\n' "$*"; fail=$((fail+1)); }
cleanup() { docker rm -f "$NAME" >/dev/null 2>&1 || true; rm -f /tmp/ul-*.bin; }
trap cleanup EXIT

# --- the configured limit, from the API ----------------------------------- #
LIMIT_MB="$(grep -oP 'max-file-size:\s*\$\{MAX_UPLOAD_MB:\K[0-9]+' \
  "$API/src/main/resources/application.yaml" || true)"
: "${LIMIT_MB:=25}"
echo "configured limit: ${LIMIT_MB} MB  (from application.yaml)"

# --- layer 1: render the template exactly as the container does ------------ #
echo
echo "1. nginx template"
if ! grep -q 'client_max_body_size' "$UI/nginx/nginx.conf.template"; then
  bad "client_max_body_size is absent — nginx will default to 1m and reject every photo"
else
  ok "client_max_body_size is set"
fi
# every ${VAR} in the template must be in the Dockerfile's envsubst whitelist,
# or it survives into the config verbatim and nginx refuses to start
missing=""
for v in $(grep -oP '\$\{\K[A-Z_]+(?=\})' "$UI/nginx/nginx.conf.template" | sort -u); do
  grep -q "\${$v}" "$UI/DOCKERFILE" || missing="$missing $v"
done
[[ -z "$missing" ]] && ok "every template variable is in the envsubst whitelist" \
                    || bad "not substituted, nginx would fail to start:$missing"

# --- boundary test against a real nginx ------------------------------------ #
echo
echo "2. live nginx boundary (no API needed)"
docker rm -f "$NAME" >/dev/null 2>&1 || true
docker run -d --name "$NAME" -p "$PORT:80" \
  -e BACKEND_HOST="127.0.0.1:9" -e MAX_UPLOAD_MB="$LIMIT_MB" \
  -v "$UI/nginx/nginx.conf.template:/etc/nginx/templates/default.conf.template:ro" \
  nginx:alpine sh -c "apk add --no-cache gettext >/dev/null 2>&1;
    envsubst '\${BACKEND_HOST} \${MAX_UPLOAD_MB}' \
      < /etc/nginx/templates/default.conf.template > /etc/nginx/conf.d/default.conf &&
    nginx -g 'daemon off;'" >/dev/null

for _ in $(seq 1 30); do
  curl -s -o /dev/null "http://localhost:$PORT/" 2>/dev/null && break; sleep 1
done

probe() {  # size_mb -> http status
  head -c "$(( $1 * 1024 * 1024 ))" /dev/zero > "/tmp/ul-$1.bin"
  curl -s -o /dev/null -w '%{http_code}' -X POST "http://localhost:$PORT/api/receipts/scan" \
       -F "file=@/tmp/ul-$1.bin;type=image/jpeg" 2>/dev/null
}

under=$(( LIMIT_MB - 1 )); over=$(( LIMIT_MB + 1 ))
s_under="$(probe "$under")"; s_over="$(probe "$over")"

[[ "$s_under" != "413" ]] && ok "${under} MB passes nginx (got $s_under; 502 means it reached the proxy)" \
                          || bad "${under} MB was rejected with 413 — the limit is lower than configured"
[[ "$s_over"  == "413" ]] && ok "${over} MB is rejected with 413, as intended" \
                          || bad "${over} MB was NOT rejected (got $s_over) — the limit is higher than configured"

echo
if [[ $fail -eq 0 ]]; then echo "all $pass checks passed"; else echo "$fail of $((pass+fail)) checks FAILED"; exit 1; fi
