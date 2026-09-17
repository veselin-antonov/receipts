# Current State

> Assessed 2026-09-16 by reading both repositories, their unmerged branches, the
> Obsidian notes, and the deployment directory, then by merging both branches
> and running both test suites. See [§Verification](#verification) for what was
> actually executed.

## Summary

The project went dormant around March 2026. Six months of finished, tested work
is sitting unmerged on feature branches in both repositories, and the
documentation that describes it is stranded on those same branches — which is
most of why the docs felt scattered.

Nothing is lost. Both branches are clean fast-forwards.

## Repositories

### `receipts-api`

Spring Boot 3.5.6, Java 21, MongoDB. Also configured: Spring Security with
OAuth2 resource server, Bucket4j rate limiting, Caffeine cache, JavaMail,
Actuator, Lombok, spring-dotenv.

| Ref | State |
|---|---|
| `master` | **`30c7b85` — merged 2026-09-16.** Was `8833451`. |
| `origin/feature/receipt-scanning` | Merged, fast-forward, zero conflicts. |

What is on that branch and not on master:

- The domain refactor into `receipts.{common,products,purchases,stores,scanning}`
- `ReceiptScanService`, `OcrService` (Tess4J), `LlmReceiptParser` (Spring AI →
  OpenAI), `NormalizationService`, `MatcherService`
- `AuthenticatedUserService` and user-scoped purchases — a multi-user data
  isolation fix
- **6 test classes**: `SecurityCorsTest`, `AuthenticatedUserServiceTest`,
  `PurchasesControllerTest`, `PurchaseServiceTest`,
  `ReceiptScanControllerTest`, `MatcherServiceTest`
- The entire reorganised `docs/` set: `INDEX`, `DEVELOPMENT_SETUP`,
  `PRODUCTION_SETUP`, `ARCHITECTURE_AND_FLOWS`, `API_CONTRACTS`,
  `RECEIPT_SCANNING`. These are genuinely good and should survive the migration.

### `receipts-ui`

React 19, Vite 6, React Router 7, Tailwind 4, shadcn/ui, React Hook Form + Zod.

| Ref | State |
|---|---|
| `master` | **`8085295` — merged 2026-09-16.** Was `6920b41`. |
| `origin/feature/receipt-scan-ui` | Merged, fast-forward, zero conflicts. |

What is on that branch and not on master:

- `ReceiptScanPanel` — the upload → review → match → submit flow
- `src/lib/api.js` — shared fetch layer with error handling
- Cookie-auth corrections so session restore works after a refresh
- Vitest + Testing Library, and **7 test files / 15 tests**
- A real 142-line README
- Dev proxy switched from `https://localhost:7002` to `http://`

Note: the branch's `package.json` diff looks enormous, but that is a CRLF → LF
line-ending rewrite of the whole file. The real change is adding vitest,
testing-library, jsdom, and cross-env.

## Deployment

Not a repository. It lives untracked at `~/docker-apps/homeapp/`:

```text
compose.yaml       ui + api + mongo, all reading a shared .env
.env               db creds, SMTP, JWT PEM keys inline, ports
private.pem        JWT signing keys, in the clear
public.pem
db/                the live MongoDB data directory
db/db-backup/      JSON exports dated 20-01-26
ui/                a stale built UI bundle
```

The Obsidian notes refer to a `homeapp-infra` repository. **It does not exist.**
This directory is the whole of the infrastructure, and it is unversioned.

Container state: `receipts-api` exited 143 and `homeapp-ui` exited 0, both six
months ago. `receipts-db` is `Created` but has never started.

## Data

Intact, and more substantial than expected:

| Collection | Records |
|---|---|
| purchases | **717** |
| products | **211** |
| stores | 14 |
| users | 1 |

Plus JSON backups from 20-01-2026. ~992 KB total. This is enough real data to
make the lookup loop immediately useful and to test matching against realistic
name noise.

## What is broken

Six defects found by reading the code. Full detail in
[SPEC.md §7](SPEC.md#7-known-defects-that-block-the-product).

| | Defect | Severity |
|---|---|---|
| D1 | `quantity` / `quantityUnit` accepted by the API, then silently dropped on persist. No unit price is possible. | **Critical** |
| D2 | Prices and dates cross the wire as locale-formatted display strings (`"3,29 лв."`). Cannot be computed on or compared. | **Critical** |
| D3 | `Statistics` entity, repository, and DTO exist; nothing writes or reads them. | High |
| D4 | Product details endpoint and its service method are commented out, orphaned by the domain refactor. | High |
| D5 | `findProductByNameFuzzy` returns `null`. | High |
| D6 | No product, product-detail, or search UI exists. | High |
| D7 | `gradlew` is committed as mode `100644`, not executable. `./gradlew` fails on Linux and macOS. | Medium |
| D8 | **Neither repo runs its tests in CI.** The API builds with `./gradlew build -x test`; the UI workflows only build a Docker image, with no lint or test step. | **High** |
| D9 | The test suite cannot run on a fresh clone. It needs two gitignored files that nothing creates or documents as a test prerequisite. | **High** |
| D10 | `processResources` packages the JWT private key and `dev.env` credentials into the jar, and so into any image built locally. | **High** |
| D11 | Every historical price now renders as euros: the `bg-BG` locale reports EUR, so 12.65 лв is served as `"12,65 €"`. | **Critical** |
| D12 | The product matcher uses whole-string Levenshtein, so it misses on word order and produces confident false positives. | **High** |
| D13 | Store matching is exact-only, and receipts print Cyrillic store names against a Latin catalog. Every scan needs the store set by hand. | **High** |
| D14 | `docker-compose.dev.yml` starts mongo with `--auth` but sets no `MONGO_INITDB_ROOT_*`, so a fresh volume has no user and refuses every connection. | Medium |

Also dead or half-finished: `PurchaseService.enrichParsedPurchases` (commented
out), `storeService` and `productService` injected into `PurchaseService` but
never used.

### On D8 and D9 together

These two compound into something worse than either alone. The tests require
local setup that a fresh clone does not have, and CI skips tests entirely — so
nothing, anywhere, would have noticed if those six test classes had rotted over
the six dormant months. They happened to still pass. That was luck, not a
signal.

D9 in detail: `SecurityCorsTest` is annotated `@ActiveProfiles("dev")`, which
loads `application-dev.yaml`, which resolves `jwt.private-key` to
`classpath:certs/private.pem` — a gitignored path. Once keys exist, the context
still fails, because **12 placeholders in `application.yaml` have no defaults**
(`SERVER_PORT`, `APP_HOST_URL`, and all the `MONGODB_*` and `SMTP_*` values),
and `spring-dotenv` finds no `.env` to supply them. `server.port=${SERVER_PORT}`
then fails to parse as an `Integer`.

The fix is to make the tests hermetic — an `application-test.yaml` with fixed
values, or defaults on the placeholders — rather than to document more manual
setup.

## Documentation, before this repo

Five places, with real duplication and contradictions:

1. Obsidian `Home App/` — vision, module ideas, doc strategy, status review, two
   HTML UI mockups
2. `receipts-api/docs/ROADMAP.md` on master — 300+ lines, Dec 2025, heavily
   duplicating the vault's backlogs
3. `receipts-api/docs/*` on the unmerged branch — the good canonical set
4. `receipts-ui/README.md` plus `.github/copilot-instructions.md`, the latter
   doing real architecture-doc duty
5. Two `CHANGELOG.md` files

Stale facts now corrected: the vault still gives repository paths as
`D:/Documents/ReceiptsApp/...` from an old Windows machine, and references the
non-existent `homeapp-infra` repo. The Current Status Review claims the docs
were cleaned up — true, but only on a branch that was never merged.

## Verification

Run 2026-09-16 after merging both branches.

| Check | Result |
|---|---|
| `receipts-api` merge | Fast-forward to `30c7b85`, zero conflicts |
| `receipts-ui` merge | Fast-forward to `8085295`, zero conflicts |
| API compiles on Java 25 | **Pass** |
| API test suite | **Pass — 12 tests, 0 failures**, across all 6 classes |
| UI dependency install | Pass (`npm ci`; reports 18 audit vulnerabilities, 12 high) |
| UI test suite | **Pass — 7 files, 15 tests, 0 failures** |

The API run needed a JDK 25 container, since this host has no JVM installed, and
it needed the two files described under D9 to be created first.

## End-to-end run, 2026-09-17

The gap open since June is now closed. The full stack ran and a receipt went
through the whole pipeline.

| Step | Result |
|---|---|
| `docker compose -f docker-compose.dev.yml up -d` | mongo, mailhog, ui all start |
| MongoDB auth | Works, once `MONGO_INITDB_ROOT_*` is supplied (D14) |
| Backup restore | 1 user, 211 products, 14 stores, 717 purchases |
| API boot, Java 25, native | **Started in 3.23 s** |
| Unauthenticated access | All endpoints 401; rate-limit headers present |
| Register → verify → login | 201 → 200 → `JWT` cookie, `ACTIVE_USER`, HttpOnly/Secure/SameSite=Strict |
| Restored data through Spring Data | 211 products and 14 stores deserialize correctly; a purchase maps product, store, date and discount |
| Tesseract `eng+bul` | Reads Bulgarian cleanly |
| `POST /api/receipts/scan` | **HTTP 200 in 24 s**, 5 line items parsed |

What the scan got right: the date, all five prices, and every quantity with its
unit — including `1,240 x 2,49` correctly read as 1.24 KILOGRAM. Product
matching found good candidates for two of five rows.

What it got wrong is recorded as D11, D12 and D13.

## Confidence gaps

Still unverified, and each is a plausible source of trouble:

- **A real receipt.** The end-to-end run used a *synthetic* Bulgarian receipt,
  clean and machine-rendered. Real receipts are crumpled thermal paper, shot at
  an angle in bad light. OCR quality on those is still unknown, and it is the
  single biggest remaining unknown in the capture loop.
- **The PDF path.** Only the image path was exercised. PDFs take a different
  route entirely ([ADR-0004](adr/0004-ocr-plus-llm-parsing.md)).
- **`POST /api/receipts/submit`.** The scan was reviewed but not submitted, so
  the write path has not been run against real parsed data.
- **The UI.** Never opened in a browser. `ReceiptScanPanel` is covered by tests
  but has not been driven by hand.
- **The published container images.** The `:dev` tags on GHCR are six months old
  and predate the scanning merge, so running the existing `compose.yaml` would
  test the *old* code. New images must be built from the merged source.
- **The original account's password.** Unknown, so the 717 restored purchases
  were verified by temporarily reassigning a single document to a test user and
  reverting it, rather than by logging in as their owner.
