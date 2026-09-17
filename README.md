# Receipts

A personal grocery price tracker.

You scan receipts at home so that, standing in a store, you can answer one question
in a couple of seconds: **is this a good price?**

## What this repo is

This is the home of the Receipts product. Today it holds the specification; the
`receipts-api` and `receipts-ui` codebases merge in here as `/api` and `/ui`
once the current feature branches have landed (see
[ADR-0002](docs/adr/0002-single-repo.md)).

## Reading order

| # | Document | What it answers |
|---|---|---|
| 1 | [docs/SPEC.md](docs/SPEC.md) | What we are building and why. **The spec we follow.** |
| 2 | [docs/STATE.md](docs/STATE.md) | What actually exists today, and what is broken. |
| 3 | [docs/DEV_SETUP.md](docs/DEV_SETUP.md) | How to get it running locally, and what is automated. |
| 4 | [docs/SCANNING_PATHS.md](docs/SCANNING_PATHS.md) | The three routes an uploaded receipt can take, and why they differ. |
| 5 | [docs/ROADMAP.md](docs/ROADMAP.md) | The order we build it in. |
| 6 | [docs/adr/](docs/adr/) | Decisions, with the reasoning that produced them. |

## Documentation ownership

One rule, to stop docs scattering again:

> **If a document changes because code changed, it lives in this repo.
> If it changes because an idea changed, it lives in Obsidian.**

| Kind | Home |
|---|---|
| Product spec, roadmap, decisions | `docs/` here |
| Build / run / test instructions | `api/README.md`, `ui/README.md` |
| API contracts | `docs/` here, next to the spec |
| Release notes | `CHANGELOG.md` + GitHub releases |
| Raw ideas, not-yet-built modules | Obsidian `Home App/` |

Receipts is **not** part of the Home App suite — see
[ADR-0001](docs/adr/0001-standalone-from-home-app.md). The Home App notes in
Obsidian remain the home for Discounts, Tools & Appliances, and Outfit Wear.
