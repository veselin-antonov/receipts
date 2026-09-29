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
- [x] Exclude `certs/**` and `*.env` from `processResources` **(D10)** — done
      2026-09-26: dev keys moved to `api/certs/` and are read as `file:`,
      `processResources` excludes key and env files, `verifyNoSecretsInJar`
      fails `build` on a jar containing one, and `example.env` moved to
      `api/example.env`
- [x] Dates do not depend on the JVM's zone — storage is UTC by construction
      (`MongoConfig` uses the MongoDB driver's codecs, verified with a
      Europe/Sofia JVM). The zone affects log timestamps only; see DEV_SETUP's
      "Timezones"
- [x] Tidy `docker-compose.dev.yml` — network mismatch, wrong `depends_on`,
      and move `dev.env` out of the Java resources tree. Done 2026-09-25: it
      interpolates from `api/.env`, `dev.env` is gone, the ui reaches the
      native api through `host.docker.internal` (it was a 502 before), mongo is
      pinned to 8.2, and `UI_TAG` swaps in a pull request's preview image
- [x] Complete `example.env` — it omits the OCR, CORS, `UI_PORT`, and
      `BACKEND_HOST` variables the app actually reads. Done in #30: every
      placeholder in `application*.yaml` and every variable the dev compose
      file reads is listed; `BACKEND_HOST` is set by compose (`UI_BACKEND`)
- [ ] Make the test suite runnable on a fresh clone **(D9)** — add
      `application-test.yaml` or defaults for the 12 undefaulted placeholders,
      so `SecurityCorsTest` does not need hand-made certs and a `.env`
- [x] Commit `gradlew` as mode `100755` **(D7)** — it is `100755` in the index
- [x] **Wire tests into CI (D8)** — the api image workflow runs
      `./gradlew build` with tests, and the ui gained `checks.yml` (lint,
      format, tests) gating its images. At M1 both run on pull requests too
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

- [x] `git subtree add` both repos into this one as `/api` and `/ui`, preserving
      history — from the unsquashed local branches, since #23 and #31 were
      squash-merged. Author e-mails rewritten to the GitHub noreply address
      before publishing
- [x] Fold `receipts-api/docs/*` into `/docs`, keeping `ARCHITECTURE_AND_FLOWS`,
      `API_CONTRACTS`, and `RECEIPT_SCANNING`; keep setup docs next to their code
      (`api/docs/DEVELOPMENT_SETUP.md`, `PRODUCTION_SETUP.md`)
- [x] Rewrite the GitHub Actions workflows with path filters so an api change
      does not rebuild the ui, and both still publish to GHCR — `api.yml` /
      `ui.yml`, checks on pull requests and master, images under the same
      `receipts-api` / `receipts-ui` names. First runs green on 2026-09-24
- [x] **Wire `scripts/test-upload-limits.sh` into CI** — `upload-limits.yml`,
      path-filtered on `ui/nginx/**`, `ui/DOCKERFILE` and the api's
      `application.yaml`
- [x] Revisit end-to-end tests generally once one CI run can see both sides —
      done 2026-09-25: `e2e/` runs both images with MongoDB, MailHog and an
      OpenAI stub under Playwright on every pull request, and
      `MongoIntegrationTest` runs the api against a real MongoDB. `ci.yml`
      ends in `ci-summary`, the one check to require
- [x] Single `VERSION` and `CHANGELOG.md` at the root
- [x] Version the deployment: `~/docker-apps/homeapp/compose.yaml` is now
      `deploy/compose.yaml`, with `example.env`; secrets stay in the gitignored
      `.env`
- [x] Publish as the public `veselin-antonov/receipts`, confirm both images
      publish from it, then archive `receipts-api` and `receipts-ui` — both
      `:dev` images published from `6311e12`; old repos archived 2026-09-24
- [x] Update the Obsidian notes to point here and drop the stale
      `D:/Documents/...` paths and the `homeapp-infra` reference

**Done when:** one clone, one CI run, one place the spec lives.

---

## M1b — Monorepo development workflow

**Goal:** develop the api and the ui as one product. M1 met its done-when
literally but only joined the two repos: the dev stack still lives in `api/`,
nothing starts or tests both layers, the setup docs follow the old repo
boundary, and nothing has ever been released from here. Comes before the
remaining feature work.

### Decisions — 2026-09-27

1. **The dev stack moves to the repo root:** `compose.dev.yaml`, `.env` and
   `example.env` at the root, not under `api/`.
2. **ui development runs on vite** (5173), proxying to the `bootRun` api. The
   ui container is for previews and built images only. vite's api target
   becomes configurable.
3. **Hosts**, added by the user in nginx-proxy-manager, outside the repo:
   - `receipts-dev.vasoft.dev` → vite on 5173, which proxies to `bootRun` on
     7002. Needs WebSocket support for hot reload, and the host in vite's
     `allowedHosts`.
   - `receipts-test.vasoft.dev` → the preview stack: the ui container plus the
     api container from #30.
   - Each environment's origin goes in its api's `APP_CORS_ALLOWED_ORIGINS`,
     with a matching `APP_HOST_URL`.
4. **A root `Makefile`** (make, not just).
5. **First monorepo release is 0.1.0.** Run `release.yml` for real, define
   what `latest` means, move `deploy/compose.yaml` from `:dev` to release tags,
   and move the production stack (`~/docker-apps/homeapp`) to 0.1.0.
6. **One dev guide in `docs/`:** `api/docs/DEVELOPMENT_SETUP.md` and the setup
   parts of `api/README.md` fold into `docs/DEV_SETUP.md`;
   `api/docs/PRODUCTION_SETUP.md` folds into `deploy/README.md`.

### Decisions — 2026-09-28

7. **Production moves to `receipts.vasoft.dev`**, and `homeapp.vasoft.dev`
   redirects to it. The user adds both in nginx-proxy-manager.
8. **Production starts from the dev database.** At the switch, the current dev
   data (migrated, 717 purchases) is copied in; from then on production is the
   source of truth. The old `~/docker-apps/homeapp/db` is not used.
9. **Production ports:** the ui on a host port of its own (e.g. 7870). The api
   and MongoDB are not published on the host, only reachable inside the stack.
10. **The dev ui container goes in step 1.** It is not used day to day; being
    without it until the structure is right is accepted.
11. **dotenv reads the root `.env`, while `bootRun` still runs from `api/`**,
    so D10's `file:./certs/*.pem` paths keep working. Dev keys stay in
    `api/certs/`.
12. **Every database is separate:** dev, preview and production each have
    their own. Dev and preview can be refreshed from production on demand: a
    copy, never shared, never writing to production. Until production exists
    (step 7), the dev database is the source, so refreshing from production
    only becomes possible after the switch.
13. **Preview ports:** api 7013, ui 7873; `receipts-test.vasoft.dev` → 7873.
14. **`make dev` runs in the foreground:** the api and vite together, output
    interleaved, and Ctrl-C stops both.
15. **Targets:** `setup`, `dev`, `down`, `test`, `e2e`, `preview PR=<n>`,
    `db-restore` and `db-refresh`.
16. **Production runs a pinned version**, 0.1.0. `latest` is defined but
    production does not use it.
17. **`release.yml` is run by the user or by Claude.** Claude runs it only when
    asked, after the version-bump pull request is merged, and verifies the
    tag, the release and the images.
18. **`.github/instructions/{api,ui}.instructions.md` stay per layer**, their
    setup steps replaced by pointers to `docs/DEV_SETUP.md`.
19. **M1b comes before the remaining feature work.** Branches that do not
    touch M1b's files merge whenever they are ready.
20. **The PR plan below is approved** as drafted: seven PRs in that order,
    the Makefile and the preview stack as separate PRs.

No open questions remain.

### Where things stand

- `api/docker-compose.dev.yml` (`name: homeapp-dev`) runs `receipts-db`,
  `mailhog` and `receipts-ui`, and interpolates from `api/.env` — so the api's
  directory owns the ui's dev settings (`UI_PORT`, `UI_TAG`).
- `ui/vite.config.js` proxies `/api` to a hardcoded
  `target: 'http://localhost:7002'`. `dev-setup.sh` writes
  `APP_HOST_URL=http://localhost:5173`, so verification mails link to vite,
  while day-to-day use goes through the ui container on 7863.
- `homeapp.vasoft.dev` is served by the **dev** stack today. The production
  `~/docker-apps/homeapp/.env` uses `UI_PORT=7863`, `SERVER_PORT=7002` and
  `MONGODB_PORT=27017`, the same ports as the dev stack, and no production
  container exists (only `homeapp-ui-dev`, `receipts-db-dev` and `mailhog` are
  up). The dev `api/.env` lists `https://homeapp.vasoft.dev` in
  `APP_CORS_ALLOWED_ORIGINS` for the same reason.
- `release.yml` (`workflow_dispatch` only) has never run: no runs, no tags, no
  GitHub releases. `VERSION` and `ui/package.json` are `0.0.6`.
  `deploy/compose.yaml` and the production compose run `receipts-api:dev` and
  `receipts-ui:dev`, and `api.yml` / `ui.yml` publish with
  `include_latest: false`, so nothing in this repository has published
  `:latest` yet.
- `scripts/dev-setup.sh` ends by printing four commands to run by hand
  (compose up, `db-restore.sh`, `bootRun`, `npm run dev`); no one command
  starts or tests both layers.

### Checklist

- [ ] **Dev stack at the root (decisions 1, 10)** — `compose.dev.yaml`,
      `.env`, `example.env`, holding db and MailHog only; `bootRun` and
      `dev-setup.sh` read and write the root `.env`. One env file for one
      product, not the api's
- [ ] **dotenv reads the root `.env` (decision 11)** — `bootRun` keeps cwd
      `api/`, so `file:./certs/*.pem` still resolves to `api/certs/`, where
      dev keys stay
- [ ] **Move `example.env` to the root** — it is complete for today's layout
      since #30 (the M0 item); the move adds only the vite api target
      (decision 2)
- [ ] **vite api target from the environment (decision 2)** — replace the
      hardcoded `http://localhost:7002` in `ui/vite.config.js`, defaulting to
      it
- [ ] **vite behind `receipts-dev.vasoft.dev`** — `server.allowedHosts` and
      HMR over the proxy's WebSocket, or the page loads without hot reload
- [ ] **Preview stack at `receipts-test.vasoft.dev` (decision 13)** — ui on
      7873 and api on 7013, on `pr-<n>` images from #30, with a MongoDB of its
      own
- [ ] **Separate databases, refreshed on demand (decision 12)** — dev,
      preview and production never share one. `make db-refresh` copies
      production into dev; `make preview` can do the same for the preview
      database. Read-only against production; until step 7, the dev database
      is the source
- [ ] **`APP_HOST_URL` per environment** — dev `https://receipts-dev.vasoft.dev`,
      preview `https://receipts-test.vasoft.dev`, production
      `https://receipts.vasoft.dev`, each origin in the same stack's
      `APP_CORS_ALLOWED_ORIGINS`. Also settles D16 (M2a) for the real setup
- [ ] **Root `Makefile` (decisions 14, 15)** — targets:
      - `setup` — run `scripts/dev-setup.sh` (prerequisites, keys, `.env`, `npm ci`)
      - `dev` — start db and MailHog, then `bootRun` and vite in the
        foreground, output interleaved; Ctrl-C stops both
      - `down` — stop whatever `dev` or `preview` started
      - `test` — `./gradlew test` and the ui's lint, format and tests
      - `e2e` — `e2e/run.sh`
      - `preview PR=<n>` — pull and run that PR's preview images at
        `receipts-test.vasoft.dev`, optionally refreshing its database
      - `db-restore` — `scripts/db-restore.sh`
      - `db-refresh` — copy production's database into dev
- [ ] **`dev-setup.sh` ends with `make dev`** — instead of the four manual
      commands
- [ ] **One dev guide (decision 6)** — fold `api/docs/DEVELOPMENT_SETUP.md` and
      `api/README.md`'s Quickstart (Windows `TESSDATA_PATH`, `export`s) into
      `docs/DEV_SETUP.md`; `api/docs/PRODUCTION_SETUP.md` into
      `deploy/README.md`; delete what is folded
- [ ] **Point `.github/instructions` at the guide (decision 18)** — the
      per-layer files stay, but their setup text is stale:
      `ui.instructions.md` says the proxy goes to `http://localhost:7002` and
      `npm run dev` starts an "HTTPS dev server"; `api.instructions.md` has its
      own "Run locally" steps. Replace those with pointers
- [ ] **Release 0.1.0 (decisions 5, 17)** — bump `VERSION`, `ui/package.json`
      and `CHANGELOG.md` in a pull request, then run `release.yml`
- [ ] **Define the tags** — `latest` is the newest release, `dev` is master,
      `pr-<n>` a preview; production pins a version instead (decision 16).
      Write it in `deploy/README.md`, which already lists them
- [ ] **`deploy/compose.yaml` on release tags** — `receipts-api` and
      `receipts-ui` pinned to `0.1.0`, not `:dev` or `latest`; only the ui
      published on the host (e.g. 7870), the api and MongoDB internal
      (decision 9)
- [ ] **Production on 0.1.0 at `receipts.vasoft.dev` (decisions 7, 8)** —
      `~/docker-apps/homeapp` runs `deploy/compose.yaml` at 0.1.0 with a copy
      of the dev database, not the old `~/docker-apps/homeapp/db`;
      `homeapp.vasoft.dev` redirects to `receipts.vasoft.dev`

Stragglers:

- [ ] The ui's `<title>` is still `Home App` (`ui/index.html`)
- [ ] `e2e/compose.yaml` hard-codes `name: receipts-e2e`, so two e2e runs on
      one host tear each other down (seen testing D10). Name the project per
      run
- [ ] `api/docker-compose.yml` is the old api repository's deployment file
      (`receipts-api:latest`, api and db only), duplicating
      `deploy/compose.yaml`. Delete it, and set `TZ=${TZ:-Europe/Sofia}` on the
      api in `deploy/compose.yaml`, which sets none, for local-time logs
- [ ] `api/.env` still has `BACKEND_HOST=localhost:7002`, which nothing reads:
      the dev compose sets the ui container's `BACKEND_HOST` itself, and
      `dev-setup.sh` does not write it. Drop it when `.env` moves to the root
- [ ] **Q4**, the `dev.vasoft.homeapp` package rename, stays deferred: it is
      invasive and buys nothing functional

### PR plan

Already in flight:

- **#30** — `receipts-api:pr-<n>` preview images, and an optional api service
  in the dev compose. Merge first: the preview stack builds on it. *(user
  merges)*
- **`chore/receipts-container-names`** — unpushed; renames project
  `homeapp-dev` to `receipts-dev` and gives `deploy/compose.yaml`
  `name: receipts`. Rebase on #30, push, open, merge. *(user merges)*
- **#28** (M2a search and D15) — rebase on #30 so it gets an api preview.
- **#28 and `feat/responses-api`** (which waits on the OCR evaluation) are not
  blocked by M1b and merge whenever they are ready: neither touches its files.
  *(user merges)*

Then, each one testable on its own:

1. **Dev stack to the root** — `compose.dev.yaml` with db and MailHog only,
   root `.env` and `example.env`, dotenv reading the root `.env`,
   `dev-setup.sh` writing it (and moving an existing `api/.env`),
   `BACKEND_HOST` dropped, the dev ui container removed. *Test:* on a fresh
   clone, `dev-setup.sh`, `docker compose -f compose.dev.yaml up -d`, `bootRun`
   and `./gradlew test` all work with no `api/.env`, and the keys load from
   `api/certs/`.
2. **vite for ui development** — configurable api target, `allowedHosts`, HMR
   through the proxy, dev `APP_HOST_URL` and CORS origin, the `<title>`.
   *User:* add `receipts-dev.vasoft.dev` → 5173 with WebSockets on.
   *Test:* edit a component at `receipts-dev.vasoft.dev` and see it reload;
   the verification mail links there.
3. **Root `Makefile`** — `setup`, `dev` (foreground, Ctrl-C stops both),
   `down`, `test`, `e2e`, `db-restore`, `db-refresh` (usable once production
   exists, step 7); `dev-setup.sh` ends with `make dev`; per-run e2e
   project name. *Test:* `make setup && make dev` on a fresh clone, Ctrl-C
   leaves no api or vite behind; `make test`; two `make e2e` runs side by side.
4. **Preview stack** — `make preview PR=<n>` runs the ui (7873) and api (7013)
   containers on that PR's images with their own MongoDB, refreshable as a
   copy, and the preview `APP_HOST_URL` and CORS origin.
   *User:* add `receipts-test.vasoft.dev` → 7873.
   *Test:* `make preview PR=28` serves #28 at `receipts-test.vasoft.dev`
   while `make dev` keeps running, and writes nothing to the dev database.
5. **One dev guide** — fold the api setup docs into `docs/DEV_SETUP.md` and
   `deploy/README.md`, written around `make`, and point the
   `.github/instructions` setup steps at them. *Test:* following
   `docs/DEV_SETUP.md` alone gets a fresh clone to a working `make dev`.
6. **Release 0.1.0** — bump `VERSION`, `ui/package.json` and `CHANGELOG.md`.
   *(user merges)* Then the user, or Claude when asked, runs `release.yml`.
   *Test:* `v0.1.0` tag, GitHub release, and `receipts-api:0.1.0` /
   `receipts-ui:0.1.0` / `:latest` in GHCR.
7. **Deploy on release tags** — `deploy/compose.yaml` pinned to `0.1.0`, the
   ui on its own host port (e.g. 7870), api and MongoDB internal, production
   `APP_HOST_URL` and CORS `https://receipts.vasoft.dev`, tags defined in
   `deploy/README.md`. *(user merges)*
   *User:* copy the dev database into production, switch
   `~/docker-apps/homeapp` to the new compose, add `receipts.vasoft.dev` → the
   production ui and redirect `homeapp.vasoft.dev` to it. *Test:*
   `receipts.vasoft.dev` serves 0.1.0 with 717 purchases while the dev stack
   is stopped; `homeapp.vasoft.dev` redirects there; `make db-refresh` then
   copies from production.

**Done when:** a fresh clone runs `make setup && make dev` and develops the api
and ui together at `receipts-dev.vasoft.dev`; `make preview PR=<n>` serves a
pull request at `receipts-test.vasoft.dev` on its own database; and
`receipts.vasoft.dev` runs release 0.1.0 from its own stack, with
`homeapp.vasoft.dev` redirecting to it.

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
- [x] **Screenshot detection (D19)** — resolved by removing the branch: every
      image takes one preprocessing path (see M0a's crop item, ADR-0004)
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

**Q6** — *mostly answered: everything resolves from Maven Central, and the one
milestone left, Spring AI 2.1.0-M1, has an exit condition in
[ADR-0008](adr/0008-spring-ai-2.1-milestone.md).* Originally: pin the Spring
milestone and snapshot dependencies to release versions,
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
