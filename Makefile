# One entry point for development. docs/DEV_SETUP.md explains each target.
.PHONY: setup dev down test e2e preview db-restore db-refresh

# Prerequisites, JWT keys, the root .env and the ui's dependencies
setup:
	scripts/dev-setup.sh

# MongoDB and MailHog, then the api (7802) and vite (7800) in the foreground;
# Ctrl-C stops both
dev:
	scripts/dev.sh

# Stop the dev and preview containers. Their data stays: dev's in
# MONGODB_DATAPATH, the preview's in its volume
down:
	docker compose -f compose.dev.yaml down
	docker compose -f compose.preview.yaml down

# The api's tests, and the ui's lint, format check and tests
test:
	cd api && ./gradlew test --console=plain
	cd ui && npm run lint && npm run format:check && npm test

# Both images end to end under Playwright; see e2e/run.sh for its variables
e2e:
	e2e/run.sh

# Pull request <n>'s preview images at receipts-test.vasoft.dev (7810), on a
# database of its own; REFRESH=1 copies production's into it again
preview:
	scripts/preview.sh $(PR)

# Import the Jan-2026 backups into the dev database (replaces its collections)
db-restore:
	scripts/db-restore.sh

# Replace the dev database with a copy of production's (read-only on production)
db-refresh:
	scripts/db-copy.sh prod dev
