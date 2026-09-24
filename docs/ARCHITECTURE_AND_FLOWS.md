# Architecture and Main Flows

This document explains the main backend components, active endpoints, and the request flows that matter when changing the API.

## Module map

```text
src/main/java/dev/vasoft/homeapp/
├── auth/
│   ├── api/controllers
│   ├── config
│   └── services
├── users/
│   ├── api/controllers
│   └── services
└── receipts/
    ├── common/
    ├── products/
    ├── purchases/
    ├── scanning/
    └── stores/
```

### Responsibilities by area

- `auth`
  - handles login token issuance and auth-status checks
  - configures Spring Security and JWT cookie auth
- `users`
  - handles register, verify, and resend-verification flows
- `receipts.products`
  - product catalog reads and product registration
- `receipts.stores`
  - store catalog reads
- `receipts.purchases`
  - user-scoped purchase reads and direct purchase creation
- `receipts.scanning`
  - upload validation, OCR/LLM parsing, normalization, matching, and reviewed submit flow
- `receipts.common`
  - shared DTOs, repository utilities, web config, and `MongoConfig` (dates
    stored as UTC). There is no server-side display formatting: the API emits
    data and the UI formats it

## Security model

`SecurityConfiguration` defines two main security paths:

1. `POST /api/auth/token`
   - HTTP Basic authentication
   - stateless
   - used to exchange credentials for a JWT cookie

2. the rest of the API
   - stateless resource server using JWT
   - JWT is loaded from the cookie by `JwtCookieFilter`
   - public endpoints are limited to registration and verification
   - resend-verification is allowed only for inactive users
   - most API routes require the active-user authority

Cookie characteristics from `AuthController`:
- HttpOnly
- Secure
- SameSite=Strict
- path `/`

Implication: local and production environments must be configured so browser cookie behavior still works as expected.

## Main endpoints

### Auth

```text
POST /api/auth/token
GET  /api/auth/status
```

Behavior:
- `POST /api/auth/token` returns token lifetime and sets the JWT cookie.
- If the token type is limited/inactive, the controller returns `403` instead of `200`.
- `GET /api/auth/status` returns remaining validity for an existing authenticated cookie session.

### Users

```text
POST /api/users/register
POST /api/users/verify?user=<email>&token=<token>
POST /api/users/resend-verification
```

Behavior:
- register returns `201` on success and `409` if the user already exists
- verify returns:
  - `200` when verification succeeds
  - `404` when the verification token is missing
  - `400` when the verification link is invalid
- resend-verification requires an authenticated inactive user

### Products and stores

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

Important rule:
- purchases are always scoped to the authenticated user through `AuthenticatedUserService`
- do not introduce purchase reads or writes that bypass current-user lookup

### Receipt scanning

```text
POST /api/receipts/scan
POST /api/receipts/submit
```

`/scan` accepts multipart form-data with a `file` field.
`/submit` accepts reviewed purchases as JSON.

## Flow: login and cookie session

```text
client sends HTTP Basic credentials to POST /api/auth/token
→ AuthController asks TokenService to generate JWT
→ controller sets JWT cookie on the response
→ response body returns token lifetime
→ later requests include the cookie
→ JwtCookieFilter extracts the JWT from the cookie
→ Spring Security authorizes the request
```

Practical consequence for frontend/backend integration:
- the UI should use same-origin credentials
- `403` from login can mean accepted credentials for an inactive account, not just bad credentials

## Flow: registration and verification

```text
client POST /api/users/register
→ UserService creates the user and sends verification email
→ user opens verification link
→ client POST /api/users/verify?user=<email>&token=<token>
→ UserService verifies and activates the account
```

Resend flow:

```text
inactive authenticated user POST /api/users/resend-verification
→ UserService resends verification email
```

## Flow: direct purchases API

```text
authenticated client GET /api/purchases
→ PurchasesController resolves current user id
→ PurchaseService queries only that user's purchases
→ paged result returned
```

```text
authenticated client POST /api/purchases
→ PurchasesController resolves current user id
→ PurchaseService saves purchase for that user
→ created purchase returned
```

## Flow: receipt scanning and reviewed submit

High-level flow:

```text
upload receipt file
→ validate file type and size
→ route by content type
→ parse receipt
→ match store and products
→ return review payload
→ client edits/matches rows
→ submit reviewed purchases
→ save purchases for authenticated user
```

Detailed branching inside `ReceiptScanService`:

```text
POST /api/receipts/scan
→ validate file
→ if image:
    OcrService.extractText(file)
    → LlmReceiptParser.parseReceiptText(ocrText)
  else if PDF:
    LlmReceiptParser.parseReceipt(file)
→ MatcherService.matchStore(...)
→ MatcherService.matchProductsToPurchases(...)
→ return ResScanResult
```

Submit path:

```text
POST /api/receipts/submit
→ resolve current user id
→ ReceiptScanService.submitPurchases(userId, purchases)
→ PurchaseService.registerPurchases(...)
→ saved purchases returned
```

## OCR component responsibilities

`OcrService` is responsible for image-specific preprocessing before OCR:
- read image bytes
- inspect EXIF orientation
- rotate/flip when needed
- treat PNGs as screenshots and skip heavy preprocessing
- preprocess camera photos
- optionally save debug images
- pass the prepared image into Tesseract

This service is only part of the image flow. PDFs take a different path.

## Configuration surfaces that affect behavior

These config areas are especially important:
- MongoDB connection properties
- JWT key sources
- OpenAI API key and model setup
- OCR data path, OCR language, OCR debug output path
- allowed CORS origins
- multipart file limits
- rate-limiting filters

See:
- `DEVELOPMENT_SETUP.md`
- `PRODUCTION_SETUP.md`
- `RECEIPT_SCANNING.md`

## Rate limiting

`application.yaml` defines Bucket4j filters for:
- registration
- verification
- login
- receipt scanning
- general protected API traffic
- resend-verification for inactive users

The scan endpoint is intentionally stricter because of LLM/API cost.

## Main change risks

When modifying backend code, pay extra attention to these invariants:
- cookie-based auth must keep working
- inactive vs active user handling must stay distinct
- purchases must remain user-isolated
- receipt image and PDF flows must both remain supported
- OCR/Tesseract runtime assumptions must match deployment docs
- rate limits should remain reasonable for both cost and UX
