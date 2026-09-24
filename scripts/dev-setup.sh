#!/usr/bin/env bash
# Prepare a local development environment for Receipts.
#
# Idempotent: safe to re-run. Never overwrites a value you have already set,
# and never touches anything tracked by git except gradlew's executable bit.
#
# What it cannot do is install system packages or invent secrets. Those are
# reported at the end as manual steps.

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
API="$root/api"; UI="$root/ui"

MANUAL=()
ok()   { printf '  \033[32m✓\033[0m %s\n' "$*"; }
skip() { printf '  \033[90m·\033[0m %s\n' "$*"; }
warn() { printf '  \033[33m!\033[0m %s\n' "$*"; }

echo "api: $API"
echo "ui:  $UI"

# --- 1. prerequisites ------------------------------------------------------ #
echo
echo "1. Checking prerequisites"

java_ok=false
if command -v java >/dev/null 2>&1; then
  v="$(java -version 2>&1 | head -1 | grep -oE '[0-9]+' | head -1)"
  if [[ "${v:-0}" -ge 25 ]]; then ok "java $v"; java_ok=true
  else warn "java $v found, but the Gradle toolchain needs 25"; fi
else
  warn "java not installed"
fi
$java_ok || MANUAL+=("sudo apt install openjdk-25-jdk   # Gradle toolchain targets Java 25")

if command -v node >/dev/null 2>&1; then ok "node $(node --version)"
else MANUAL+=("install Node 20 or newer"); warn "node not installed"; fi

if command -v docker >/dev/null 2>&1; then ok "docker $(docker --version | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1)"
else MANUAL+=("install Docker, for MongoDB and MailHog"); warn "docker not installed"; fi

command -v openssl >/dev/null 2>&1 && ok "openssl" || { warn "openssl not installed"; MANUAL+=("sudo apt install openssl"); }

TESSDATA=""
if command -v tesseract >/dev/null 2>&1; then
  ok "tesseract $(tesseract --version 2>&1 | head -1 | awk '{print $2}')"
  langs="$(tesseract --list-langs 2>/dev/null | tail -n +2 | tr '\n' ' ')"
  for need in eng bul; do
    if grep -qw "$need" <<<"$langs"; then ok "tessdata: $need"
    else warn "tessdata missing: $need"; MANUAL+=("sudo apt install tesseract-ocr-$need"); fi
  done
  for c in /usr/share/tesseract-ocr/5/tessdata /usr/share/tesseract-ocr/4.00/tessdata /usr/share/tessdata; do
    [[ -d "$c" ]] && { TESSDATA="$c"; ok "tessdata path: $c"; break; }
  done
else
  warn "tesseract not installed - image receipts will fail, PDFs will still work"
  MANUAL+=("sudo apt install tesseract-ocr tesseract-ocr-eng tesseract-ocr-bul")
fi
: "${TESSDATA:=/usr/share/tesseract-ocr/5/tessdata}"

# --- 2. gradlew executable bit (defect D7) --------------------------------- #
echo
echo "2. Build wrapper"
if [[ -x "$API/gradlew" ]]; then skip "gradlew already executable"
else chmod +x "$API/gradlew"; ok "made gradlew executable"; fi
if [[ "$(git -C "$API" ls-files -s gradlew | cut -d' ' -f1)" == "100644" ]]; then
  warn "gradlew is committed as 100644; ./gradlew fails on a fresh clone"
  MANUAL+=("cd $API && git update-index --chmod=+x gradlew && git commit -m 'fix: make gradlew executable'")
fi

# --- 3. JWT signing keys --------------------------------------------------- #
echo
echo "3. JWT signing keys"
certs="$API/src/main/resources/certs"
if [[ -f "$certs/private.pem" && -f "$certs/public.pem" ]]; then
  skip "keys already present"
else
  mkdir -p "$certs"
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$certs/private.pem" 2>/dev/null
  openssl rsa -in "$certs/private.pem" -pubout -out "$certs/public.pem" 2>/dev/null
  ok "generated RSA keypair (PKCS#8, gitignored)"
fi

# --- 4. backend .env (defect D9) ------------------------------------------- #
# application.yaml has 12 placeholders with no defaults, and spring-dotenv
# reads this file. Without it even `gradlew test` fails, because
# server.port=${SERVER_PORT} will not parse as an Integer.
echo
echo "4. Backend environment"
env_file="$API/.env"
set_default() {
  local key="$1" val="$2"
  if grep -qE "^${key}=.+" "$env_file" 2>/dev/null; then
    skip "$key already set"
  else
    grep -qE "^${key}=" "$env_file" 2>/dev/null \
      && sed -i "s|^${key}=.*|${key}=${val}|" "$env_file" \
      || printf '%s=%s\n' "$key" "$val" >> "$env_file"
    ok "$key=$val"
  fi
}
[[ -f "$env_file" ]] || { printf '# Local development. Gitignored. Not real credentials.\n' > "$env_file"; }


set_default MONGODB_DATABASE  receipts-db
set_default MONGODB_HOST      localhost
set_default MONGODB_PORT      27017
set_default MONGODB_AUTH_DB   admin
set_default MONGODB_USER      receipts
set_default MONGODB_PASS      localdev
set_default MONGODB_DATAPATH  ./.mongo-data
set_default SMTP_HOST         localhost
set_default SMTP_PORT         1025
set_default SMTP_USER         dev
set_default SMTP_PASS         dev
set_default SERVER_PORT       7002
set_default APP_HOST_URL      http://localhost:5173
set_default UI_PORT           7863
set_default BACKEND_HOST      "localhost:7002"
# Linux overrides for defaults that application-dev.yaml points at Windows paths
set_default TESSDATA_PATH     "$TESSDATA"
set_default OCR_LANGUAGE      "eng+bul"
set_default OCR_DEBUG_OUTPUT_PATH ./ocr-debug
set_default APP_CORS_ALLOWED_ORIGINS "http://localhost:5173,https://localhost:5173,http://localhost:7863"
# Single source for the upload ceiling: the API's multipart config and the UI's
# nginx client_max_body_size both derive from this one number.
set_default MAX_UPLOAD_MB 25

if grep -qE '^OPENAI_API_KEY=.+' "$env_file"; then
  ok "OPENAI_API_KEY already set"
else
  grep -qE '^OPENAI_API_KEY=' "$env_file" || printf 'OPENAI_API_KEY=\n' >> "$env_file"
  warn "OPENAI_API_KEY is empty - receipt parsing will fail"
  MANUAL+=("set OPENAI_API_KEY in $env_file")
fi

# --- 5. compose env -------------------------------------------------------- #
echo
echo "5. Compose environment"
# docker-compose.dev.yml reads this via env_file. Deliberately NOT a copy of
# .env: this path sits inside the Java resources tree, so processResources
# packages it into the jar (defect D10). Keep it to the few variables compose
# actually needs, and keep the OpenAI key out of it entirely.
#
# MONGO_INITDB_ROOT_* are required because the compose file starts mongo with
# --auth. Without them no user is ever created on a fresh volume, mongo comes
# up with auth enabled and no credentials, and every connection is refused.
dev_env="$API/src/main/resources/dev.env"
cat > "$dev_env" <<DEVENV
# Generated by scripts/dev-setup.sh - local development only.
# Consumed by docker-compose.dev.yml. Deliberately excludes OPENAI_API_KEY.
MONGO_INITDB_ROOT_USERNAME=${MONGODB_USER:-receipts}
MONGO_INITDB_ROOT_PASSWORD=${MONGODB_PASS:-localdev}
MONGODB_DATABASE=${MONGODB_DATABASE:-receipts-db}
MONGODB_PORT=${MONGODB_PORT:-27017}
MONGODB_DATAPATH=${MONGODB_DATAPATH:-./.mongo-data}
SERVER_PORT=${SERVER_PORT:-7002}
UI_PORT=${UI_PORT:-7863}
BACKEND_HOST=${BACKEND_HOST:-localhost:7002}
MAX_UPLOAD_MB=${MAX_UPLOAD_MB:-25}
DEVENV
ok "wrote dev.env (compose vars only, no OpenAI key)"

# --- 6. UI ----------------------------------------------------------------- #
echo
echo "6. Frontend"
if [[ -d "$UI/node_modules" ]]; then skip "node_modules present (npm ci to refresh)"
elif command -v npm >/dev/null 2>&1; then (cd "$UI" && npm ci --silent) && ok "installed UI dependencies"
else warn "skipped npm ci - npm unavailable"; fi

# vite.config.js serves https only if these two files exist. They are not
# needed: browsers treat http://localhost as a trustworthy origin, so the
# backend's Secure auth cookie is accepted over plain http there. Generating
# them just buys a certificate warning. Opt in with --https if you want to
# exercise a TLS path locally.
if [[ -f "$UI/localhost.crt" && -f "$UI/localhost.key" ]]; then
  skip "UI dev certs present (vite will serve https)"
elif [[ "${1:-}" == "--https" ]]; then
  openssl req -x509 -newkey rsa:2048 -nodes -days 825 \
    -keyout "$UI/localhost.key" -out "$UI/localhost.crt" \
    -subj "/CN=localhost" -addext "subjectAltName=DNS:localhost,IP:127.0.0.1" 2>/dev/null
  ok "generated UI dev certs (gitignored)"
  MANUAL+=("trust https://localhost:5173 in your browser once")
else
  skip "no UI dev certs; vite serves http (fine - localhost is a secure context)"
fi

# --- summary --------------------------------------------------------------- #
echo
if [[ ${#MANUAL[@]} -eq 0 ]]; then
  echo "Environment ready. Nothing left to do manually."
else
  echo "Manual steps remaining (${#MANUAL[@]}):"
  for m in "${MANUAL[@]}"; do printf '  - %s\n' "$m"; done
fi

cat <<'NEXT'

Then:
  docker compose -f <api>/docker-compose.dev.yml up -d   # mongo + mailhog
  scripts/db-restore.sh                                  # import the backups
  cd <api> && ./gradlew bootRun --args='--spring.profiles.active=dev'
  cd <ui>  && npm run dev
NEXT
