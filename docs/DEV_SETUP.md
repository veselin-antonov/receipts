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
| `TZ=UTC` | Pinned deliberately — see [Timezone](#timezone-this-one-matters) |
| Compose `dev.env` | Copies the env file where `docker-compose.dev.yml` expects it |
| UI dependencies | `npm ci` |
| UI dev certs | Only with `--https`. Skipped by default: browsers treat `http://localhost` as a trustworthy origin, so the backend's `Secure` cookie works over plain http, and a self-signed cert just adds a warning to click through |

Database restore is a second script:

```bash
docker compose -f ../receipts-api/docker-compose.dev.yml up -d   # mongo + mailhog
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
# in receipts-api/.env
OPENAI_API_KEY=sk-...
```

Receipt parsing fails without it. Nothing else does.

### 3. Commit the `gradlew` mode fix

```bash
cd receipts-api
git update-index --chmod=+x gradlew
git commit -m "fix: make gradlew executable"
```

The script sets the bit locally so you can work, but the next fresh clone hits
the same wall until this is committed.

### 4. Create a verified user, or restore the backup

The backup contains one already-verified account. If you would rather register
fresh, MailHog catches the verification mail at <http://localhost:8025>.

---

## Timezone: this one matters

`scripts/dev-setup.sh` pins `TZ=UTC`, and that is not cosmetic.

Every one of the 717 purchase dates in the backup is **midnight Europe/Sofia
stored as a UTC instant** — 22:00Z in winter, 21:00Z in summer. The `Purchase`
entity maps `date` to a `LocalDate`, so the zone the JVM runs in decides which
calendar day you read back:

- in `Europe/Sofia`, the original date
- in `UTC`, **one day earlier, for all 717 rows**

The Dockerfile is `eclipse-temurin:25-jre-alpine` with no `TZ` set, so
containers default to UTC. `migrate-backups.py` therefore re-anchors every date
to midnight UTC, which makes the value mean the same thing in every zone — on
the condition that the app also runs in UTC.

**So: pin `TZ=UTC` in the Dockerfile and in compose too.** Mixing a
UTC-anchored database with a Sofia-local JVM reintroduces the same off-by-one
in the other direction.

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
| `purchases` (717) | **`userId` backfilled** from the user document, `discount` boolean → `discountAmount`, dates re-anchored, `_class` rewritten |

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

Both files are gitignored, so they never reach git — but they do reach the
**jar**, and `./gradlew buildImage` bakes that jar into a container image that
`publishImage` pushes to GHCR.

CI is not affected: it builds from a fresh checkout where neither file exists.
The exposure is building and pushing an image **from a developer machine**,
which would ship the JWT signing key and live credentials to a registry.

Fix: exclude `certs/**` and `*.env` from `processResources`, and move dev keys
out of the resources tree, referencing them with `file:` rather than
`classpath:`.

### docker-compose.dev.yml rough edges

- `receipts-db` declares no `networks`, so it sits on `default` while `mailhog`
  and `homeapp-ui` are on `homeapp-network`. Harmless in the hybrid workflow,
  because the backend runs on the host and reaches Mongo through its published
  port — but it would not work container-to-container.
- `homeapp-ui` declares `depends_on: receipts-db`. The UI does not talk to
  Mongo; it needs the API, which is not in this compose file at all.
- The compose `env_file` points at `src/main/resources/dev.env`, inside the Java
  resources tree. That is what causes half of D10. Compose config does not
  belong there.
- `example.env` is missing the OCR, CORS, `UI_PORT`, and `BACKEND_HOST`
  variables that the app and compose actually read.
