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
| OCR implementation details | `OCR_IMPLEMENTATION_SUMMARY.md` |
| Spring AI/OpenAI setup | `SPRING_AI_SETUP.md` |
| Backend roadmap notes | `ROADMAP.md` |

## Legacy / historical docs

These still contain useful context, but they are no longer the primary entry points:

| Document | Purpose |
|---|---|
| `IMPLEMENTATION_COMPLETE.md` | earlier implementation narrative |
| `../IMPLEMENTATION_CHECKLIST.md` | older implementation checklist |
| `../QUICKSTART.md` | older quickstart/reference notes |
| `../REFERENCE_CARD.md` | command reference |
| `../HELP.md` | generated/help-style notes |

## Documentation ownership

To avoid duplicated and stale docs:

- Obsidian `Home App/` holds product vision, cross-repo flows, decisions, and status reviews.
- `receipts-api` holds backend setup, runtime config, deployment notes, architecture, and endpoint behavior.
- `receipts-ui` holds React/Vite setup and UI-specific behavior.

If a change is caused by backend code, document it here.
If a change is caused by a cross-repo product decision, document it in Obsidian and link back here when needed.
