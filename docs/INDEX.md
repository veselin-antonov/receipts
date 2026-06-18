# Receipts API Documentation Index

This folder contains backend-specific documentation for the Home App / ReceiptsApp API.

## Documentation ownership

Use this split to avoid duplicate, stale docs:

- Obsidian `Home App/`: product vision, cross-repo flows, decisions, status reviews.
- `receipts-api`: backend setup, API behavior, OCR/Spring AI config, deployment/runtime notes.
- `receipts-ui`: React/Vite setup and UI-specific behavior.

When a backend behavior changes, update this repo. When the product flow or cross-repo decision changes, update Obsidian and link to repo docs as needed.

## Start here

| Need | Document |
|---|---|
| Backend quickstart, architecture, endpoints | `../README.md` |
| OCR implementation details | `OCR_IMPLEMENTATION_SUMMARY.md` |
| Spring AI/OpenAI setup | `SPRING_AI_SETUP.md` |
| Backend roadmap notes | `ROADMAP.md` |
| Historical implementation notes | `IMPLEMENTATION_COMPLETE.md` |
| Old implementation checklist | `../IMPLEMENTATION_CHECKLIST.md` |
| CLI/reference snippets | `../QUICKSTART.md`, `../REFERENCE_CARD.md`, `../HELP.md` |

## Current backend map

```text
src/main/java/dev/vasoft/homeapp/
├── auth/
├── users/
└── receipts/
    ├── common/
    ├── products/
    ├── purchases/
    ├── scanning/
    └── stores/
```

## Active endpoints

```text
POST /api/auth/token
GET  /api/auth/status
POST /api/users/register
POST /api/users/verify
POST /api/users/resend-verification
GET  /api/products
POST /api/products
GET  /api/stores
GET  /api/purchases
POST /api/purchases
POST /api/receipts/scan
POST /api/receipts/submit
```

Endpoint and setup details are maintained in `../README.md`.

## Verification

Backend changes should be checked with:

```bash
./gradlew test --console=plain
git diff --check
```

Current receipt-scanning branch status: backend tests pass after the receipt scanning workflow commit.
