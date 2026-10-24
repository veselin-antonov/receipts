# Development Setup

This document explains how to run the backend locally for day-to-day development.

## What local development needs

Required:
- Java 25
- MongoDB
- OpenSSL
- OpenAI API key
- Tesseract OCR installed locally with tessdata available

Helpful:
- Docker / Docker Compose for local supporting services
- MailHog for email testing

## Local configuration model

Two configuration layers matter in development:

1. `application.yaml`
   - base/shared configuration
   - MongoDB, mail, Spring AI, multipart, JWT, OCR, rate limiting
2. `application-dev.yaml`
   - local development overrides
   - JWT keys from `api/certs/` (`file:` paths)
   - local OCR defaults
   - allowed local frontend origins
   - verbose logging

Run the app with the `dev` profile:

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

## Step 1: generate local JWT keys

`scripts/dev-setup.sh` does this for you. By hand, from `api/`:

```bash
mkdir -p certs
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out certs/private.pem
openssl rsa -in certs/private.pem -pubout -out certs/public.pem
```

The dev profile reads them from the working directory, which is `api/` under
`bootRun`:

```text
jwt.private-key=${JWT_PRIVATE_KEY:file:./certs/private.pem}
jwt.public-key=${JWT_PUBLIC_KEY:file:./certs/public.pem}
```

`api/certs/` is gitignored. Never put keys under `src/main/resources`: they
would be packaged into the jar, so the build excludes them and
`verifyNoSecretsInJar` fails any jar that contains a `.pem` or `.env` (D10).

## Step 2: set environment variables

Minimum useful local environment:

```bash
export OPENAI_API_KEY='<your-openai-api-key>'
export TESSDATA_PATH='C:/Program Files/Tesseract-OCR/tessdata'
export OCR_LANGUAGE='eng+bul'
export OCR_DEBUG_OUTPUT_PATH='./ocr-debug'
export APP_CORS_ALLOWED_ORIGINS='http://localhost:7800'
```

You will also need the MongoDB and SMTP variables used by `application.yaml`. `bootRun` reads them from the repository root's `.env` (written by `scripts/dev-setup.sh`, documented in the root `example.env`); a shell export or IDE run configuration overrides it. An IDE run outside Gradle reads that file only with `-Dspringdotenv.directory=..`.

Important variables from the base config:

```text
MONGODB_HOST
MONGODB_PORT
MONGODB_DATABASE
MONGODB_AUTH_DB
MONGODB_USER
MONGODB_PASS
SMTP_HOST
SMTP_PORT
SMTP_USER
SMTP_PASS
SERVER_PORT
APP_HOST_URL
OPENAI_API_KEY
```

## Step 3: provide local infrastructure

### MongoDB

You can run MongoDB any way you like, but the repository root has
`compose.dev.yaml` for the local supporting services:
- `receipts-db` - MongoDB with auth, on `127.0.0.1:27017`
- `mailhog` - SMTP sink and email UI

The backend runs with Gradle on the host (7802) and the UI on vite (7800).
From the repository root:

```bash
docker compose -f compose.dev.yaml up -d
```

The compose file interpolates from the root `.env` next to it, the same file
`bootRun` reads.

### Mail testing

MailHog ports from the dev compose file:
- SMTP: `1025`
- Web UI: `7805`

This is useful for register/verify/resend-verification flows.

## Step 4: run the backend

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

## Step 5: verify the backend

Run tests:

```bash
./gradlew test --console=plain
```

Recommended local checks before committing:

```bash
./gradlew test --console=plain
git diff --check
git status --short
```

## Local browser/auth notes

The auth cookie created by `AuthController` is:
- HttpOnly
- Secure
- SameSite=Strict

That means local browser behavior depends on how you run the frontend and backend origins/protocols. When auth looks broken, verify:
- the browser is actually accepting the cookie
- the frontend is sending same-origin credentials
- your local scheme/origin setup matches the allowed dev origins

## Development testing by main flow

### Auth/login
- call `POST /api/auth/token`
- confirm cookie is set
- call `GET /api/auth/status`
- confirm the remaining session time is returned

### Register/verify
- register a user
- inspect MailHog for verification email
- verify the account
- test resend-verification for an inactive user

### Purchases
- create a purchase through `POST /api/purchases`
- fetch purchases through `GET /api/purchases`
- confirm results are scoped to the authenticated user

### Receipt scanning
- upload a real image receipt
- upload a PDF receipt
- confirm parsed rows are returned
- confirm reviewed submit creates purchases for the current user
- if OCR quality looks wrong, set `OCR_DEBUG_OUTPUT_PATH` and inspect debug images

## Common local failure points

- missing JWT key files
- invalid MongoDB credentials or auth DB
- missing `OPENAI_API_KEY`
- wrong `TESSDATA_PATH`
- Tesseract installed but language data missing
- cookie not being sent because of local origin/protocol mismatch
- compose values missing from `.env` (it falls back to the dev defaults)
