# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
The api and the ui are versioned together: one [`VERSION`](VERSION), one
release, both images tagged with it ([ADR-0002](docs/adr/0002-single-repo.md)).
Releases up to 0.0.6 were separate; their notes are in
[`api/CHANGELOG.md`](api/CHANGELOG.md) and [`ui/CHANGELOG.md`](ui/CHANGELOG.md).

## [Unreleased]

### Repository

- `receipts-api` and `receipts-ui` merged into this repository as `api/` and
  `ui/`, with full history (ADR-0002)
- One `VERSION` and one `CHANGELOG.md` for both
- CI rewritten with path filters: an api change runs only the api checks and
  image, a ui change only the ui's. Both now run on pull requests; the api had
  no pull request CI before
- The upload-limit test (`scripts/test-upload-limits.sh`) runs in CI whenever
  the nginx config, the ui Dockerfile or the api's `application.yaml` changes
- The deployment compose file is versioned in `deploy/`
- Dev stack (`api/docker-compose.dev.yml`): the ui container now reaches the
  natively running api (it proxied to itself and returned 502), `UI_TAG`
  selects a pull request's `pr-<n>` preview image, mongo is pinned to 8.2, and
  `dev.env` is gone in favour of interpolating from `api/.env`

### API

#### Added
- `currency` (`BGN` | `EUR`) stored on every purchase; optional on requests,
  defaulting to `EUR`
- `LegacyCurrencyBackfill`: at startup, tags currency-less purchases `BGN`, and
  fails startup rather than guess for one dated 2026-01-01 or later
- `LegacyDataGuard`: refuses to start against unmigrated purchase data, i.e.
  dates not at midnight UTC (they would read as the previous day) or purchases
  without a `userId` (they would be invisible), and points at
  `migrate-backups.py`. Runs before the currency backfill

#### Changed
- **Breaking:** purchase responses carry numeric `priceEur` and
  `discountAmountEur` (full precision, converted at 1 EUR = 1.95583 BGN)
  instead of the formatted `price` string and `discountAmount`
- **Breaking:** every date on the wire is ISO-8601 in both directions. Purchase
  requests (`POST /api/purchases`, `/api/receipts/submit`) now take
  `yyyy-MM-dd` and reject `dd/MM/yyyy` with 400. One global Jackson setting
  replaces the per-DTO `@JsonFormat` patterns
- `POST /api/purchases` resolves product and store the same way as submit: a
  submitted id wins, the name is the fallback
- Verification token expiry is an `Instant`, not a zoneless `LocalDateTime`
- Dates are stored in UTC regardless of the JVM's timezone: `MongoConfig`
  switches to the MongoDB driver's java.time codecs, so a purchase date is
  always midnight UTC of its day. The JVM's zone now affects only log
  timestamps; `docker-compose.yml` sets `TZ` (default `Europe/Sofia`) for that

#### Fixed
- A null parser result gave a 500; it now takes the 422 "no purchases" path
- The OCR debug image path used the client's filename unsanitised, so a name
  with path separators could write outside the debug directory
- Raw OCR text of a receipt was logged at DEBUG, which the dev profile enables;
  it is now TRACE
- Docs gave a 10MB upload limit and `gpt-5-mini`; the defaults are 25MB
  (`MAX_UPLOAD_MB`) and `gpt-6-luna`
- `POST /api/purchases` with ids and no names created a product and a store
  with empty names
- Legacy BGN prices were served as euros, ~2x overstated, after the JDK's
  bg-BG locale switched to EUR (D11)

#### Removed
- `Formatter`; formatting belongs to the UI (D2)
- `ResParsedPurchase` and `PurchaseMapper.toResParsedPurchases`, unused

### UI

#### Added

- `src/lib/format.js`: `formatEur` and `formatDate`. The API now sends numbers
  and ISO dates, so the UI owns rounding and localisation

#### Changed

- The purchases table renders `priceEur` / `discountAmountEur` and ISO dates
  from the API's new wire format (requires the matching API change)
- Price inputs in the manual form and the scan review are labelled `(€)`
- Dates go to the API as ISO `yyyy-MM-dd`, built by `toIsoDate` in
  `src/lib/format.js` from the local calendar day (not `toISOString()`, which
  shifts to UTC). The scan panel passes the scan's ISO date through untouched
  instead of rewriting it to `dd/MM/yyyy`
- The manual purchase form sends `productName`, `storeName` and a numeric
  `price`, which is what the API reads; it no longer sends a stale
  `Authorization: Bearer` header from `localStorage`

#### Fixed

- A failed rescan left the previous receipt's rows on screen, submittable as
  if they belonged to the new one; scanning now clears them first
- A review price typed with a comma (`2,49`) became `NaN`, sent as `null`, and
  the API would store 0. `parseDecimal` in `format.js` accepts either
  separator, and an unreadable amount blocks the submit with an error
- README and copilot instructions: test counts, and the API proxy is plain
  HTTP (the API has had no TLS since 0.0.6)
- The manual purchase form sent `product` / `store`, which the API does not
  read, so every manual entry would have created a nameless product and store
- The discount icon read `purchase.discount`, which the API never sent, so it
  was always off; it now reads `discountAmountEur`

## [0.0.6] - 19.01.2026 (api) / 03.01.2026 (ui)

Last separate releases. See [`api/CHANGELOG.md`](api/CHANGELOG.md#006---19012026)
and [`ui/CHANGELOG.md`](ui/CHANGELOG.md#006---03012026).
