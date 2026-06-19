# Production Setup

This document explains the production/runtime setup expected by the backend.

## Production runtime requirements

Required services and inputs:
- Java 25 runtime or the built Docker image
- MongoDB
- SMTP server
- OpenAI API key
- RSA JWT key pair
- Tesseract OCR runtime with tessdata available
- environment-variable or secrets-management support

## Configuration model

Production primarily uses values from `application.yaml` via environment variables.

Key groups:
- MongoDB connection
- SMTP settings
- server port
- JWT keys
- app host URL
- OCR settings
- CORS / frontend origin settings when applicable
- OpenAI API key
- rate-limiting defaults

## Required environment variables

From `application.yaml`, these are the main production variables to supply:

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
JWT_PRIVATE_KEY
JWT_PUBLIC_KEY
```

Common OCR-related variables:

```text
TESSDATA_PATH
OCR_LANGUAGE
OCR_DEBUG_OUTPUT_PATH
```

In most production environments, `OCR_DEBUG_OUTPUT_PATH` should be left empty unless you are actively diagnosing OCR problems and have a place to store those debug images.

## JWT key handling

Production should not use local classpath cert files.

The backend supports JWT keys as either:

1. PEM content directly
2. file references such as `file:/path/to/key.pem`

Examples:

```bash
export JWT_PRIVATE_KEY='-----BEGIN PRIVATE KEY-----...'
export JWT_PUBLIC_KEY='-----BEGIN PUBLIC KEY-----...'
```

or:

```bash
export JWT_PRIVATE_KEY='file:/etc/secrets/private.pem'
export JWT_PUBLIC_KEY='file:/etc/secrets/public.pem'
```

Use your platform's secret management instead of committing key files.

## Docker image expectations

The repository Dockerfile:
- starts from `eclipse-temurin:25-jre-alpine`
- installs `tesseract-ocr`
- installs English and Bulgarian tessdata packages
- copies `build/libs/receipts-api-${VERSION}.jar`
- runs `java -jar app.jar`

This means the container image is already designed to support OCR at runtime, as long as the remaining environment variables and external services are configured.

## Production deployment checklist

### 1. Build the application

```bash
./gradlew clean build
```

### 2. Build the image if deploying with Docker

The Gradle file defines image tasks:

```bash
./gradlew buildImage -PrepositoryName=<owner/repo>
./gradlew tagImage -PrepositoryName=<owner/repo>
```

### 3. Provide runtime secrets and environment

At minimum configure:
- MongoDB credentials and host info
- SMTP credentials and host info
- OpenAI API key
- JWT private/public keys
- `APP_HOST_URL`
- `SERVER_PORT`
- `TESSDATA_PATH` if your runtime differs from the Docker default

### 4. Ensure OCR runtime is valid

Container default from `application.yaml`:

```text
/usr/share/tessdata
```

If you are not using the provided Docker image, make sure:
- Tesseract is installed on the host
- tessdata exists at the configured path
- the required language packs are installed

### 5. Verify frontend/backend integration assumptions

Important auth/cookie characteristics in production:
- JWT is stored in an HttpOnly cookie
- cookie is marked Secure
- SameSite is Strict

So production deployment must ensure:
- HTTPS is correctly terminated
- frontend and backend origin behavior matches the cookie strategy
- browser clients can actually store and send the cookie in the intended flow

### 6. Verify rate limiting and file upload constraints

Current production defaults include:
- 10MB multipart upload limit
- strict rate limit for receipt scanning
- per-IP and per-user limits on sensitive endpoints

These values affect both UX and cost.

## Recommended smoke tests after deployment

### Auth
- log in via `POST /api/auth/token`
- confirm cookie is set
- call `GET /api/auth/status`

### Registration
- create a new user
- confirm SMTP delivery path works
- verify the account

### Purchases
- create a purchase
- fetch paginated purchases
- confirm user isolation still holds

### Receipt scanning
- upload a real image receipt
- upload a PDF receipt
- confirm scan result payload looks valid
- submit reviewed purchases

## Production risks to watch closely

- JWT secret/key misconfiguration
- SMTP credentials or TLS mismatch
- MongoDB auth DB mismatch
- OCR runtime installed but tessdata path wrong
- cookie delivery broken by proxy/HTTPS/origin setup
- OpenAI key missing or rate/cost surprises on scan traffic
- upload size too small for realistic receipt files

## When to update this document

Update this doc whenever you change:
- required environment variables
- Dockerfile/runtime assumptions
- JWT key loading behavior
- OCR runtime dependencies
- cookie/security deployment assumptions
- production verification steps
