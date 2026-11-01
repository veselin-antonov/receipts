# Receipts API

[![API](https://github.com/veselin-antonov/receipts/actions/workflows/api.yml/badge.svg)](https://github.com/veselin-antonov/receipts/actions/workflows/api.yml)

Spring Boot backend for Receipts.

This service owns:
- authentication and JWT cookie issuance
- user registration and verification
- products and stores catalogs
- user-scoped purchases
- receipt scanning, OCR/LLM parsing, matching, and purchase submission

The React frontend lives next door in [`../ui`](../ui). Product docs — spec,
roadmap, decisions, and the cross-cutting docs below — live in [`../docs`](../docs).

## How to use these docs

Start with the docs that match your task:

- [`../docs/DEV_SETUP.md`](../docs/DEV_SETUP.md) - developing the api and the ui together: `make setup`, `make dev`, `make test`
- [`../deploy/README.md`](../deploy/README.md) - production runtime configuration and deployment checks
- [`../docs/ARCHITECTURE_AND_FLOWS.md`](../docs/ARCHITECTURE_AND_FLOWS.md) - main components, endpoints, and request flows
- [`../docs/API_CONTRACTS.md`](../docs/API_CONTRACTS.md) - request/response examples for active endpoints
- [`../docs/RECEIPT_SCANNING.md`](../docs/RECEIPT_SCANNING.md) - canonical feature doc for receipt scanning, OCR, AI parsing, matching, and submit flow

## What is important to know before changing code

- The backend sets an HttpOnly JWT cookie on `POST /api/auth/token`.
- The UI should use same-origin credentials instead of localStorage bearer tokens.
- Purchases must always be scoped to the authenticated user.
- Receipt scanning accepts images and PDFs, but images and PDFs take different paths internally.
- Production and development use different configuration sources for JWT keys, OCR paths, and infrastructure.

Those conventions are documented in detail in `../docs/ARCHITECTURE_AND_FLOWS.md`, `../docs/DEV_SETUP.md`, and `../deploy/README.md`.

## Verification before committing

`make test` from the repository root runs these tests and the ui's; from here:

```bash
./gradlew test --console=plain
git diff --check
```
