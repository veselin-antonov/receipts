# One entry point for development. docs/DEV_SETUP.md explains each target.
.PHONY: setup dev down test e2e db-restore db-refresh

# Prerequisites, JWT keys, the root .env and the ui's dependencies
setup:
	scripts/dev-setup.sh

# MongoDB and MailHog, then the api (7802) and vite (7800) in the foreground;
# Ctrl-C stops both
dev:
	scripts/dev.sh

# Stop the dev containers. Their data stays in MONGODB_DATAPATH
down:
	docker compose -f compose.dev.yaml down

# The api's tests, and the ui's lint, format check and tests
test:
	cd api && ./gradlew test --console=plain
	cd ui && npm run lint && npm run format:check && npm test

# Both images end to end under Playwright; see e2e/run.sh for its variables
e2e:
	e2e/run.sh

# Import the Jan-2026 backups into the dev database (replaces its collections)
db-restore:
	scripts/db-restore.sh

db-refresh:
	@echo "db-refresh copies production's database into dev; there is no production stack yet (ROADMAP M1b step 7)" >&2
	@exit 1
