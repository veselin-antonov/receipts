# Deployment

`compose.yaml` runs the whole product on one host: the ui (nginx serving the
built app and proxying `/api`), the api, and MongoDB. It pulls the images CI
publishes:

| Image | Published by | Tags |
|---|---|---|
| `ghcr.io/veselin-antonov/receipts-api` | `.github/workflows/api.yml`, `release.yml` | `dev` and `sha-<sha>` from master; `<version>` and `latest` on release |
| `ghcr.io/veselin-antonov/receipts-ui` | `.github/workflows/ui.yml`, `release.yml` | the same, plus `pr-<n>` previews |

The compose file tracks `:dev`, i.e. whatever master last built.

## Secrets stay out of git

Everything secret or host-specific is read from `.env` next to `compose.yaml`,
which is gitignored, as is `*.pem`. Start from the template:

```bash
cp deploy/example.env deploy/.env   # then fill it in
docker compose -f deploy/compose.yaml up -d
```

`example.env` lists every variable the three services read, with the optional
ones commented out at their defaults.

## Where it actually runs

The live deployment is `~/docker-apps/homeapp/` on the home server, which holds
its own copy of `compose.yaml` beside the real `.env`, the JWT key pair and the
Mongo data directory. This directory is the versioned source of that file: change
it here, then copy it there. Pointing the live directory at a checkout of this
repository instead is a later step.

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

## After a deployment

- Log in (`POST /api/auth/token`), then `GET /api/auth/status` answers with the
  session's remaining time.
- Register a user, and check that the verification mail arrives and verifies.
- Add a purchase and list purchases; another user must not see it.
- Scan an image receipt and a PDF, and submit the reviewed rows.

What usually goes wrong: a JWT key that does not parse, SMTP credentials or
TLS mode, the MongoDB auth database, the tessdata path outside the image, the
cookie lost behind a proxy or on a non-https origin, and no OpenAI key.
