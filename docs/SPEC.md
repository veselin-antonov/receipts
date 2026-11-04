# Receipts — Specification

> Status: active · Last updated: 2026-09-16
> This is the document we build against. If the code disagrees with this, one of
> the two is wrong, and we fix it deliberately rather than by drift.

---

## 1. Product definition

Receipts is a **personal grocery price tracker**.

It exists to answer one question, asked while standing in a store holding an item:

> **Is this a good price?**

Everything else in the product exists to make that answer possible and accurate.

### The two loops

The product is two loops running at very different frequencies:

| | **Capture loop** | **Lookup loop** |
|---|---|---|
| Where | At home, at a desk | In a store, on a phone |
| Frequency | Occasionally, after shopping | Constantly, while shopping |
| Effort budget | Minutes are fine | **Seconds** |
| What it does | Receipt → parsed rows → reviewed → saved | Search a product → verdict on a price |
| Purpose | Feeds the lookup loop | **The reason the product exists** |

This asymmetry is the most important thing in this spec. The capture loop is
overhead that the user tolerates in order to get the lookup loop. A design
decision that makes capture slightly nicer at the cost of making lookup slower
is the wrong trade, every time.

### Why it is standalone

Receipts is deliberately **not** a module inside the Home App suite. The lookup
loop is latency-critical and happens one-handed in a shop. Navigating a
multi-module household app to reach it defeats the purpose. See
[ADR-0001](adr/0001-standalone-from-home-app.md).

---

## 2. Users and scale

- **One real user today.** Design for single digits, not for scale.
- Existing data: **several hundred purchases, a few hundred products, a dozen
  or so stores, one account**, spanning roughly 14 months.
- Growth: on the order of a few hundred purchases per year.

The dataset is *tiny*. This is a design asset, not a limitation:

- The entire product/price dataset fits comfortably in a phone's memory, which
  makes the offline cache in [ADR-0005](adr/0005-pwa-offline-lookup.md) trivial.
- Price statistics can be computed on demand from raw purchases. There is no
  need for a maintained statistics cache, and we will not build one
  ([§9.3](#93-statistics-are-computed-not-stored)).

Multi-user isolation still matters and is already implemented — every purchase
is scoped to a `userId` — but multi-user *features* (sharing, households,
permissions) are out of scope.

---

## 3. Non-goals

Explicitly out of scope. If one of these starts creeping in, it needs a decision
record first.

- **Budgeting or expense reporting.** This is not a finance app. It tracks unit
  prices of things, not what you can afford.
- **Household records.** Manuals, warranties, appliances, wardrobe — those are
  Home App modules and stay there.
- **Multi-tenant SaaS.** No billing, no orgs, no onboarding funnel.
- **Shared or family accounts.** Single-user per account, for now.
- **A general receipt archive.** We keep the extracted data, not the scanned
  images, beyond what parsing needs.

---

## 4. Primary use cases

Ranked by how often they happen. Build quality should follow this ranking.

### UC-1 — Check a price in a store *(primary)*

The user is in a shop, phone in one hand, product in the other.

1. Opens the app. It is **already usable** — search focused, no login wall, no
   spinner, works with no signal.
2. Types a few characters of a product name, possibly misspelled, possibly in
   Bulgarian or English.
3. Sees matching products ranked by relevance.
4. Taps one and gets a **verdict**, not a data dump: what this usually costs,
   the best price ever paid and where, and whether the shelf price in front of
   them is good.

### UC-2 — Capture a receipt

At home after shopping: upload a photo or PDF, review the parsed rows, correct
what the parser got wrong, submit. Covered in [§8.1](#81-f1--receipt-capture).

### UC-3 — Add a purchase by hand

A single item, no receipt. Must stay fast: prefill the last store and today's
date.

### UC-4 — Browse and correct history

List, search, and page through past purchases. Fix a bad parse after the fact.
Merge two products that are really the same thing.

---

## 5. System architecture

### Shape

A single repository, two deployables, one database.

```text
receipts/
├── api/     Spring Boot 4.1.1 · Java 25 · MongoDB
├── ui/      React 19 · Vite · Tailwind 4 · shadcn/ui
└── docs/    this specification
```

Rationale for one repo: [ADR-0002](adr/0002-single-repo.md).

### Runtime

```text
        browser / phone (PWA)
                 │
                 ▼
        ui  (nginx, serves SPA, proxies /api → api)
                 │
                 ▼
        api (Spring Boot)
                 │
                 ▼
        mongodb
```

Deployed with Docker Compose. Images publish to GHCR as `receipts-api` and
`receipts-ui` from path-filtered workflows. TLS terminates externally.

### API surface

All routes are under `/api`. The active surface:

```text
POST /api/auth/token                 exchange credentials for a JWT cookie
GET  /api/auth/status                remaining validity of the current session

POST /api/users/register
POST /api/users/verify
POST /api/users/resend-verification

GET  /api/products
POST /api/products?name=...
GET  /api/stores

GET  /api/purchases?pageNumber&pageSize&searchQuery
POST /api/purchases

POST /api/receipts/scan              multipart, field `file`
POST /api/receipts/submit            reviewed rows
```

To be added for the lookup loop ([§8.2](#82-f2--price-lookup)):

```text
GET  /api/products/search?q=...      fuzzy, alias-aware product search
GET  /api/products/{id}/prices       price history and statistics
GET  /api/lookup/snapshot            whole dataset, for the offline cache
```

---

## 6. Domain model

Stored in MongoDB. Collections and their meaningful fields:

### `products`

| Field | Notes |
|---|---|
| `canonicalName` | The display name |
| `normalizedCanonicalName` | Lowercased, accent-folded, for matching |
| `aliases` | Other names seen on receipts for this same product |
| `normalizedAliases` | Match forms of the above |
| `iconID` | Display icon |

Aliases are how OCR noise and store-specific naming collapse onto one product.
They are central to both matching and search, and they are currently unused by
any write path.

### `stores`

`canonicalName`, `normalizedCanonicalName`, `iconID`.

### `purchases`

| Field | Notes |
|---|---|
| `userId` | **Every read and write is scoped to this. Non-negotiable.** |
| `product` | Reference |
| `store` | Reference |
| `price` | What was paid, in `currency`, for the whole line, so the unit price is `price / quantity` ([F2.0](#f20-note-on-how-price-is-recorded)). Never rewritten |
| `currency` | `BGN` or `EUR`, stored on every row — see [§9.5](#95-currency-handling) |
| `date` | When |
| `discountAmount` | Discount applied, in `currency` |
| `quantity` | How much, in `quantityUnit`; required on every new purchase |
| `quantityUnit` | `PIECE`, `GRAM`, `KILOGRAM`, `MILLILITER` or `LITER` |
| `quantityAssumed` | `true` only on a legacy purchase, whose quantity was never recorded and was set by the quantity migration |

Purchases recorded before quantities existed (Q2) carried the unit price, with
the unit in the product name (`Лук (цена за кг)`). `LegacyQuantityMigration`
gives each one quantity 1 in its product's unit, so its price stays the unit
price, and marks it `quantityAssumed`. The unit per product is guessed from the
name (per kg → `KILOGRAM`, per litre → `LITER`, anything else, a stated pack
size included, → `PIECE`) and reviewed by a person before anything is written:
the first start writes a proposal and changes nothing, and a reviewed copy of
it is applied on a later start ([deploy/README.md](../deploy/README.md), "One-off
data migrations"). Product names are left as they are.

### `users`

`email`, `password` (PBKDF2), `isActive`.

---

## 7. Known defects that block the product

Known defects are tracked as bugs in the project's planning workspace, under
their D-numbers (D1, D12, D13, …), which code comments and the other docs use
to refer to them.

---

## 8. Feature specifications

### 8.1 F1 — Receipt capture

**Status:** implemented on `origin/feature/receipt-scanning` and
`origin/feature/receipt-scan-ui`, never merged, never verified end to end.

The pipeline:

```text
upload file
→ validate type and size (≤10 MB; JPEG, PNG, GIF, WebP, PDF)
→ route by type
     image → OcrService (Tesseract) → LlmReceiptParser.parseReceiptText
     pdf   → LlmReceiptParser.parseReceipt   (vision/document path)
→ NormalizationService normalizes raw names
→ MatcherService suggests store and product matches
→ return review payload (never persists)
→ user reviews and corrects in the UI
→ POST /api/receipts/submit persists, scoped to the user
```

Images go through OCR because vision models misread digits on receipt photos;
PDFs skip OCR because they are already clean text. Parsing never writes
directly — `/submit` is the only persistence point, because OCR and LLM output
is not trustworthy enough to save unreviewed.

Requirements to consider F1 done:

- **F1.1** A real image receipt and a real PDF receipt both parse end to end.
- **F1.2** Parsed rows are reviewable and correctable before saving.
- **F1.3** Unmatched products and stores are surfaced clearly, not silently
  created with a bad name.
- **F1.4** Submitted purchases are saved **with quantity and unit**.
- **F1.5** Scan endpoints are rate-limited more aggressively than normal traffic,
  because each call costs money at the LLM provider.

### 8.2 F2 — Price lookup

**Status:** does not exist. This is the product.

#### F2.1 Search

- Matches on canonical name **and** aliases.
- Tolerant of partial input, typos, and missing accents.
- Works for Bulgarian and English input, including mixed.
- Ranks by relevance, then by how recently and often the user buys the thing.
- Fast enough to run on every keystroke.

#### F2.0a Unit price needs package size, not just quantity

`quantity` and `quantityUnit` make unit price computable, but only make it
*comparable* when the unit is absolute. Weighed goods are fine: `1,240 x 2,49`
is 2.49 per kilogram, and that compares against any other kilogram price.

`PIECE` is not absolute. Two pieces of yoghurt at 1.45 each tells you nothing
about value unless you know whether the pot is 400 g or 500 g, and receipts
usually do not print it. The existing catalog works around this by smuggling
the unit into the name — `Лук (цена за кг)`, `Лалета 7 бр.`, `банани на кг` —
which is unsearchable and unusable for arithmetic.

So the honest scope is:

- **per kg / per litre** comparisons: correct once D1 is fixed
- **per piece** comparisons: correct only within the same package size

Making the verdict trustworthy for packaged goods needs a `packageSize` on the
product (amount plus unit), set once per product rather than per purchase.
Until then the UI must not present a per-piece comparison as though it were
per-kilogram — showing a confidently wrong verdict is worse than showing none.

#### F2.0 Note on how price is recorded

The scan pipeline returns `price` as the **line total**, not the unit price: a
row of `2 x 1,45` comes back as `price: 2.90, quantity: 2.0`. Unit price is
therefore `price / quantity`, and any unit-price calculation must divide before
comparing. Getting this backwards would silently double or halve comparisons.

#### F2.2 The verdict

A product's page answers "is this a good price?" *before* it shows any history.
Required elements:

- **Typical price** — what this normally costs.
- **Best price ever paid**, with the store and date.
- **Last paid price**, with store and date.
- **Usual store** for this product.
- **Per-store comparison** — "Lidl 3.29 · Kaufland 3.49 · Billa 3.60".
- **Unit price** throughout, normalised per kg / per litre / per piece, so that
  differently-sized packages are comparable *(blocked on D1)*.

#### F2.3 Price check

The user types the shelf price they are looking at, and the app returns a plain
verdict — good / normal / expensive — against their own history for that
product. This is the single highest-value interaction in the app: it converts
stored data into a decision without making the user interpret a chart.

#### F2.4 History

Below the verdict: purchases in reverse-chronological order (price, unit price,
store, date), and enough of a trend to see whether the item is getting more
expensive over time.

#### F2.5 Offline

Everything in F2.1–F2.4 works with no network connection. See
[ADR-0005](adr/0005-pwa-offline-lookup.md).

### 8.3 F3 — Purchase management

List, search, paginate, and correct past purchases. All reads and writes scoped
to `userId`. Manual entry prefills last store and today's date.

### 8.4 F4 — Catalog and normalization

- Normalization folds case, accents, and punctuation into a match form.
- Matching maps a raw receipt string onto an existing product or store,
  or proposes creating a new one.
- Confirming a match on a raw name should **record it as an alias**, so the same
  receipt wording matches automatically next time. This is the mechanism by
  which parsing accuracy improves with use, and it is currently not wired up.
- Merging two products that turn out to be the same thing must be possible, and
  must move their purchases.

### 8.5 F5 — Accounts

Implemented and working: registration, emailed verification token (24 h), login
issuing an RSA-signed JWT in an HttpOnly / Secure / SameSite=Strict cookie,
active vs inactive token scopes, Bucket4j rate limits per IP and per user.

Auth behaviour worth remembering: `POST /api/auth/token` answers an unverified
account with 403 and `error: ACCOUNT_NOT_VERIFIED` — the credentials were
*correct*. A 403 without that code, such as a CORS rejection, says nothing about
the account.

Deferred: password reset, refresh tokens and logout, account lockout, RBAC, MFA.
None of these block the two loops. See [ADR-0003](adr/0003-cookie-jwt-auth.md).

---

## 9. Cross-cutting rules

### 9.1 The wire format carries data, not presentation

Consequence of D2. Money is a plain number whose currency is part of the field
name — `priceEur`, `discountAmountEur` — never a formatted string, and never a
bare `price` a client could read in the wrong currency. Responses carry EUR
only; the stored currency is a server-side detail (§9.5). Requests carry
`price` plus an optional `currency`, defaulting to EUR.

Dates and times are ISO-8601 in both directions: a calendar date is
`yyyy-MM-dd`, a moment is a UTC instant such as `2026-09-23T18:15:10Z`.
This is one global setting (`spring.jackson.datatype.datetime.write-dates-as-timestamps: false`),
never a per-DTO `@JsonFormat` pattern, and anything else in a request is a
400 rather than a guess. Server-side moments are `Instant`, never a zoneless
`LocalDateTime`.

Storage is UTC by construction: a calendar date is stored as midnight UTC of
that day, through the MongoDB driver's codecs (`MongoConfig`), and moments as
UTC instants. No stored value depends on the JVM's timezone, so the server can
run in local time and its only effect is on log timestamps. A timezone is
applied only where one is genuinely needed — displaying a moment, or deciding
which day a moment falls on — and then explicitly (`Europe/Sofia` for a shop),
never implicitly through the server's default.

Formatting, rounding and localisation happen in the UI, in one module
(`src/lib/format.js`). The UI builds a request date from the picked local
calendar day, never from `toISOString()`, which shifts it to UTC.

### 9.2 Everything is scoped to the user

There is no code path that reads or writes a purchase without resolving the
current user through `AuthenticatedUserService` first.

### 9.3 Statistics are computed, not stored

There is no `statistics` collection. With hundreds of purchases, a MongoDB aggregation
computes every statistic in this spec faster than a cache could be invalidated
correctly. A stale-cache bug that quietly reports a wrong "lowest price" would
undermine the one thing the product is for.

### 9.5 Currency handling

The dataset spans Bulgaria's euro changeover, so it is **permanently mixed**:

| | Currency | Count today |
|---|---|---|
| Legacy purchases (to Oct 2025) | **BGN** | all of them |
| New purchases | **EUR** | grows from here |
| Display, everywhere | **EUR** | — |

Rules:

1. **Store the currency on every purchase.** Never infer it from the date. A
   date-based rule would be a second hidden assumption of exactly the kind that
   caused D11, and it would break the moment a backdated row is entered.
2. **Never rewrite a recorded amount.** `12.65` BGN is what was paid; it stays
   `12.65` with `currency: BGN`. Converting in place destroys the original
   figure and makes the error unrecoverable if the rate is ever wrong.
3. **Convert at read time, to EUR, using the fixed rate.** BGN was pegged, and
   adoption used the same irrevocable rate: **1 EUR = 1.95583 BGN**. This is a
   constant, not a market lookup — conversion is deterministic and reproducible.
4. **Compare only within one currency.** The lookup loop compares prices across
   three years that straddle the changeover. Convert to EUR *first*, then
   compare. A BGN figure compared against a EUR figure is wrong by ~2x, which is
   large enough to invert a "good price" verdict.
5. **Round only at display.** 12.65 BGN is 6.4678… EUR. Keep full precision
   through statistics and comparison; round in the UI. Rounding early and then
   averaging accumulates error across hundreds of rows.
6. **Watch the scan path.** During the dual-display transition Bulgarian
   receipts print both BGN and EUR. The parser must be explicit about which
   figure it takes, and record the matching currency. Taking the wrong column
   silently injects ~2x errors into new data.

   This is confirmed, not theoretical. A real receipt from 03.10.2025 prints:

   ```text
   ОБЩА СУМА ЛВ          7.49
   ОБЩА СУМА В ЕВРО      3.8x
   ВАЛ. КУРС 1 ЕВРО = 1.95583 ЛВ
   ```

   Two totals and the rate, on one receipt, from *before* adoption. The parser
   currently has no concept of currency, so which figure it picks is
   undefined. Useful corollary: receipts state the rate themselves, so a parser
   that reads it can verify the constant rather than trusting a hard-coded one.

Migration for the existing rows: set `currency: BGN` on all of them. They predate
the changeover, so this is unambiguous — but it must be an explicit stored
value, not an inferred one.

Implemented 2026-09-23. `LegacyCurrencyBackfill` writes `currency: BGN` onto
every currency-less purchase at startup, before the web server serves
anything, and `migrate-backups.py` writes it explicitly on restore. The
backfill refuses to act on a currency-less row dated 2026-01-01 or later,
since only old code running after the changeover could produce one and it
could be either currency; startup fails instead. The read path likewise throws
on a purchase with no currency rather than assume one. On the wire, a purchase
carries only `priceEur` and `discountAmountEur`, unrounded, and an ISO date.

### 9.4 Parsed data is never persisted unreviewed

`/scan` returns; `/submit` writes. OCR and LLM output is a suggestion.

---

## 10. Non-functional requirements

| | Target | Why |
|---|---|---|
| App open → usable search | < 1 s, offline | UC-1 happens in a shop aisle |
| Search keystroke → results | < 100 ms | It should feel local, because it is |
| Lookup with no network | Fully functional | Signal inside shops is unreliable |
| Receipt scan | < 30 s | Tolerable; it is the overhead loop |
| Scan cost | Bounded by rate limit | Each scan costs real money |
| Purchase list page | < 500 ms | Secondary path |

---

## 11. Conventions

- **Java** 25 (Gradle toolchain), Spring Boot 4.1.1, Gradle 9.7.1. Package root
  `dev.vasoft.homeapp` *(rename pending, Q4)*.
- **Receipt parsing** uses Spring AI 2.1.0-M1, a milestone, and Tess4J 5.14.0.
  Everything resolves from Maven Central; the milestone and its exit condition
  are [ADR-0008](adr/0008-spring-ai-2.1-milestone.md).
- **API** under `/api`, plural nouns, `ProblemDetail` for errors with a stable
  machine-readable `error` code.
- **Commits** follow Conventional Commits, as the existing history does.
- **Versioning** from the root `VERSION` file, shared by api and ui;
  `CHANGELOG.md` at the root in Keep a Changelog format.
- **Tests** live beside the code they cover. The API has 20 test classes / 73
  tests, one of them against a real MongoDB, and the UI has 9 test files / 36
  tests (2026-09-29). `e2e/` drives both images end to end with Playwright.
  That is the floor, not the ceiling. `ci.yml` runs whichever of them a pull
  request needs and reports once, as `ci-summary`.

---

## 12. Open questions

Open questions are tracked in the project's planning workspace, under their
Q-numbers; the answered ones are reflected in the sections above.
