# Receipts

A personal grocery price tracker.

You scan receipts at home so that, standing in a store, you can answer one question
in a couple of seconds: **is this a good price?**

## What this repo is

The whole Receipts product in one repository (see
[ADR-0002](docs/adr/0002-single-repo.md)):

| Path | What | Tooling |
|---|---|---|
| [`api/`](api) | Spring Boot backend — auth, purchases, receipt scanning | Gradle, Java 25 |
| [`ui/`](ui) | React + Vite frontend, served by nginx | npm, Node 22 |
| [`docs/`](docs) | Spec, roadmap, decisions, cross-cutting docs | — |
| [`deploy/`](deploy) | The compose file that runs it | Docker Compose |
| [`scripts/`](scripts) | Dev setup, backup restore, scan harness, upload-limit test | bash, Python |

`api` and `ui` were separate repositories (`receipts-api`, `receipts-ui`) until
2026-09; both histories were carried over with `git subtree`. There is one
[`VERSION`](VERSION) and one [`CHANGELOG.md`](CHANGELOG.md) for both.

## Reading order

| # | Document | What it answers |
|---|---|---|
| 1 | [docs/SPEC.md](docs/SPEC.md) | What we are building and why. **The spec we follow.** |
| 2 | [docs/STATE.md](docs/STATE.md) | What actually exists today, and what is broken. |
| 3 | [docs/DEV_SETUP.md](docs/DEV_SETUP.md) | How to get it running locally, and what is automated. |
| 4 | [docs/SCANNING_PATHS.md](docs/SCANNING_PATHS.md) | The three routes an uploaded receipt can take, and why they differ. |
| 5 | [docs/ROADMAP.md](docs/ROADMAP.md) | The order we build it in. |
| 6 | [docs/adr/](docs/adr/) | Decisions, with the reasoning that produced them. |
| 7 | [docs/ARCHITECTURE_AND_FLOWS.md](docs/ARCHITECTURE_AND_FLOWS.md) | The API's components and request flows. |
| 8 | [docs/API_CONTRACTS.md](docs/API_CONTRACTS.md) | Request and response shapes, endpoint by endpoint. |
| 9 | [docs/RECEIPT_SCANNING.md](docs/RECEIPT_SCANNING.md) | The scanning feature: OCR, LLM parsing, matching, submit. |

## Documentation ownership

One rule, to stop docs scattering again:

> **If a document changes because code changed, it lives in this repo.
> If it changes because an idea changed, it lives in Obsidian.**

| Kind | Home |
|---|---|
| Product spec, roadmap, decisions | `docs/` here |
| Build / run / test instructions | `api/README.md`, `ui/README.md`, `api/docs/` |
| API contracts | `docs/` here, next to the spec |
| Release notes | `CHANGELOG.md` + GitHub releases |
| Raw ideas, not-yet-built modules | Obsidian `Home App/` |

Receipts is **not** part of the Home App suite — see
[ADR-0001](docs/adr/0001-standalone-from-home-app.md). The Home App notes in
Obsidian remain the home for Discounts, Tools & Appliances, and Outfit Wear.
