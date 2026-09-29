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
| 2 | [docs/DEV_SETUP.md](docs/DEV_SETUP.md) | How to get it running locally, and what is automated. |
| 3 | [docs/SCANNING_PATHS.md](docs/SCANNING_PATHS.md) | The three routes an uploaded receipt can take, and why they differ. |
| 4 | [docs/ROADMAP.md](docs/ROADMAP.md) | The order we build it in. |
| 5 | [docs/adr/](docs/adr/) | Decisions, with the reasoning that produced them. |
| 6 | [docs/ARCHITECTURE_AND_FLOWS.md](docs/ARCHITECTURE_AND_FLOWS.md) | The API's components and request flows. |
| 7 | [docs/API_CONTRACTS.md](docs/API_CONTRACTS.md) | Request and response shapes, endpoint by endpoint. |
| 8 | [docs/RECEIPT_SCANNING.md](docs/RECEIPT_SCANNING.md) | The scanning feature: OCR, LLM parsing, matching, submit. |

## Documentation ownership

One rule, to stop docs scattering again:

> **If a document changes because code changed, it lives in this repo.
> If it changes because an idea changed, it lives in Obsidian.**

And one for what goes in them:

> **Docs describe how things are now.** History belongs in `CHANGELOG.md`, the
> ADRs and git: no "Fixed on …" banners over stale text, no "until …, it used
> to …" asides, no one-time migration steps. Keep a sentence about the past only
> if it stops someone undoing a choice, and then say it as the reason, not the
> story. A fixed defect in SPEC §7 shrinks to one line; dated measurements stay,
> because the date is part of the result.

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
