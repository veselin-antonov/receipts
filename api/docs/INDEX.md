# Receipts API Documentation Index

This folder contains backend-specific documentation for the Home App / ReceiptsApp API.

## Recommended reading order

1. `../README.md` - backend overview and quickstart
2. `DEVELOPMENT_SETUP.md` - local setup and daily development workflow
3. `ARCHITECTURE_AND_FLOWS.md` - main components, endpoints, and request flows
4. `PRODUCTION_SETUP.md` - production deployment and runtime configuration

Then use the deeper reference docs as needed.

## Core docs

| Need | Document |
|---|---|
| Backend overview and quickstart | `../README.md` |
| Local development setup | `DEVELOPMENT_SETUP.md` |
| Production setup and deployment | `PRODUCTION_SETUP.md` |
| Main components and request flows | `ARCHITECTURE_AND_FLOWS.md` |
| Active request/response contracts | `API_CONTRACTS.md` |
| Receipt scanning feature | `RECEIPT_SCANNING.md` |

## Documentation ownership

To avoid duplicated and stale docs:

- Obsidian `Home App/` holds product vision, cross-repo flows, decisions, and status reviews.
- `receipts-api` holds backend setup, runtime config, deployment notes, architecture, and endpoint behavior.
- `receipts-ui` holds React/Vite setup and UI-specific behavior.

If a change is caused by backend code, document it here.
If a change is caused by a cross-repo product decision, document it in Obsidian and link back here when needed.
