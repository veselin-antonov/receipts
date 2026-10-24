# Development environment

> What is automated, what you must do by hand, and why.

Run this first:

```bash
scripts/dev-setup.sh          # add --https to also make vite dev certs
```

It is idempotent. It never overwrites a value you have already set, and the
only tracked file it touches is `gradlew`'s executable bit.

---

## Why setup needs a script at all

A fresh clone cannot build, test, or run. Three separate reasons:

1. `gradlew` is committed non-executable, so `./gradlew` fails outright (D7).
2. `application.yaml` has **12 placeholders with no defaults**, and
   `spring-dotenv` reads a `.env` that is gitignored. Without it,
   `server.port=${SERVER_PORT}` fails to parse as an `Integer` and even
   `gradlew test` dies during context startup (D9).
3. The `dev` profile resolves `jwt.private-key` to `file:./certs/private.pem`
   (that is, `api/certs/`), a gitignored path that nothing else creates.

None of this is documented as a test prerequisite, and CI never runs tests
(D8), so nothing catches it.

---

## Automated

| Step | What the script does |
|---|---|
| Prerequisite audit | Checks java 25, node, docker, openssl, tesseract and its `eng`/`bul` language data; reports what is missing instead of guessing |
| `gradlew` | Sets the executable bit, and warns that the fix needs committing |
| JWT keys | Generates a PKCS#8 RSA keypair into the gitignored `api/certs/`, outside the resources tree so it never reaches the jar |
| `.env` | Writes the root `.env` with all 12 undefaulted variables, plus Linux overrides for the OCR paths that `application-dev.yaml` defaults to Windows locations. An `api/.env` from an older checkout is moved there, its old default ports switched to 7800/7802 |
| UI dependencies | `npm ci` |
| UI dev certs | Only with `--https`. Skipped by default: browsers treat `http://localhost` as a trustworthy origin, so the backend's `Secure` cookie works over plain http, and a self-signed cert just adds a warning to click through |

Database restore is a second script:

```bash
docker compose -f compose.dev.yaml up -d   # mongo, mailhog
scripts/db-restore.sh
```

It migrates the Jan-2026 backups onto the current schema and imports them. See
[Restoring the backups](#restoring-the-backups).

---

## Manual

Four things the script cannot do for you.

### 1. Install the JDK and Tesseract

Both need root. On Ubuntu 24.04 both are in apt:

```bash
sudo apt install openjdk-25-jdk
sudo apt install tesseract-ocr tesseract-ocr-eng tesseract-ocr-bul
```

Java 25 is required because the merge moved the Gradle toolchain from 21 to 25.

Tesseract is only needed for **image** receipts. PDFs take the direct LLM path
([ADR-0004](adr/0004-ocr-plus-llm-parsing.md)), so you can defer this and still
exercise most of the scan flow.

If you would rather not install a JDK at all, the build runs in a container:

```bash
docker run --rm -u "$(id -u):$(id -g)" -v "$PWD:/work" \
  -v "$HOME/.gradle-docker:/gradle-home" -e GRADLE_USER_HOME=/gradle-home \
  -w /work eclipse-temurin:25-jdk sh gradlew test
```

That is how this project's tests were first verified. It is fine for CI-style
runs, but it gives up `spring-boot-devtools` hot restart, which is most of the
value of running the backend natively.

### 2. Provide an OpenAI API key

```bash
# in .env, at the repository root
OPENAI_API_KEY=sk-...
```

Receipt parsing fails without it. Nothing else does.

### 3. Create a verified user, or restore the backup

The backup contains one already-verified account. If you would rather register
fresh, MailHog catches the verification mail at <http://localhost:7805>.

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

The Jan-2026 backups **cannot be imported as they are.** They predate three
changes: the package rename to `dev.vasoft`, the split into per-domain
packages, and the rename of `name` to `canonicalName`. They also predate
user-scoped purchases.

`scripts/migrate-backups.py` handles all of it:

| Collection | Migration |
|---|---|
| `users` (1) | `_class` rewritten. Already on the new package — this collection was re-saved after the rename, the others were not. |
| `products` (211) | `name` → `canonicalName`, plus a computed `normalizedCanonicalName`, empty alias arrays, `_class` rewritten |
| `stores` (14) | Same as products, `iconID` preserved |
| `purchases` (717) | **`userId` backfilled** from the user document, `discount` boolean → `discountAmount`, **`currency: BGN`** written explicitly, dates re-anchored from midnight Europe/Sofia to midnight UTC, `_class` rewritten |

The currency is stored rather than inferred ([SPEC §9.5](SPEC.md#95-currency-handling)).
A database restored before this change is fixed up anyway: the API's
`LegacyCurrencyBackfill` tags any currency-less purchase `BGN` at startup and
logs how many it touched. It refuses — and startup fails — if a currency-less
purchase is dated 2026-01-01 or later, since that one could be either currency.

The API also refuses to start against **unmigrated** data (`LegacyDataGuard`):
any purchase whose date is not midnight UTC, or that has no `userId`, stops
startup with the counts and a pointer back here. Both are exactly what this
migration fixes. A database the old app wrote directly will trip it: the live
one under `~/docker-apps/homeapp/db` is where the Jan-2026 backups came from,
and those had both problems (not checked on the live files themselves).
Migrate it before pointing the new API at it.

The `userId` backfill is the critical one. Nothing in the backup carries a
`userId`, and every read path scopes by it — so importing untouched gives you a
database that looks full and an app that shows an empty list.

The normalization is a port of `NormalizationService`, verified against the
real Java implementation over all 225 product and store names with zero
mismatches. It has to match exactly: the port reproduces even the quirk where
NFD decomposition and mark-stripping turn Cyrillic **Й into И**. A divergence
here would make imported products unmatchable by the scanner.

### What cannot be recovered

- **Quantity and unit on all 717 purchases.** Never stored (D1). Without it
  there is no unit price, so cross-package-size comparison stays broken for
  historical data. This is open question Q2 in the spec.
- **Discount amounts on 307 purchases.** The old schema stored a `true`/`false`
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

- The stub answers every scan with the same two-item receipt, so a run is
  free, deterministic and needs no key. That is also why it runs for
  Dependabot pull requests, which cannot read repository secrets. It says
  nothing about parsing quality; that is the harness's job (ADR-0007).
- A fresh JWT key pair is generated per run into the gitignored `e2e/certs/`.
- The api runs its default (production) profile. Only the mail protocol is
  switched to plain SMTP, because MailHog does not speak SMTPS.
- On failure the Playwright report, trace and every container's log are in
  `e2e/test-results/`; CI uploads them as the `e2e-report` artifact.

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
keeps devtools hot restart and the ui vite's hot reload. From the repository
root:

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

`receipts-dev.vasoft.dev` serves vite through nginx-proxy-manager, with
WebSockets on for hot reload; the HMR client connects back to the page's own
host and port, so vite needs no HMR settings. Three values in the root `.env`
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
`ghcr.io/veselin-antonov/receipts-ui:pr-<n>`. A bot comment on the pull request
lists the ones it built. Write the tag on the command line; `--pull always`
matters, because a preview tag is overwritten on every push.

A **ui** preview runs against the native api, on the preview ui port:

```bash
docker run --rm --pull always -p 7810:80 \
  --add-host host.docker.internal:host-gateway \
  -e BACKEND_HOST=host.docker.internal:7802 \
  ghcr.io/veselin-antonov/receipts-ui:pr-10
# click through http://localhost:7810, with http://localhost:7810 in
# APP_CORS_ALLOWED_ORIGINS
```

An **api** preview has no recipe on the dev stack: the dev MongoDB listens on
`127.0.0.1` only, and a preview must not write to the dev database anyway. e2e
covers it on the pull request; `make preview`
([ROADMAP M1b](ROADMAP.md#m1b--monorepo-development-workflow), step 4) is
where a preview stack with a database of its own comes in.
