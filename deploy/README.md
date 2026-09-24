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
