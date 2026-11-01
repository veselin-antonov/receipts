#!/usr/bin/env bash
# `make preview PR=<n> [REFRESH=1]`: run pull request <n>'s preview images as
# the preview stack (compose.preview.yaml) at receipts-test.vasoft.dev.
#
# A side the pull request did not touch has no :pr-<n> image; it runs :dev
# (master) instead. The preview database is a copy of the dev database, made
# on the first run and again with REFRESH=1; the copy only reads the source.
# Until production exists (ROADMAP M1b step 7), dev is the source.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
pr="${1:-}"
[[ "$pr" =~ ^[0-9]+$ ]] || { echo "usage: make preview PR=<pull request number> [REFRESH=1]" >&2; exit 2; }
[[ -f "$root/.env" ]] || { echo "no $root/.env - run make setup first" >&2; exit 1; }

registry=ghcr.io/veselin-antonov
tag_for() {
  if docker manifest inspect "$registry/receipts-$1:pr-$pr" >/dev/null 2>&1; then
    echo "pr-$pr"
  else
    echo "dev"
  fi
}
export API_TAG UI_TAG
API_TAG="$(tag_for api)"
UI_TAG="$(tag_for ui)"
if [[ "$API_TAG" == dev && "$UI_TAG" == dev ]]; then
  echo "pull request #$pr has no preview images (only this repository's branches get them)" >&2
  exit 1
fi
echo "api: receipts-api:$API_TAG   ui: receipts-ui:$UI_TAG"

# A key pair of the preview's own, made once
certs="$root/.preview/certs"
if [[ ! -f "$certs/private.pem" ]]; then
  mkdir -p "$certs"
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$certs/private.pem" 2>/dev/null
  openssl rsa -in "$certs/private.pem" -pubout -out "$certs/public.pem" 2>/dev/null
  chmod 644 "$certs/"*.pem
fi

dc() { docker compose -f "$root/compose.preview.yaml" "$@"; }

dc up -d --wait db mailhog

# --- the database: a copy of dev, on the first run or with REFRESH=1 --------- #
set -a; . "$root/.env"; set +a
preview_has_data="$(docker exec receipts-preview-db mongosh --quiet -u preview -p preview \
  --authenticationDatabase admin receipts-db --eval 'db.getCollectionNames().length')"
if [[ "${REFRESH:-}" == 1 || "$preview_has_data" == 0 ]]; then
  echo "copying the dev database into the preview database"
  docker inspect receipts-db-dev >/dev/null 2>&1 \
    || { echo "the dev database (receipts-db-dev) is not running: docker compose -f compose.dev.yaml up -d" >&2; exit 1; }
  docker exec receipts-db-dev mongodump --quiet --archive \
      -u "$MONGODB_USER" -p "$MONGODB_PASS" --authenticationDatabase "$MONGODB_AUTH_DB" \
      --db "$MONGODB_DATABASE" \
    | docker exec -i receipts-preview-db mongorestore --quiet --archive --drop \
      -u preview -p preview --authenticationDatabase admin \
      --nsFrom "$MONGODB_DATABASE.*" --nsTo 'receipts-db.*'
fi

# --- the pull request's api and ui ------------------------------------------ #
dc up -d --pull always --wait

echo
echo "preview of #$pr: https://receipts-test.vasoft.dev (http://localhost:7810)"
echo "  api http://localhost:7812, MailHog http://localhost:7815"
echo "  logs: docker logs -f receipts-preview-api; stop: make down"
