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
3. The `dev` profile resolves `jwt.private-key` to `classpath:certs/private.pem`,
   a gitignored path that nothing creates.

None of this is documented as a test prerequisite, and CI never runs tests
(D8), so nothing catches it.

---

## Automated

| Step | What the script does |
|---|---|
| Prerequisite audit | Checks java 25, node, docker, openssl, tesseract and its `eng`/`bul` language data; reports what is missing instead of guessing |
| `gradlew` | Sets the executable bit, and warns that the fix needs committing |
| JWT keys | Generates a PKCS#8 RSA keypair into the gitignored `certs/` |
| Backend `.env` | Writes all 12 undefaulted variables, plus Linux overrides for the OCR paths that `application-dev.yaml` defaults to Windows locations |
| UI dependencies | `npm ci` |
| UI dev certs | Only with `--https`. Skipped by default: browsers treat `http://localhost` as a trustworthy origin, so the backend's `Secure` cookie works over plain http, and a self-signed cert just adds a warning to click through |

Database restore is a second script:

```bash
docker compose -f api/docker-compose.dev.yml up -d   # mongo, mailhog, ui on :7863
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
# in api/.env
OPENAI_API_KEY=sk-...
```

Receipt parsing fails without it. Nothing else does.

### 3. Create a verified user, or restore the backup

The backup contains one already-verified account. If you would rather register
fresh, MailHog catches the verification mail at <http://localhost:8025>.

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
- in a container, set `TZ` on the API service. `docker-compose.yml` does,
  defaulting to `Europe/Sofia`; the image on its own logs in UTC

Spring Boot's log format includes the offset (`…+03:00`), so local-time logs
still line up unambiguously with UTC data.

History, because it explains the migration: the Jan-2026 backup stored each
date as midnight Europe/Sofia expressed in UTC (21:00Z/22:00Z), since the old
app mapped dates through the JVM's zone. `migrate-backups.py` re-anchors them
to midnight UTC. Before `MongoConfig`, correctness depended on running the JVM
in UTC; a Sofia-time JVM stored 2026-09-23 as `2026-09-22T21:00Z`. `TZ` in
`.env` never helped with that, because `spring-dotenv` turns `.env` into Spring
properties, not process environment.

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
| `purchases` (717) | **`userId` backfilled** from the user document, `discount` boolean → `discountAmount`, **`currency: BGN`** written explicitly, dates re-anchored, `_class` rewritten |

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
E2E_UI_PORT=18780 E2E_MAILHOG_PORT=18725 e2e/run.sh           # next to the dev stack
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

## Known environment defects

Found while building this setup. Tracked in
[SPEC §7](SPEC.md#7-known-defects-that-block-the-product) and
[ROADMAP M0](ROADMAP.md#m0--recover-the-stranded-work).

### D10 — Secrets are packaged into the jar

`processResources` copies everything under `src/main/resources` into the build
output. Verified contents:

```text
build/resources/main/certs/private.pem   the RSA JWT signing key
build/resources/main/dev.env             MONGODB_PASS, SMTP_PASS, OPENAI_API_KEY
```

`dev.env` is no longer written since 2026-09-25 — the dev compose file reads
`api/.env` for interpolation instead — so the signing key is what remains.

Both files are gitignored, so they never reach git — but they do reach the
**jar**, and `./gradlew buildImage` bakes that jar into a container image that
`publishImage` pushes to GHCR.

CI is not affected: it builds from a fresh checkout where neither file exists.
The exposure is building and pushing an image **from a developer machine**,
which would ship the JWT signing key and live credentials to a registry.

Fix: exclude `certs/**` and `*.env` from `processResources`, and move dev keys
out of the resources tree, referencing them with `file:` rather than
`classpath:`.

### The dev stack: `api/docker-compose.dev.yml`

MongoDB, MailHog and the ui image. The api is not in it; it runs natively with
`./gradlew bootRun --args=--spring.profiles.active=dev`, so it keeps devtools
hot restart.

- Compose interpolates from `api/.env`, which it reads because the file sits
  next to it. Nothing is passed to a container with `env_file`, so the OpenAI
  key never enters one, and there is no compose config in the Java resources
  tree any more.
- The ui container's nginx proxies `/api/` to `host.docker.internal:7002`, the
  natively running api. Until 2026-09-25 it pointed at `localhost:7002`, which
  inside the container is the container itself, so every `/api` call through
  <http://localhost:7863> was a 502.
- MongoDB is pinned to a major (`mongo:8.2`). `latest` can move a major on a
  pull, and mongod refuses data files more than one feature-compatibility
  version behind.
- Data lives in `api/.mongo-data` (gitignored, owned by the container's uid).

#### Testing a ui pull request on its preview image

Every ui pull request publishes `ghcr.io/veselin-antonov/receipts-ui:pr-<n>`.
`UI_TAG` swaps it in against the same api and data, and leaves the database
container alone:

```bash
docker compose -f api/docker-compose.dev.yml pull receipts-ui   # refresh :dev first
UI_TAG=pr-10 docker compose -f api/docker-compose.dev.yml up -d --pull always receipts-ui
# click through http://localhost:7863
docker compose -f api/docker-compose.dev.yml up -d receipts-ui  # back to master's :dev
```

`--pull always` matters: the preview tag is overwritten on every push to the
pull request.

`example.env` is still missing the OCR and CORS variables the app reads.
