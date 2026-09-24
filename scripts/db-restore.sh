#!/usr/bin/env bash
# Migrate the Jan-2026 backups onto the current schema and import them.
#
# The backups cannot be imported as they are: they predate the package rename,
# the domain refactor, the canonicalName rename, and user-scoped purchases.
# migrate-backups.py handles all of that; this script runs it and loads the
# result.
#
# Destructive: --drop replaces each collection. Aimed at a dev database.

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
API="$root/api"

SRC="${SRC:-$HOME/docker-apps/homeapp/db/db-backup}"
OUT="${OUT:-$root/.migrated}"
[[ -f "$API/.env" ]] || { echo "no $API/.env - run scripts/dev-setup.sh first" >&2; exit 1; }
set -a; . "$API/.env"; set +a

: "${MONGODB_HOST:?}" "${MONGODB_PORT:?}" "${MONGODB_DATABASE:?}"
: "${MONGODB_USER:?}" "${MONGODB_PASS:?}" "${MONGODB_AUTH_DB:?}"

echo "==> Migrating backups to the current schema"
python3 "$root/scripts/migrate-backups.py" --src "$SRC" --out "$OUT"

URI="mongodb://${MONGODB_USER}:${MONGODB_PASS}@${MONGODB_HOST}:${MONGODB_PORT}/${MONGODB_DATABASE}?authSource=${MONGODB_AUTH_DB}"

# mongoimport is rarely installed on the host. Prefer it if present, otherwise
# run it inside the already-running MongoDB container: spawning a second
# mongo:latest just for the client trips over that image's declared volumes.
MONGO_CONTAINER="${MONGO_CONTAINER:-}"
if [[ -z "$MONGO_CONTAINER" ]] && ! command -v mongoimport >/dev/null 2>&1; then
  MONGO_CONTAINER="$(docker ps --filter ancestor=mongo --format '{{.Names}}' | head -1)"
  [[ -n "$MONGO_CONTAINER" ]] || {
    echo "no running mongo container found, and mongoimport is not on PATH." >&2
    echo "start one with: docker compose -f $API/docker-compose.dev.yml up -d" >&2
    echo "or set MONGO_CONTAINER=<name>" >&2
    exit 1
  }
fi

if [[ -n "$MONGO_CONTAINER" ]]; then
  echo "    using mongo client inside container '$MONGO_CONTAINER'"
  docker exec "$MONGO_CONTAINER" rm -rf /tmp/restore
  docker exec "$MONGO_CONTAINER" mkdir -p /tmp/restore
  for c in users products stores purchases; do
    docker cp "$OUT/${c}.json" "$MONGO_CONTAINER:/tmp/restore/${c}.json" >/dev/null
  done
  run_import() { docker exec "$MONGO_CONTAINER" mongoimport "$@"; }
  run_shell()  { docker exec -i "$MONGO_CONTAINER" mongosh "$@"; }
  FILE_DIR=/tmp/restore
else
  run_import() { mongoimport "$@"; }
  run_shell()  { mongosh "$@"; }
  FILE_DIR="$OUT"
fi

echo
echo "==> Importing into ${MONGODB_DATABASE} at ${MONGODB_HOST}:${MONGODB_PORT}"
for c in users products stores purchases; do
  printf '  %-10s ' "$c"
  run_import --uri "$URI" --collection "$c" --file "${FILE_DIR}/${c}.json" \
      --jsonArray --drop --quiet && echo "ok"
done

echo
echo "==> Verifying"
run_shell "$URI" --quiet --eval '
  ["users","products","stores","purchases"].forEach(c =>
    print("  " + c.padEnd(10) + db[c].countDocuments()));
  print("  purchases without userId:  " + db.purchases.countDocuments({userId: {$exists: false}}));
  print("  products unnormalized:     " + db.products.countDocuments({normalizedCanonicalName: {$in: [null, ""]}}));
  print("  dates not at midnight UTC: " + db.purchases.countDocuments(
    {$expr: {$ne: [{$dateToString: {date: "$date", format: "%H:%M:%S"}}, "00:00:00"]}}));
  const p = db.purchases.find().sort({date: 1}).limit(1).toArray()[0];
  print("  earliest purchase: " + p.date.toISOString().slice(0,10) + "  " + p.price);
'

if [[ -n "$MONGO_CONTAINER" ]]; then
  docker exec "$MONGO_CONTAINER" rm -rf /tmp/restore
fi
