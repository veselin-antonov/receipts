# Receipts API

Spring Boot backend for the Home App / ReceiptsApp project.

This service owns:
- authentication and JWT cookie issuance
- user registration and verification
- products and stores catalogs
- user-scoped purchases
- receipt scanning, OCR/LLM parsing, matching, and purchase submission

The React frontend lives in the separate `receipts-ui` repository.

## How to use these docs

Start with the docs that match your task:

- `docs/INDEX.md` - full backend docs map
- `docs/DEVELOPMENT_SETUP.md` - local development setup and day-to-day workflow
- `docs/PRODUCTION_SETUP.md` - production/runtime configuration and deployment checklist
- `docs/ARCHITECTURE_AND_FLOWS.md` - main components, endpoints, and request flows
- `docs/API_CONTRACTS.md` - request/response examples for active endpoints
- `docs/RECEIPT_SCANNING.md` - canonical feature doc for receipt scanning, OCR, AI parsing, matching, and submit flow

## Quickstart

Prerequisites:
- Java 25
- MongoDB
- OpenSSL for generating local JWT keys
- OpenAI API key for LLM receipt parsing
- Tesseract OCR with tessdata for image receipt parsing

Generate local JWT keys:

```bash
mkdir -p src/main/resources/certs
openssl genrsa -out src/main/resources/certs/private.pem 2048
openssl rsa -in src/main/resources/certs/private.pem -pubout -out src/main/resources/certs/public.pem
```

Set local environment variables:

```bash
export OPENAI_API_KEY='<your-openai-api-key>'
export TESSDATA_PATH='C:/Program Files/Tesseract-OCR/tessdata'
export OCR_LANGUAGE='eng+bul'
export OCR_DEBUG_OUTPUT_PATH='./ocr-debug'
export APP_CORS_ALLOWED_ORIGINS='https://localhost:5173,http://localhost:5173,http://localhost:7863'
```

Run the backend in dev profile:

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

Run tests:

```bash
./gradlew test --console=plain
```

## What is important to know before changing code

- The backend sets an HttpOnly JWT cookie on `POST /api/auth/token`.
- The UI should use same-origin credentials instead of localStorage bearer tokens.
- Purchases must always be scoped to the authenticated user.
- Receipt scanning accepts images and PDFs, but images and PDFs take different paths internally.
- Production and development use different configuration sources for JWT keys, OCR paths, and infrastructure.

Those conventions are documented in detail in `docs/ARCHITECTURE_AND_FLOWS.md`, `docs/DEVELOPMENT_SETUP.md`, and `docs/PRODUCTION_SETUP.md`.

## Verification before committing

```bash
./gradlew test --console=plain
git diff --check
git status --short
```

If docs or generated files are touched on Windows, keep `git diff --check` in the loop to catch whitespace and line-ending issues.
