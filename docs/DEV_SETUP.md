# Development environment

> The one guide for developing Receipts: the api and the ui together, from a
> fresh clone to a running stack. Deploying is in
> [`deploy/README.md`](../deploy/README.md).

## Quick start

On Ubuntu 24.04, from a fresh clone:

```bash
sudo apt install openjdk-25-jdk tesseract-ocr tesseract-ocr-eng tesseract-ocr-bul
# Node 22 (.nvmrc), e.g. `nvm install`; and Docker
make setup          # checks the above, writes keys and .env, npm ci
# put your OpenAI key in .env: OPENAI_API_KEY=sk-...
make dev            # db and MailHog, then the api (7802) and vite (http://localhost:7800)
make db-restore     # once, in a second terminal: the Jan-2026 backups, from BACKUP_DIR in .env
```

`make dev` runs in the foreground; Ctrl-C stops the api and vite. To keep it
running after you log out, start it detached and follow the log:

```bash
setsid nohup make dev > dev.log 2>&1 < /dev/null &
tail -f dev.log
```

| Target | What it does |
|---|---|
| `setup` | `scripts/dev-setup.sh`: prerequisites, keys, the root `.env`, `npm ci` |
| `dev` | `scripts/dev.sh`: starts MongoDB and MailHog, then `bootRun` (7802) and vite (7800) in the foreground, output interleaved and prefixed `[api]` / `[ui]`. Ctrl-C, or either one exiting, stops both; the containers keep running |
| `down` | stops the dev and preview containers; the data stays |
| `test` | the api's `./gradlew test`, then the ui's lint, format check and tests |
| `e2e` | `e2e/run.sh` ([below](#end-to-end-smoke)) |
| `preview PR=<n>` | `scripts/preview.sh`: that pull request's images at `receipts-test.vasoft.dev` ([below](#testing-a-pull-request-on-its-preview-images)) |
| `db-restore` | `scripts/db-restore.sh` ([Restoring the backups](#restoring-the-backups)) |
| `db-refresh` | `scripts/db-copy.sh prod dev`: replaces the dev database with a copy of production's, reading production only |

Before committing, `make test` and `git diff --check`.

---

## What `make setup` does

`scripts/dev-setup.sh` is idempotent. It never overwrites a value you have
already set, and the only tracked file it touches is `gradlew`'s executable bit.

| Step | What the script does |
|---|---|
| Prerequisite audit | Checks java 25, node (the major in `.nvmrc`, at least 22.12), docker, openssl, tesseract and its `eng`/`bul` language data; reports what is missing instead of guessing |
| `gradlew` | Sets the executable bit if a checkout lost it |
| JWT keys | Generates a PKCS#8 RSA keypair into the gitignored `api/certs/`, outside the resources tree so it never reaches the jar |
| `.env` | Writes the root `.env` with the variables `application.yaml` has no default for, plus Linux overrides for the OCR paths that `application-dev.yaml` defaults to Windows locations. An `api/.env` from an older checkout is moved there, its old default ports switched to 7800/7802 |
| UI dependencies | `npm ci` |
| UI dev certs | Only with `scripts/dev-setup.sh --https`. Skipped by default: browsers treat `http://localhost` as a trustworthy origin, so the backend's `Secure` cookie works over plain http, and a self-signed cert just adds a warning to click through |

Without it a fresh clone builds and tests (the `test` profile needs no
`.env`), but cannot run: `bootRun` needs the `.env` values, and the `dev`
profile reads its JWT keys from `api/certs/`, which nothing else creates.

## What you install yourself

- **Java 25**, the Gradle toolchain's version.
- **Node 22**, the version in `.nvmrc`, which CI reads too.
- **Docker**, for MongoDB, MailHog, e2e and previews.
- **Tesseract** with `eng` and `bul`. Only **image** receipts need it; PDFs take
  the direct LLM path ([ADR-0004](adr/0004-ocr-plus-llm-parsing.md)), so you
  can defer it and still exercise most of the scan flow.
- **An OpenAI API key**, in the root `.env` as `OPENAI_API_KEY`. Receipt
  parsing fails without it; nothing else does.

**On Windows**, install Tesseract from
<https://github.com/UB-Mannheim/tesseract/wiki> and set `TESSDATA_PATH` in
`.env` to its `tessdata` directory; the `dev` profile's default,
`C:/Program Files/Tesseract-OCR/tessdata`, is the installer's.

If you would rather not install a JDK at all, the api's tests run in a
container, from `api/`:

```bash
docker run --rm -u "$(id -u):$(id -g)" -v "$PWD:/work" \
  -v "$HOME/.gradle-docker:/gradle-home" -e GRADLE_USER_HOME=/gradle-home \
  -w /work eclipse-temurin:25-jdk sh gradlew test
```

That gives up `spring-boot-devtools` hot restart, which is most of the value of
running the backend natively.

**A user:** the backups contain one already-verified account. To register a
fresh one, MailHog catches the verification mail at <http://localhost:7805>.

---

## Configuration: profiles and `.env`

- `application.yaml` is the base, and what production runs: MongoDB, mail,
  Spring AI, multipart, JWT, OCR and rate limiting, mostly from environment
  variables.
- `application-dev.yaml` (`--spring.profiles.active=dev`, which `make dev`
  passes) overrides it for local work: JWT keys from `api/certs/` as `file:`
  paths, local OCR defaults, the vite origin for CORS, plain SMTP for MailHog,
  and verbose logging. Without the profile the JWT keys resolve to empty
  defaults and startup fails.
- `application-test.yaml` (`src/test/resources`) gives the tests every value
  they need, so they read no `.env`.
- The root `.env` supplies the rest; the root `example.env` lists every
  variable. A shell export or an IDE run configuration overrides it. An IDE run
  outside Gradle reads it only with `-Dspringdotenv.directory=..` and `api/` as
  the working directory.

## Checking a change by hand

The flows that unit tests do not cover end to end, at
<http://localhost:7800>:

- **Login:** `POST /api/auth/token` sets the cookie; `GET /api/auth/status`
  returns the session's remaining time.
- **Registration:** register, open the mail in MailHog (7805), verify, and
  try resend-verification for an inactive user.
- **Purchases:** add one by hand and list them; another user must not see it.
- **Scanning:** an image receipt and a PDF; review the parsed rows and submit
  them. If OCR looks wrong, set `OCR_DEBUG_OUTPUT_PATH` and look at the debug
  images.

## When something fails

- **Auth looks broken:** the cookie is `HttpOnly`, `Secure` and
  `SameSite=Strict`. Check that the browser kept it, that the ui sends
  same-origin credentials, and that the origin you are on is in
  `APP_CORS_ALLOWED_ORIGINS`. Plain-http origins other than `localhost` lose
  the `Secure` cookie, so remote access goes through an https proxy host.
- **The api does not start:** missing JWT key files, wrong MongoDB
  credentials or auth database, or a `.env` value missing.
- **Scans fail:** no `OPENAI_API_KEY`; for images, a wrong `TESSDATA_PATH` or
  missing language data.

---

## Timezones: storage is UTC, logs are local

**Stored dates do not depend on the server's timezone.** A purchase date is a
calendar day, stored as midnight UTC of that day. The API's `MongoConfig` uses
the MongoDB driver's java.time codecs, which read and write a `LocalDate` as
midnight UTC whatever zone the JVM runs in. Moments, such as token expiry, are
`Instant`s and zone-free already. On the wire every date is ISO-8601
([SPEC §9.1](SPEC.md#91-the-wire-format-carries-data-not-presentation)), and
the UI formats it for display.

So the JVM's zone decides only **log timestamps**. Run it in local time:

- natively, `./gradlew bootRun --args='--spring.profiles.active=dev'` follows
  the host clock. The `dev` profile is required: without it the JWT keys
  resolve to the empty `JWT_PUBLIC_KEY` default and startup fails
- in a container, set `TZ` on the api service; the image on its own logs in
  UTC

Spring Boot's log format includes the offset (`…+03:00`), so local-time logs
still line up unambiguously with UTC data.

---

## Restoring the backups

`make db-restore` reads the backup exports from `BACKUP_DIR` in the root
`.env` (or `SRC=<dir>` on the command line), migrates them into `.migrated/`
and imports them into the dev database, replacing its collections.

The Jan-2026 backups **cannot be imported as they are.** They predate three
changes: the package rename to `dev.vasoft`, the split into per-domain
packages, and the rename of `name` to `canonicalName`. They also predate
user-scoped purchases.

`scripts/migrate-backups.py` handles all of it:

| Collection | Migration |
|---|---|
| `users` | `_class` rewritten. Already on the new package — this collection was re-saved after the rename, the others were not. |
| `products` | `name` → `canonicalName`, plus a computed `normalizedCanonicalName`, empty alias arrays, `_class` rewritten |
| `stores` | Same as products, `iconID` preserved |
| `purchases` | **`userId` backfilled** from the user document, `discount` boolean → `discountAmount`, **`currency: BGN`** written explicitly, dates re-anchored from midnight Europe/Sofia to midnight UTC, `_class` rewritten |

The currency is stored rather than inferred ([SPEC §9.5](SPEC.md#95-currency-handling)).
A database restored before this change is fixed up anyway: the API's
`LegacyCurrencyBackfill` tags any currency-less purchase `BGN` at startup and
logs how many it touched. It refuses — and startup fails — if a currency-less
purchase is dated 2026-01-01 or later, since that one could be either currency.

The API also refuses to start against **unmigrated** data (`LegacyDataGuard`):
any purchase whose date is not midnight UTC, or that has no `userId`, stops
startup with the counts and a pointer back here. Both are exactly what this
migration fixes. A database the old app wrote directly will trip it: the
Jan-2026 backups came from one, and had both problems.
Migrate it before pointing the new API at it.

The `userId` backfill is the critical one. Nothing in the backup carries a
`userId`, and every read path scopes by it — so importing untouched gives you a
database that looks full and an app that shows an empty list.

The normalization is a port of `NormalizationService`, verified against the
real Java implementation over every product and store name in the backups, with zero
mismatches. It has to match exactly: the port reproduces even the quirk where
NFD decomposition and mark-stripping turn Cyrillic **Й into И**. A divergence
here would make imported products unmatchable by the scanner.

### What cannot be recovered

- **Quantity and unit on every legacy purchase.** Never stored (D1). Without it
  there is no unit price, so cross-package-size comparison stays broken for
  historical data. This is open question Q2 in the spec.
- **Discount amounts on the discounted legacy purchases.** The old schema stored a `true`/`false`
  flag, not a figure. Migrated as `discountAmount: 0.0`, which here means
  "unknown", not "none".

---

## End-to-end smoke

`e2e/` runs the whole product the way it ships: the api and ui images built
from your checkout, MongoDB with auth, MailHog, and a stub in place of OpenAI.
Playwright registers a new account, verifies it from the mail, logs in, scans a
receipt, saves the reviewed rows, adds a purchase by hand, and reloads to check
everything persisted. CI runs the same script on every pull request that
touches the api, the ui or `e2e/`.

```bash
cd e2e && npm ci && npx playwright install chromium && cd ..   # once
e2e/run.sh                                                     # ui on 7830, MailHog on 7835
E2E_UI_PORT=18780 E2E_MAILHOG_PORT=18725 e2e/run.sh           # other ports
E2E_KEEP=1 e2e/run.sh                                          # leave it up to poke at
```

Each run is a compose project of its own, `receipts-e2e-<run>` (`E2E_RUN`,
by default a timestamp and the shell's pid), with its keys in `e2e/certs/<run>`
and its results in `e2e/test-results/<run>`. Two runs side by side therefore
do not tear each other down; the second needs other ports.

- The stub answers every scan with the same two-item receipt, so a run is
  free, deterministic and needs no key. That is also why it runs for
  Dependabot pull requests, which cannot read repository secrets. It says
  nothing about parsing quality; that is the harness's job (ADR-0007).
- A fresh JWT key pair is generated per run into the gitignored
  `e2e/certs/<run>/`, and deleted with the stack.
- The api runs its default (production) profile. Only the mail protocol is
  switched to plain SMTP, because MailHog does not speak SMTPS.
- On failure the Playwright report, trace and every container's log are in
  `e2e/test-results/<run>/`; CI uploads them as the `e2e-report` artifact.

## JWT keys

- Dev keys live in `api/certs/` (gitignored). `application-dev.yaml` reads
  `file:./certs/private.pem` and `file:./certs/public.pem`, relative to the
  working directory, which is `api/` under `bootRun`. `JWT_PRIVATE_KEY` and
  `JWT_PUBLIC_KEY` override them, with a `file:` path or PEM content.
- Keys never reach the jar: `processResources` excludes `*.pem`, `*.key`,
  `*.p12`, `*.jks` and `*.env`, and `verifyNoSecretsInJar` fails the build if
  the boot jar or the plain jar contains any of those. It runs after every
  `bootJar`, as part of `check` (so `./gradlew build` and CI run it), and
  before `buildImage`.
- The root `example.env` documents every variable; it is not read by anything.

## The dev stack: `compose.dev.yaml`

MongoDB and MailHog in containers; the api and the ui run natively, so the api
keeps devtools hot restart and the ui vite's hot reload. `make dev` starts all
four; by hand, from the repository root:

```bash
docker compose -f compose.dev.yaml up -d
(cd api && ./gradlew bootRun --args='--spring.profiles.active=dev')
(cd ui && npm run dev)
```

| Service | Port | Notes |
|---|---|---|
| ui (vite) | 7800 | proxies `/api` to the api |
| api (`bootRun`) | 7802 | `SERVER_PORT` |
| MailHog | 7805 (web), 1025 (SMTP) | |
| MongoDB | 27017 | bound to `127.0.0.1` only |

These are the dev block of the port scheme in
[ROADMAP M1b decision 21](ROADMAP.md#decisions--2026-10-24); previews,
production and e2e each have a block of their own.

- **One `.env`, at the root.** Compose interpolates from it because it sits
  next to `compose.dev.yaml`, and `bootRun` reads it through spring-dotenv:
  `api/build.gradle` sets `springdotenv.directory` to the root, while the
  working directory stays `api/`, so the dev profile's `file:./certs/*.pem`
  still means `api/certs/`. `example.env` beside it lists every variable.
  Tests run the `test` profile and read no `.env`.
- MongoDB is pinned to a major (`mongo:8.2`). `latest` can move a major on a
  pull, and mongod refuses data files more than one feature-compatibility
  version behind.
- Data lives in `MONGODB_DATAPATH`, relative to the root: `.mongo-data` on a
  fresh setup, `api/.mongo-data` for a `.env` that `dev-setup.sh` moved from
  `api/` (both gitignored, owned by the container's uid).

### Behind a proxy host

A reverse proxy can serve vite under a host name, with WebSockets on for hot
reload; the HMR client connects back to the page's own host and port, so vite
needs no HMR settings. Three values in the root `.env`
make a proxy host work:

```bash
UI_ALLOWED_HOSTS=receipts-dev.vasoft.dev        # vite answers 403 to any other host
APP_HOST_URL=https://receipts-dev.vasoft.dev    # where verification mails link
APP_CORS_ALLOWED_ORIGINS=http://localhost:7800,https://receipts-dev.vasoft.dev
```

`UI_ALLOWED_HOSTS` takes a comma-separated list and defaults to
`receipts-dev.vasoft.dev`; `localhost` is always allowed. vite proxies `/api`
to `UI_API_TARGET`, by default `http://localhost:${SERVER_PORT}`. The browser's
`Origin` passes through that proxy unchanged, so the api checks it against
`APP_CORS_ALLOWED_ORIGINS` as if the browser called it directly.

### Testing a pull request on its preview images

Every pull request from a branch of this repository publishes a preview image
for each side it touches: `ghcr.io/veselin-antonov/receipts-api:pr-<n>` and
`ghcr.io/veselin-antonov/receipts-ui:pr-<n>`; a bot comment on the pull request
lists the ones it built. `make preview` runs them at
`https://receipts-test.vasoft.dev`, next to the dev stack:

```bash
make preview PR=28              # pull and run #28's images
make preview PR=28 REFRESH=1    # the same, on a fresh copy of the database
```

| Service | Port | Notes |
|---|---|---|
| ui | 7810 | `receipts-test.vasoft.dev` points here |
| api | 7812 | `APP_HOST_URL` and the CORS origin are `https://receipts-test.vasoft.dev` |
| MailHog | 7815 (web) | the preview's own; verification mails link to `receipts-test` |
| MongoDB | internal | the volume `receipts-preview_db` |

- A side the pull request did not touch has no `pr-<n>` image and runs `:dev`
  (master) instead. Images are pulled every time, because a preview tag is
  overwritten on every push.
- **The preview database is a copy, never shared.** The first run, and every
  run with `REFRESH=1`, copies production's database into it with
  `scripts/db-copy.sh` (`mongodump | mongorestore --drop`), which only reads
  the source; nothing the preview writes reaches production or dev. Without a
  running production stack it stops with an error. It keeps its data between pull requests otherwise.
- The preview api has a JWT key pair of its own in `.preview/certs/`
  (gitignored, made on the first run). It takes `OPENAI_API_KEY` and the OCR
  and upload settings from the root `.env`; everything else is set in
  `compose.preview.yaml`.
- One preview runs at a time: `make preview PR=<m>` replaces the running one.
  `make down` stops it, with the dev containers; `docker compose -f
  compose.preview.yaml down` stops it alone. `docker logs -f
  receipts-preview-api` follows its api.
