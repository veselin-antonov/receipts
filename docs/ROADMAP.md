# Roadmap

Ordered by dependency, not by ambition. Each milestone ends in something
verifiable.

This replaces `receipts-api/docs/ROADMAP.md` (Dec 2025) and the backlog sections
of the Obsidian notes. Items from those that are still relevant are folded in
below; items that were Home App concerns stay in Obsidian.

---

## M0 — Recover the stranded work

**Goal:** get six months of finished work onto `master` and prove the stack runs.

- [x] Merge `origin/feature/receipt-scanning` into `receipts-api/master` —
      fast-forward to `30c7b85`, zero conflicts
- [x] Merge `origin/feature/receipt-scan-ui` into `receipts-ui/master` —
      fast-forward to `8085295`, zero conflicts
- [x] Delete the stale `receipts-api/docs/ROADMAP.md` — the branch already did
      this; superseded by this file
- [x] Run the API test suite — **12 tests, 0 failures** (needed a JDK 25
      container; this host has no JVM)
- [x] Run the UI test suite — **7 files, 15 tests, 0 failures**
- [x] Automate dev environment setup — `scripts/dev-setup.sh`, and
      `scripts/db-restore.sh` + `migrate-backups.py` for the data
      ([DEV_SETUP.md](DEV_SETUP.md))
- [x] Restore the backups: 1 user, 211 products, 14 stores, 717 purchases,
      verified against a live MongoDB
- [ ] Exclude `certs/**` and `*.env` from `processResources` **(D10)** — the JWT
      private key and live credentials are currently packaged into the jar
- [x] ~~Pin `TZ=UTC`~~ — **superseded 2026-09-23: storage is UTC by
      construction.** `MongoConfig` uses the MongoDB driver's codecs, so a
      purchase date is midnight UTC however the JVM is started (verified with
      a Europe/Sofia JVM, which previously stored 2026-09-23 as
      `2026-09-22T21:00Z`). The JVM's zone now affects log timestamps only, so
      the server runs in local time: `docker-compose.yml` sets
      `TZ=${TZ:-Europe/Sofia}`, and `dev-setup.sh` no longer writes `TZ=UTC`
- [ ] Add `TZ: Europe/Sofia` to the unversioned deployment compose
      (`~/docker-apps/homeapp/compose.yaml`) for local-time logs; optional,
      since without it the container simply logs in UTC
- [ ] Tidy `docker-compose.dev.yml` — network mismatch, wrong `depends_on`,
      and move `dev.env` out of the Java resources tree
- [ ] Complete `example.env` — it omits the OCR, CORS, `UI_PORT`, and
      `BACKEND_HOST` variables the app actually reads
- [ ] Make the test suite runnable on a fresh clone **(D9)** — add
      `application-test.yaml` or defaults for the 12 undefaulted placeholders,
      so `SecurityCorsTest` does not need hand-made certs and a `.env`
- [ ] Commit `gradlew` as mode `100755` **(D7)** — `git update-index --chmod=+x`
- [ ] **Wire tests into CI (D8)** — the API runs `build -x test` and the UI
      builds an image with no test or lint step. Six test classes could have
      rotted unnoticed over six dormant months; they happened not to
- [x] Bring up mongo + api + ui; API boots natively on Java 25 in 3.23 s
- [x] Confirm Tesseract resolves with `eng+bul` — reads Bulgarian cleanly
- [x] Confirm the OpenAI key and `gpt-5-mini` work — scan returned 200 in 24 s
- [x] **Close the E2E gap open since June** — register → verify via MailHog →
      login → scan → 5 line items parsed with quantities and units.
      Caveat: a *synthetic* receipt; a real one is still untested
- [ ] Repeat with a real crumpled receipt photo, and with a PDF
- [ ] Exercise `POST /api/receipts/submit` and the UI in a browser
- [ ] Build fresh container images from the merged source — the GHCR `:dev` tags
      predate the scanning merge, so the existing `compose.yaml` runs old code
- [ ] Review the 18 npm audit findings (12 high)

**Done when:** a real receipt goes in one end and a correct purchase comes out
the other, against the real database.

Status: the pipeline works. A synthetic Bulgarian receipt parsed end to end,
with correct prices, quantities and units. What it exposed is that the
*matching* half is much weaker than the *parsing* half — see M2a.

---

## M1 — One repository

**Goal:** collapse three doc locations and two repos into one.
See [ADR-0002](adr/0002-single-repo.md).

- [ ] `git subtree add` both repos into this one as `/api` and `/ui`, preserving
      history
- [ ] Fold `receipts-api/docs/*` into `/docs`, keeping `ARCHITECTURE_AND_FLOWS`,
      `API_CONTRACTS`, and `RECEIPT_SCANNING`; keep setup docs next to their code
- [ ] Rewrite the GitHub Actions workflows with path filters so an api change
      does not rebuild the ui, and both still publish to GHCR
- [ ] **Wire `scripts/test-upload-limits.sh` into CI** — parked until now
      because it reads config from *both* repos, so neither repo's CI can run
      it. Path-filter it on `ui/nginx/**`, `ui/DOCKERFILE` and
      `api/.../application.yaml`. Depends on D8 being fixed first, since CI
      currently runs no tests at all
- [ ] Revisit end-to-end tests generally once one CI run can see both sides
- [ ] Single `VERSION` and `CHANGELOG.md` at the root
- [ ] Version the deployment: move `~/docker-apps/homeapp/compose.yaml` in as
      `/deploy`, with secrets kept out of git
- [ ] Update the Obsidian notes to point here and drop the stale
      `D:/Documents/...` paths and the `homeapp-infra` reference

**Done when:** one clone, one CI run, one place the spec lives.

---

## M0a — Make real photos work at all

**Goal:** a receipt photographed on a table parses. Right now it returns
nothing, so nothing downstream can be evaluated against real input.

**Status 2026-09-23: banked as improved, not finished.** Correct prices went
213 → 254 across the fixture set; scans that return HTTP 200 went 42 → 53. The
remaining errors are OCR-level, so further gains come from the thresholding
work below, not from prompts or models.

Baselines to compare against with `scan-harness.py --baseline`, kept outside
git next to the ground truth:
`receipt-fixtures/baselines/2026-09-23-m0a-254/summary.json` (current: 254
correct prices, 53 HTTP 200) and `2026-09-22-pre-m0a-213/` (before M0a).

- [x] **Crop to the receipt before preprocessing (D17)** — 213 → 232 correct
      prices, well outside the ±2 noise floor. Also removed the
      screenshot-vs-photo branch entirely: neither the PNG extension nor a
      colour count separates the two classes
- [ ] **Replace global Otsu with adaptive/local thresholding (Sauvola)** — now
      the highest-value remaining change. One global threshold cannot serve a
      curled or side-lit receipt where the lit and shadowed halves need
      different ones, which is exactly what the crumpled and low-light fixtures
      are for
- [ ] Try PSM 4 (single column, variable sizes) instead of PSM 6 (single
      uniform block) — a receipt is a variable-width column with a wide gap
      between name and price, which is not what PSM 6 assumes
- [ ] Try `load_system_dawg=0` and `load_freq_dawg=0` — Tesseract's dictionary
      nudges output toward real words, and receipt text is shorthand and digits
- [ ] Try `bul` alone instead of `eng+bul`, and deskew before OCR
- [ ] Try passing grayscale to Tesseract instead of 1-bit, and compare
- [x] **Fail loudly (D17)** — a scan yielding no items now throws and maps to
      422 instead of returning HTTP 200 with an empty list and a 1970 date
- [ ] Add a sanity check on OCR output volume and mean word confidence
- [x] Build a fixture set of real receipts — **64 fixtures**, 10 stores, all
      three paths, in `receipt-fixtures/` with `manifest.json`
- [x] **Build the scoring harness** — `scripts/scan-harness.py`, scoring L0/L1/L2
      against `ground-truth.json` with a measured ±2 noise floor (ADR-0007)
- [x] Transcribe ground truth from the readable shots, validated by the
      receipt's own arithmetic — **64 receipts, 421 items**. Incomplete on
      dates: 29 of 56 have none recorded, and those are excluded from scoring
      rather than counted as failures
- [x] **Settled: photos stay on OCR.** Routing them to vision was measured and
      is worse — it fabricates product names (ADR-0004)

**Done when:** the Relay receipt yields its store, its date and its one item.
*(Still failing — `relay_01_flat.jpeg` returns 422.)*

---

## M2a — Fix what the end-to-end run exposed

**Goal:** make a scanned receipt usable without hand-correcting every row.
These are not polish; they came out of the first real run.

- [x] **Currency (D11)** — done together with numeric wire format (D2), per
      [SPEC §9.5](SPEC.md#95-currency-handling). `Purchase.currency` is stored
      on every row; `LegacyCurrencyBackfill` tagged all 717 legacy rows `BGN`
      at startup (verified against the restored DB) and refuses to guess for a
      currency-less row dated 2026 or later. The API serves only
      `priceEur` / `discountAmountEur`, unrounded; `Formatter` is deleted and
      the UI rounds. Submissions with no `currency` are recorded as EUR
- [ ] **Currency on the scan path (§9.5 rule 6)** — the parser still has no
      concept of currency, so a scanned dual-display receipt is submitted as
      EUR whichever column the model read. Needs a `currency` field in
      `ParsedReceipt` and the prompt; that is a scanning change, so it is
      measured by the harness (ADR-0007). Batch it with the M0a OCR runs
- [ ] **Search matches nothing** — `CustomRepository.findBySearchQuery`
      filters on `productDetails.name` / `storeDetails.name`, but the field is
      `canonicalName` since the rename. Searching `billa` returns 0 pages
      against 717 rows. Found 2026-09-23; predates the currency work
- [x] **`POST /api/purchases` ignores IDs** — fixed 2026-09-23: it now shares
      the id-first resolution of `registerPurchases`. The UI's manual form had
      the matching bug — it sent `product` / `store`, which the API never read,
      so every manual entry would have created nameless records. It now sends
      `productName` / `storeName`. The dev DB had none, so nothing to clean
- [ ] **Stop auto-creating stores (D18)** — `resolveStore` saves any unmatched
      name as a new store with no review; this is where the catalog junk came
      from. Nothing may create a store without a human choosing it
- [ ] Return a **ranked list** of store candidates, not a single suggestion,
      mirroring how products already work
- [ ] Record the raw parsed string as an alias on the chosen store, and the
      raw product wording as a product alias — `Product.aliases` exists and
      nothing writes to it
- [ ] **Fix screenshot detection (D19)** — it keys off the PNG extension, so
      photos exported as PNG skip preprocessing they need and screenshots saved
      as JPEG get preprocessing that destroys them. Use EXIF presence, which is
      already read for orientation
- [ ] **Add aliases to `Store` (D18)** — `Product` has them, `Store` does not.
      Aliases are the only thing that can connect a legal entity such as
      `ЛАГАРДЕР ТРАВЕЛ РИТЕЙЛ ЕООД` to the brand `Relay`, since no string
      metric can. Also clean `кастрия еоод`, `мс. Алмонд` and `ройс` out of the
      catalog — all legal-entity fragments saved as shops
- [ ] Prefer the `МАГАЗИН "..."` line over the letterhead, and treat a trailing
      `ЕООД`/`ООД`/`АД` as a marker of the legal entity (D18)
- [ ] **Store extraction and matching (D13)** — the store came back empty even
      though OCR read `ЛИДЛ` clearly. Fix the prompt, then replace the
      exact-match lookup, and transliterate Cyrillic receipt names onto the
      Latin catalog. Without this every scan needs the store set by hand
- [ ] **Product matching (D12)** — replace whole-string Levenshtein with token
      set scoring. `"мляко прясно"` currently misses five `Прясно мляко`
      products on word order alone, while `"кафе на зърна"` confidently matches
      `"карфиол на брой"`
- [ ] Record confirmed matches as aliases, so each correction improves the next
      scan ([SPEC §8.4](SPEC.md#84-f4--catalog-and-normalization))
- [ ] **Stop inferring "unverified" from a bare 403 (D15)** — return a
      distinguishable error code and have the UI key off that. The current
      behaviour reported a healthy account as unverified and sent the user into
      a dead-end resend flow
- [ ] Make CORS workable off-localhost (D16) — the dev default cannot work for
      a headless server browsed from a laptop, which is the real setup
- [ ] Clean the store catalog — it holds `Тест`, `Тест 2`, `кастрия еоод`,
      `мс. Алмонд`, `ройс`

**Done when:** a scanned receipt comes back with the store identified and most
rows matched correctly.

---

## M2 — Fix the data model

**Goal:** make the lookup loop *possible*. Blocked on nothing; blocks everything
after it. Fixes D1 and D2 from [SPEC §7](SPEC.md#7-known-defects-that-block-the-product).

- [ ] Add `quantity` and `quantityUnit` to the `Purchase` entity
- [ ] Persist them in `registerPurchase` and `registerPurchases` **(D1)**
- [x] Make prices numeric on the wire; delete `Formatter` from the response path
      and move formatting into the UI **(D2)** — done with D11, see M2a
- [x] ISO-8601 dates in both directions, consistently — done 2026-09-23. One
      global Jackson setting, no per-DTO `@JsonFormat`; `dd/MM/yyyy` in a
      request is a 400. The UI builds dates with `toIsoDate` and formats them
      only in `src/lib/format.js`. `ParsedReceipt` (the LLM's output schema)
      keeps `dd/MM/yyyy` on purpose: it is never on the wire, and changing it
      changes the prompt, so it would be measured per ADR-0007
- [ ] The manual form's discount checkbox has no API counterpart — the API
      records a discount *amount*. Replace the checkbox with an amount field,
      or drop it
- [ ] Drop the unused `statistics` collection, entity, and repository **(D3)**
- [ ] Decide **Q2**: what to do about quantity for the 717 existing purchases
- [x] Decide **Q1**: currency — answered in [SPEC §9.5](SPEC.md#95-currency-handling)
      and implemented; rate 1.95583 confirmed 2026-09-23
- [ ] Tests covering quantity round-tripping through scan → submit → read

**Done when:** a purchase saved through the scan flow retains its quantity and
unit, and the API returns numbers.

---

## M3 — Price lookup API

**Goal:** the server can answer "is this a good price?".
Implements [SPEC §8.2](SPEC.md#82-f2--price-lookup).

- [ ] Replace the `findProductByNameFuzzy` stub with real fuzzy, alias-aware
      search **(D5)**
- [ ] `GET /api/products/search?q=` — relevance-ranked, typo-tolerant,
      Bulgarian and English
- [ ] `GET /api/products/{id}/prices` — typical / lowest / last price, usual
      store, per-store comparison, unit-normalised, computed by aggregation
      **(D3, D4)**
- [ ] `GET /api/lookup/snapshot` — the whole dataset in one payload for the
      offline cache
- [ ] Record confirmed matches as product aliases, so parsing improves with use
      ([SPEC §8.4](SPEC.md#84-f4--catalog-and-normalization))
- [ ] Product merge endpoint, moving purchases across

**Done when:** a single HTTP call returns everything the store-aisle screen
needs, with correct unit prices.

---

## M4 — Price lookup UI

**Goal:** the thing the product is for. Needs **Q3** (design) answered first.

- [ ] Mobile-first design for the lookup loop
- [ ] Search-first landing: focused input, results on keystroke
- [ ] Product screen leading with the **verdict**, history below
- [ ] Shelf-price check: type the price you see, get good / normal / expensive
      ([SPEC §8.2.3](SPEC.md#f23-price-check))
- [ ] Unit prices shown throughout
- [ ] PWA: installable, service worker, offline snapshot cache
      ([ADR-0005](adr/0005-pwa-offline-lookup.md))
- [ ] Verify against the NFRs in [SPEC §10](SPEC.md#10-non-functional-requirements)
      on a real phone, in a real shop, with mobile data off

**Done when:** you use it in a shop instead of guessing.

---

## M5 — Capture quality

**Goal:** make the overhead loop cheaper. Everything here was already on the old
roadmap or in the Obsidian notes.

- [ ] Improve product recognition so more rows auto-match
- [ ] Explicit recovery flow when a product or store cannot be matched
- [ ] Discounts that apply across several rows of one receipt
- [ ] Prefill last store and last purchase date on manual entry
- [ ] Toast notifications for submit success and failure
- [ ] Batch upload — decide whether it is worth it
- [ ] Surface OCR confidence so a bad scan is obvious before submitting

---

## Deferred

Real work, not blocking either loop. Pulled forward only when something forces
it.

**Auth hardening** — password reset, refresh tokens and logout, account lockout,
stronger password policy, RBAC, audit logging, MFA. All from the old roadmap's
Phase 1–4. With one user, none of it is urgent; password reset is the first one
that will actually bite.

**Operational** — health checks beyond Actuator defaults, JWT key rotation,
structured logging, metrics.

**Contracts** — generate OpenAPI instead of hand-maintaining `API_CONTRACTS.md`.

**Q6** — pin the Spring milestone and snapshot dependencies to release versions,
so a build that works today still works after another dormant stretch.

**Q4** — rename the `dev.vasoft.homeapp` package now that Receipts is
standalone.

**Q5** — retain receipt images to allow re-parsing with better models later.

---

## Not this project

The Home App suite — Discounts, Tools & Appliances, Outfit Wear, Home Products —
is a separate project and stays in Obsidian until it starts.
See [ADR-0001](adr/0001-standalone-from-home-app.md). When it does start, it is
a modular monolith, not five services.
