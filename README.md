# Receipts API

Spring Boot backend for the Home App / ReceiptsApp project.

The API owns authentication, users, products, stores, user-scoped purchases, and receipt scanning/parsing. The React UI lives in the separate `receipts-ui` repo.

## Documentation model

To avoid duplicated and stale docs:

- Product vision, cross-repo flows, and status reviews live in Obsidian under `Home App/`.
- Backend setup, API behavior, runtime config, and verification live in this repo.
- UI setup and UI-specific behavior live in `receipts-ui`.

Start here for backend docs:

- `docs/INDEX.md` - backend documentation map
- `docs/OCR_IMPLEMENTATION_SUMMARY.md` - OCR pipeline details
- `docs/SPRING_AI_SETUP.md` - Spring AI/OpenAI setup notes
- `docs/ROADMAP.md` - backend-oriented roadmap notes

## Current architecture

Main backend areas:

```text
src/main/java/dev/vasoft/homeapp/
├── auth/                 # JWT auth, security config, current-user lookup
├── users/                # registration, verification, resend verification
└── receipts/
    ├── common/           # shared DTOs, repositories, formatting, CORS config
    ├── products/         # products API/domain/service
    ├── purchases/        # user-scoped purchases API/domain/service
    ├── scanning/         # OCR/LLM receipt scanning and matching
    └── stores/           # stores API/domain/service
```

Important convention: purchases are scoped to the authenticated user. Do not add purchase queries or writes that skip current-user lookup.

## Runtime requirements

- Java 25 toolchain
- Gradle wrapper from this repo
- MongoDB
- RSA key pair for JWT signing
- OpenAI API key for LLM receipt parsing
- Tesseract/tessdata for OCR image parsing

## Local setup

### 1. JWT keys

Generate local JWT keys. The `certs` files are ignored and must not be committed.

```bash
mkdir -p src/main/resources/certs
openssl genrsa -out src/main/resources/certs/private.pem 2048
openssl rsa -in src/main/resources/certs/private.pem -pubout -out src/main/resources/certs/public.pem
```

The `dev` profile loads:

```text
classpath:certs/private.pem
classpath:certs/public.pem
```

### 2. Environment

Typical local values:

```bash
export OPENAI_API_KEY='<your-openai-api-key>'
export TESSDATA_PATH='C:/Program Files/Tesseract-OCR/tessdata'
export OCR_LANGUAGE='eng+bul'
export OCR_DEBUG_OUTPUT_PATH='./ocr-debug'
export APP_CORS_ALLOWED_ORIGINS='https://localhost:5173,http://localhost:5173,http://localhost:7863'
```

Production should pass JWT keys through environment variables or secret management:

```bash
export JWT_PRIVATE_KEY='-----BEGIN PRIVATE KEY-----...'
export JWT_PUBLIC_KEY='-----BEGIN PUBLIC KEY-----...'
```

Never commit private keys or `.env` files.

### 3. Run

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### 4. Test

```bash
./gradlew test --console=plain
```

Latest verified result in this branch:

```text
BUILD SUCCESSFUL
```

## API surface

All authenticated UI calls use the HttpOnly JWT cookie set by `POST /api/auth/token`; the UI should send same-origin credentials rather than storing bearer tokens in localStorage.

### Auth/users

```text
POST /api/auth/token
GET  /api/auth/status
POST /api/users/register
POST /api/users/verify?user=<email>&token=<token>
POST /api/users/resend-verification
```

Notes:

- `POST /api/auth/token` returns token lifetime seconds and sets the JWT cookie.
- `403` from token generation can mean credentials were accepted but the account is inactive/unverified.
- Verification resend endpoint is `POST /api/users/resend-verification`.

### Products/stores

```text
GET  /api/products
POST /api/products?name=<name>
GET  /api/stores
```

### Purchases

```text
GET  /api/purchases?pageNumber=0&pageSize=0&searchQuery=<query>
POST /api/purchases
```

Purchases are user-scoped through the authenticated JWT.

### Receipt scanning

```text
POST /api/receipts/scan
Content-Type: multipart/form-data
field: file=<receipt image or PDF>
```

Returns parsed store/date/purchase rows plus product/store suggestions for UI review.

```text
POST /api/receipts/submit
Content-Type: application/json
```

Submits reviewed parsed purchases and persists them for the authenticated user.

## Receipt scanning flow

```text
receipt upload
→ OCR or LLM parsing
→ normalization
→ product/store matching
→ UI review payload
→ reviewed submit
→ user-scoped purchase records
```

Key classes:

- `ReceiptScanController`
- `ReceiptScanService`
- `OcrService`
- `LlmReceiptParser`
- `NormalizationService`
- `MatcherService`
- `AuthenticatedUserService`

## Verification before committing backend changes

```bash
./gradlew test --console=plain
git diff --check
git status --short
```

If docs or generated files are touched on Windows, run `git diff --check` before committing to catch trailing whitespace/line-ending problems.
