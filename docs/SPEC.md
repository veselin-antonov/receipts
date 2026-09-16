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
- Existing data: **717 purchases, 211 products, 14 stores, 1 account**, spanning
  roughly 14 months.
- Growth: on the order of a few hundred purchases per year.

The dataset is *tiny*. This is a design asset, not a limitation:

- The entire product/price dataset fits comfortably in a phone's memory, which
  makes the offline cache in [ADR-0005](adr/0005-pwa-offline-lookup.md) trivial.
- Price statistics can be computed on demand from raw purchases. There is no
  need for a maintained statistics cache, and we will not build one
  ([§7.3](#73-statistics-are-computed-not-stored)).

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
├── api/     Spring Boot 3.5.6 · Java 25 · MongoDB
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
| `price` | What was paid |
| `date` | When |
| `discountAmount` | Discount applied |
| `quantity` | **Missing — see D1** |
| `quantityUnit` | **Missing — see D1** |

### `users`

`email`, `password` (PBKDF2), `isActive`.

### `statistics`

Exists as a collection, an entity, and a repository. **Nothing has ever written
to it.** It is deleted under [§7.3](#73-statistics-are-computed-not-stored).

---

## 7. Known defects that block the product

These were found by reading the code, not by running it. They are listed here
because **the lookup loop cannot be built correctly until they are fixed** — it
is not a matter of polish.

### D1 — Quantity is accepted and silently discarded *(critical)*

`ReqPurchase` carries `quantity` and `quantityUnit` (an enum of `PIECE`, `GRAM`,
`KILOGRAM`, `MILLILITER`, `LITER`). The OCR/LLM parser extracts both.
`ResScanPurchase` returns both for review.

And then `PurchaseService` persists this:

```java
new Purchase(userId, product, reqPurchase.price(), reqPurchase.date(),
             store, reqPurchase.discountAmount());
```

No quantity. No unit. Both `registerPurchase` and `registerPurchases` drop them,
because the `Purchase` entity has no fields to hold them.

**Why this is fatal to the product:** without quantity and unit there is no
*unit price*, and without unit price the core question is unanswerable. "Milk,
3.29" is meaningless — 3.29 for one litre is a good price, 3.29 for 500 ml is a
bad one. Every price comparison across differently-sized packages is currently
wrong.

This also means the **717 existing purchases have no quantity data**, and it
cannot be recovered from the database. See [ROADMAP](ROADMAP.md) M2 for the
backfill decision.

### D2 — Prices cross the wire as formatted strings *(critical)*

`ResPurchase.price` is a `String`, produced by:

```java
NumberFormat.getCurrencyInstance(bgLocale).format(price)
```

So the API emits `"3,29 лв."` rather than `3.29`. Dates get the same treatment:
`formatDate` emits `"15.06.26 г."`, while `ReqPurchase` *parses* `dd/MM/yyyy` —
the wire format is not even self-consistent.

**Why this blocks the lookup loop:** you cannot compute an average, find a
minimum, sort by price, or compare two prices from locale-formatted display
strings. The offline cache would be caching text, not data. Server-side display
formatting also hard-codes one locale and one currency into the API.

**Rule going forward:** the API emits data, the UI formats it. Money is a
number plus a currency code. Dates are ISO-8601. No exceptions.

### D3 — Statistics are never computed

The `Statistics` entity, `StatisticsRepository`, and `ResStatistics` DTO all
exist. A grep across the whole main source tree finds only their own
definitions — no service reads or writes them. The code that *would* have
computed them is commented out inside `ProductService`.

### D4 — The product details endpoint is commented out

`ProductController.getDetailsById` and `ProductService.getDetailsById` are both
commented out behind `// TODO - Reimplement product details page`. The dead code
references `Purchase.getIsDiscounted()` and `Store.getName()` — methods that no
longer exist — so it worked before the domain refactor and was orphaned by it.

Its stat labels were hard-coded Bulgarian strings (`"Средна Цена"`,
`"Най-ниска Цена"`, `"Обичаен Магазин"`), which is the same
formatting-in-the-backend mistake as D2.

### D5 — Fuzzy product search is a stub

```java
public Product findProductByNameFuzzy(String name) {
    return null;
}
```

UC-1 depends entirely on this working.

### D10 — Secrets are packaged into the jar

`processResources` copies `src/main/resources/certs/private.pem` (the JWT
signing key) and `dev.env` (Mongo, SMTP, and OpenAI credentials) into the build
output, and therefore into the jar and any image built from it. Gitignored, so
git is clean — but `./gradlew publishImage` from a developer machine would push
them to GHCR. Details and fix in
[DEV_SETUP.md](DEV_SETUP.md#d10--secrets-are-packaged-into-the-jar).

### D6 — There is no lookup UI at all

The UI has seven pages: Login, Register, VerifyAccount, SendVerification,
NotVerified, Root, Purchases. There is no product list, no product detail, and
no search beyond filtering the purchases table.

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
- **F1.4** Submitted purchases are saved **with quantity and unit** (blocked on
  D1).
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

Auth behaviour worth remembering: `POST /api/auth/token` returning **403** means
the credentials were *correct* but the account is unverified — not that login
failed.

Deferred: password reset, refresh tokens and logout, account lockout, RBAC, MFA.
None of these block the two loops. See [ADR-0003](adr/0003-cookie-jwt-auth.md).

---

## 9. Cross-cutting rules

### 9.1 The wire format carries data, not presentation

Consequence of D2. Money is `{ "amount": 3.29, "currency": "BGN" }` or a plain
number with the currency known from context — never a formatted string. Dates
are ISO-8601. Formatting and localisation happen in the UI.

### 9.2 Everything is scoped to the user

There is no code path that reads or writes a purchase without resolving the
current user through `AuthenticatedUserService` first.

### 9.3 Statistics are computed, not stored

Dropping the `statistics` collection. With 717 purchases, a MongoDB aggregation
computes every statistic in this spec faster than a cache could be invalidated
correctly. A stale-cache bug that quietly reports a wrong "lowest price" would
undermine the one thing the product is for.

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

- **Java** 25 (Gradle toolchain), Spring Boot 3.5.6, Gradle 9.3.1. Package root
  `dev.vasoft.homeapp` *(rename pending — see [§12](#12-open-questions))*.
- **Receipt parsing** uses Spring AI 1.1.2 and Tess4J 5.14.0. The build resolves
  from Spring milestone and snapshot repositories, which is a reproducibility
  risk — see Q6.
- **API** under `/api`, plural nouns, `ProblemDetail` for errors with a stable
  machine-readable `error` code.
- **Commits** follow Conventional Commits, as the existing history does.
- **Versioning** from the `VERSION` file; `CHANGELOG.md` in Keep a Changelog
  format.
- **Tests** live beside the code they cover. The API has 6 test classes and the
  UI has 7 test files / 15 tests. That is the floor, not the ceiling — and
  neither is currently run by CI (D8).

---

## 12. Open questions

Decisions deliberately deferred. Each needs an answer before the milestone that
depends on it.

- **Q1 — Currency.** All existing data is in BGN, and prices are formatted with
  a `bg-BG` locale. If Bulgaria has moved to the euro, the price history spans a
  currency change, and comparing a 2025 lev price against a 2026 euro price is
  meaningless without conversion. Needed before F2.2. *Blocks M3.*
- **Q2 — Quantity backfill.** The 717 existing purchases have no quantity or
  unit and it cannot be recovered automatically. Options: leave them
  unit-price-less and show unit price only for new data; bulk-assign a default
  unit per product; or re-scan nothing and accept the gap. *Blocks M2.*
- **Q3 — UI redesign.** The Dashboard Shell mockup is scrapped. The lookup loop
  needs a mobile-first design that does not exist yet. *Blocks M4.*
- **Q4 — Package rename.** `dev.vasoft.homeapp` is misleading now that Receipts
  has left the Home App umbrella. Invasive, zero functional gain, safe to defer.
- **Q5 — Receipt image retention.** Currently images are parsed and dropped.
  Keeping them would allow re-parsing with a better model later, at a storage
  and privacy cost.
- **Q6 — Snapshot dependencies.** `build.gradle` resolves from
  `repo.spring.io/milestone`, `repo.spring.io/snapshot`, and the Central Portal
  snapshot repository. Snapshot artifacts can be republished or withdrawn, so a
  build that works today may not work in six months — which is roughly how long
  this project was dormant. Pin to release versions before relying on
  reproducible builds.
