#!/usr/bin/env bash
# Copy one stack's database over another's: db-copy.sh <from> <to>, each one of
# prod, dev or preview. mongodump | mongorestore --drop, so the target's
# collections are replaced and the source is only read. `make db-refresh` is
# prod -> dev; scripts/preview.sh fills the preview database with it.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
from="${1:-}" to="${2:-}"

# <container> <user> <password> <auth db> <database>, per stack
stack() {
  case "$1" in
    prod)
      docker inspect receipts-db >/dev/null 2>&1 \
        || { echo "production's database (receipts-db) is not running" >&2; exit 1; }
      # The production stack keeps its credentials in its own .env; the
      # container's environment has them, so they never pass through here.
      echo "receipts-db - - admin $(docker exec receipts-db printenv MONGO_INITDB_DATABASE)" ;;
    dev)
      [[ -f "$root/.env" ]] || { echo "no $root/.env - run make setup first" >&2; exit 1; }
      docker inspect receipts-db-dev >/dev/null 2>&1 \
        || { echo "the dev database is not running: docker compose -f compose.dev.yaml up -d" >&2; exit 1; }
      ( set -a; . "$root/.env"; echo "receipts-db-dev $MONGODB_USER $MONGODB_PASS $MONGODB_AUTH_DB $MONGODB_DATABASE" ) ;;
    preview)
      echo "receipts-preview-db preview preview admin receipts-db" ;;
    *) echo "usage: db-copy.sh <prod|dev|preview> <prod|dev|preview>" >&2; exit 2 ;;
  esac
}
[[ "$to" != prod ]] || { echo "nothing writes to production" >&2; exit 2; }
[[ "$from" != "$to" ]] || { echo "usage: db-copy.sh <from> <to>, two different stacks" >&2; exit 2; }
read -r src_c src_u src_p src_auth src_db <<<"$(stack "$from")"
read -r dst_c dst_u dst_p dst_auth dst_db <<<"$(stack "$to")"

echo "copying $from ($src_db) into $to ($dst_db), replacing its collections"
if [[ "$src_u" == - ]]; then
  docker exec receipts-db sh -c 'exec mongodump --quiet --archive \
    -u "$MONGO_INITDB_ROOT_USERNAME" -p "$MONGO_INITDB_ROOT_PASSWORD" \
    --authenticationDatabase admin --db "$MONGO_INITDB_DATABASE"'
else
  docker exec "$src_c" mongodump --quiet --archive \
    -u "$src_u" -p "$src_p" --authenticationDatabase "$src_auth" --db "$src_db"
fi | docker exec -i "$dst_c" mongorestore --quiet --archive --drop \
    -u "$dst_u" -p "$dst_p" --authenticationDatabase "$dst_auth" \
    --nsFrom "$src_db.*" --nsTo "$dst_db.*"

docker exec "$dst_c" mongosh --quiet -u "$dst_u" -p "$dst_p" --authenticationDatabase "$dst_auth" "$dst_db" \
  --eval 'print(db.purchases.countDocuments() + " purchases, " + db.users.countDocuments() + " users")'
