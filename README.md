# Receipts UI

[![Preview Builds](https://github.com/veselin-antonov/receipts-ui/actions/workflows/preview.yml/badge.svg)](https://github.com/veselin-antonov/receipts-ui/actions/workflows/preview.yml)
[![Release](https://github.com/veselin-antonov/receipts-ui/actions/workflows/release.yml/badge.svg)](https://github.com/veselin-antonov/receipts-ui/actions/workflows/release.yml)

React/Vite frontend for the Home App / ReceiptsApp project.

The UI lets users authenticate, browse purchases, upload receipts, review parsed purchase rows, manually match products, and submit purchases to the backend.

## Documentation model

To avoid duplicate and stale docs:

- Product vision, cross-repo flows, and status reviews live in Obsidian under `Home App/`.
- Backend setup, API behavior, OCR/Tesseract/Spring AI config, and endpoint details live in `receipts-api`.
- UI setup, scripts, environment variables, and UI-specific behavior live in this repo.

This README intentionally references backend endpoints but does not duplicate backend internals.

## Tech stack

- Vite
- React 19
- React Router 7
- Tailwind CSS 4
- shadcn/Radix-style UI primitives
- Vitest + Testing Library
- ESLint + Prettier

## Local setup

Install dependencies:

```bash
npm install
```

Run dev server:

```bash
npm run dev
```

Build:

```bash
npm run build
```

Run tests:

```bash
npm test -- --run
```

Lint:

```bash
npm run lint
```

## Environment

The API base URL is configured by `VITE_API_URL` and defaults to `/api`.

```bash
export VITE_API_URL='/api'
```

For local development with Vite proxy/backend setup, keep the UI and API aligned so browser calls reach the backend's `/api/*` routes.

## Auth convention

The backend sets an HttpOnly JWT cookie after login. The UI should call backend APIs with same-origin credentials and should not store JWT bearer tokens in localStorage.

Important behavior:

- `POST /api/auth/token` logs in and returns token lifetime seconds.
- `GET /api/auth/status` verifies whether the cookie session is still valid.
- A `403` login response can mean the account exists but is inactive/unverified.
- Inactive users are navigated to `/not-verified`.
- Verification resend uses `POST /api/users/resend-verification`.

## Main UI areas

```text
src/components/auth/       auth provider and route guards
src/components/login/      login form behavior
src/components/receipts/   receipt scan/review/submit panel
src/components/ui/         shared UI primitives
src/lib/                   API and fetch hooks
src/pages/                 route pages
```

## Receipt scanning UI flow

```text
Purchases page
→ choose receipt file
→ POST /api/receipts/scan
→ render parsed purchases for review
→ manually match or edit product rows
→ POST /api/receipts/submit
→ clear review state
→ refresh purchases list
```

The backend owns OCR, parsing, normalization, matching, and persistence. The UI owns file upload, review/edit state, manual matching interactions, submit behavior, and user-facing errors.

## Backend endpoints used by the UI

```text
POST /api/auth/token
GET  /api/auth/status
POST /api/users/register
POST /api/users/verify
POST /api/users/resend-verification
GET  /api/products
GET  /api/purchases
POST /api/purchases
POST /api/receipts/scan
POST /api/receipts/submit
```

Keep endpoint contracts in the `receipts-api` docs/README and update this list only when the UI starts or stops using an endpoint.

## Verification before committing UI changes

```bash
npm test -- --run
npm run lint
npm run build
git diff --check
```

Latest verified result in this branch:

- Tests: 7 files passed, 15 tests passed.
- Lint: 0 errors, 4 Fast Refresh warnings.
- Build: Vite build completed.

## Known lint warnings

Current warnings are from `react-refresh/only-export-components` in shared UI/auth files. They are not build blockers, but can be cleaned up later by moving constants/helpers out of component modules.
