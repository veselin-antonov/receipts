# Deployment

`compose.yaml` runs the whole product on one host, pinned to a release: the ui
(nginx serving the built app and proxying `/api`), the api, and MongoDB. Only
the ui is published on the host, on `UI_PORT` (7820, the production block of
the port scheme); the api and MongoDB are reachable only inside the stack. A
reverse proxy terminates https in front of the ui.

## Image tags

| Tag | Means | Published by |
|---|---|---|
| `<version>`, e.g. `0.1.0` | a release; **production runs one**, written in `compose.yaml` | `release.yml`, run by hand after the version-bump pull request |
| `latest` | the newest release; nothing in this repository runs it | `release.yml` |
| `dev` | whatever master last built; the preview stack's fallback | `api.yml`, `ui.yml` on every push to master |
| `sha-<sha>` | one master build | `api.yml`, `ui.yml` |
| `pr-<n>` | a pull request's preview, for `make preview PR=<n>` | `ci.yml`, for this repository's branches |

for both `ghcr.io/veselin-antonov/receipts-api` and `receipts-ui`. Upgrading
production is a release, then a pull request raising both images in
`compose.yaml` to it.

## Secrets stay out of git

Everything secret or host-specific is read from `.env` next to `compose.yaml`,
which is gitignored, as is `*.pem`. Start from the template:

```bash
cp deploy/example.env deploy/.env   # then fill it in
docker compose -f deploy/compose.yaml up -d
```

`example.env` lists every variable the stack reads, with the optional ones
commented out at their defaults.

## Database logins

MongoDB runs with `--auth`, and production has three logins, all in its
`admin` database (they live in the data directory, not on the host):

| Login | Roles | Used by |
|---|---|---|
| `MONGODB_ROOT_USER` | `root` | administration by hand only |
| `MONGODB_USER` (`receipts-api`) | `readWrite` on `MONGODB_DATABASE` | the api |
| `receipts-reader` | `read` on `MONGODB_DATABASE` | `scripts/db-copy.sh`, i.e. `make db-refresh` and `make preview` |

`readWrite` covers everything the api does, including creating its indexes at
startup. On an empty `MONGODB_DATAPATH`, MongoDB creates root, then runs the
`create-users.js` config inline in `compose.yaml`, which creates the other two
from `MONGODB_USER` / `MONGODB_PASS` and `MONGODB_READER_PASS`. On a data
directory that already exists, create them once by hand, as root:

```bash
docker exec -it receipts-db sh -c 'mongosh -u "$MONGO_INITDB_ROOT_USERNAME" \
  -p "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin \
  "$MONGO_INITDB_DATABASE" /docker-entrypoint-initdb.d/create-users.js'
```

The reader's password reaches `db-copy.sh` only through the production
container's environment; CI never uses any of these, since e2e runs a MongoDB
of its own with throwaway credentials.

## Where it actually runs

The server keeps its own copy of `compose.yaml` beside its `.env` and the Mongo
data directory. This directory is the versioned source of that file: change it
here, then copy it there.

## Its data, and the copies

Production's database is the source of truth. The others are copies, made with
`scripts/db-copy.sh` (`mongodump | mongorestore --drop`), which reads production
as `receipts-reader`, so MongoDB refuses any write from it, and refuses
production as a target:

- `make db-refresh` replaces the dev database with a copy of production's.
- `make preview` fills the preview database from production on its first run
  and with `REFRESH=1`.

## What the api needs at runtime

The api runs its default profile, `application.yaml`, configured from the
environment. Beyond the images it needs MongoDB, an SMTP server (SMTPS by
default), an OpenAI API key and an RSA JWT key pair.

- **JWT keys** are PEM content, or a `file:` path to a mounted file:

  ```bash
  JWT_PRIVATE_KEY='-----BEGIN PRIVATE KEY-----...'
  JWT_PUBLIC_KEY='file:/etc/secrets/public.pem'
  ```

  Never bake them into an image: the build refuses a jar containing a `.pem`
  or `.env` (D10).
- **OCR** is in the api image: `api/Dockerfile` starts from
  `eclipse-temurin:25-jre-alpine` and installs Tesseract with English and
  Bulgarian data, and `TESSDATA_PATH` defaults to the image's
  `/usr/share/tessdata`. Running the jar outside the image needs Tesseract, the
  language packs and that path. Leave `OCR_DEBUG_OUTPUT_PATH` empty unless you
  are diagnosing OCR and have somewhere to keep the images.
- **HTTPS in front.** The auth cookie is `HttpOnly`, `Secure` and
  `SameSite=Strict`, so the ui must be served over https on one origin, with
  `/api` proxied by its nginx. `APP_HOST_URL` is that origin, and it goes in
  `APP_CORS_ALLOWED_ORIGINS`.
- **Upload size:** `MAX_UPLOAD_MB` (25) sets the api's multipart limit and the
  ui's nginx `client_max_body_size`; keep the two services equal. Scanning has
  a strict rate limit, and the sensitive endpoints per-IP and per-user ones.

## One-off data migrations

Some releases carry a startup migration that needs a person's review before it
writes anything. The api reads and writes its review files in `migrated/`,
beside `compose.yaml` (mounted at `/app/.migrated`). Those files hold personal
data, so they never go into git.

- **Legacy quantity** (`LegacyQuantityMigration`, SPEC §6): while any
  purchase has no quantity, the first start writes
  `migrated/legacy-quantity-units.proposed.tsv` and logs a dry-run report,
  without writing to the database. Review the unit column, save the file as
  `migrated/legacy-quantity-units.tsv`, and restart the api to apply it.

## After a deployment

- Log in (`POST /api/auth/token`), then `GET /api/auth/status` answers with the
  session's remaining time.
- Register a user, and check that the verification mail arrives and verifies.
- Add a purchase and list purchases; another user must not see it.
- Scan an image receipt and a PDF, and submit the reviewed rows.

What usually goes wrong: a JWT key that does not parse, SMTP credentials or
TLS mode, the MongoDB auth database, the tessdata path outside the image, the
cookie lost behind a proxy or on a non-https origin, and no OpenAI key.
