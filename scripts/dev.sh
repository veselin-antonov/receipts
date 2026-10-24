#!/usr/bin/env bash
# `make dev`: MongoDB and MailHog in the background, then the api (bootRun,
# 7802) and the ui (vite, 7800) in the foreground, their output interleaved
# and prefixed. Ctrl-C, or either one exiting, stops both; the containers keep
# running (`make down` stops them).
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[[ -f "$root/.env" ]] || { echo "no $root/.env - run make setup first" >&2; exit 1; }

docker compose -f "$root/compose.dev.yaml" up -d --wait

# Job control puts each pipeline in a process group of its own, so stopping one
# reaches every process in it: gradle, the app's JVM, npm and node. Without
# --no-daemon the app would be a child of the shared Gradle daemon, outside
# that group, and outlive Ctrl-C for a while.
set -m
(cd "$root/api" && exec ./gradlew bootRun --no-daemon --console=plain --args='--spring.profiles.active=dev') 2>&1 \
  | sed -u 's/^/[api] /' &
(cd "$root/ui" && exec npm run dev) 2>&1 | sed -u 's/^/[ui]  /' &
groups=$(jobs -p)

stop() {
  trap - INT TERM EXIT
  for g in $groups; do kill -TERM -- "-$g" 2>/dev/null || true; done
  wait 2>/dev/null || true
}
trap stop INT TERM EXIT

# Returns when the first of the two exits; the trap then stops the other.
wait -n
