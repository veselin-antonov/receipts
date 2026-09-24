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
   - classpath JWT keys
   - local OCR defaults
   - allowed local frontend origins
   - verbose logging

Run the app with the `dev` profile:

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

## Step 1: generate local JWT keys

The dev profile expects key files on the classpath.

```bash
mkdir -p src/main/resources/certs
openssl genrsa -out src/main/resources/certs/private.pem 2048
openssl rsa -in src/main/resources/certs/private.pem -pubout -out src/main/resources/certs/public.pem
```

Expected dev configuration:

```text
jwt.private-key=classpath:certs/private.pem
jwt.public-key=classpath:certs/public.pem
```

These files are local-only and must never be committed.

## Step 2: set environment variables

Minimum useful local environment:

```bash
export OPENAI_API_KEY='<your-openai-api-key>'
export TESSDATA_PATH='C:/Program Files/Tesseract-OCR/tessdata'
export OCR_LANGUAGE='eng+bul'
export OCR_DEBUG_OUTPUT_PATH='./ocr-debug'
export APP_CORS_ALLOWED_ORIGINS='https://localhost:5173,http://localhost:5173,http://localhost:7863'
```

You will also need the MongoDB and SMTP variables used by `application.yaml`, unless they are being supplied through your local shell, IDE run configuration, or a local env file consumed by your environment.

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

You can run MongoDB any way you like, but the repo includes `docker-compose.dev.yml` for local supporting services.

Current compose file includes:
- `receipts-db` - MongoDB with auth
- `mailhog` - SMTP sink and email UI
- `homeapp-ui` - UI container image for local integration

Note: the compose file does not start the backend container itself. A common local workflow is:
- run MongoDB and MailHog through Docker Compose
- run the backend with Gradle on the host machine
- run the UI locally or via the UI dev container

Start supporting services:

```bash
docker compose -f docker-compose.dev.yml up -d
```

The compose file expects values from:

```text
./src/main/resources/dev.env
```

That file is intentionally local/dev-specific and should be treated as environment-specific configuration.

### Mail testing

MailHog ports from the dev compose file:
- SMTP: `1025`
- Web UI: `8025`

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
- compose env values missing from `src/main/resources/dev.env`
