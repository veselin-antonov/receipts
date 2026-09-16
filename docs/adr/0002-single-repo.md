# ADR-0002 — One repository for api, ui, and docs

- **Status:** accepted
- **Date:** 2026-09-16

## Context

Receipts was two repositories, `receipts-api` and `receipts-ui`, each with its
own CI, version, changelog, and GHCR image. Documentation had accumulated in
five places, partly because a spec spanning both repos had nowhere neutral to
live.

Splitting frontend from backend is a legitimate and common choice. It pays off
when the two have independent release cadences, separate teams, or separate
deployment lifecycles.

## Decision

Combine `receipts-api` and `receipts-ui` into a single `receipts` repository with
`/api`, `/ui`, and `/docs`. Preserve both histories with `git subtree`. Publish
both container images from path-filtered workflows.

Sequencing: merge the outstanding feature branches in the existing repos
**first**, then migrate with clean `master`s. Migrating first would mean
rebasing branch work across a repository restructure for no reason.

## Why

None of the usual arguments for splitting apply here, and the evidence against
came from the project's own history:

1. **They already deploy as one unit.** A single `compose.yaml` brings up api +
   ui + mongo, and the ui's nginx proxies `/api` to the api. They are one system.
2. **The independent versioning was never used.** Both repos sat at `0.0.6`.
   The cost was paid and the benefit never taken.
3. **The decisive one:** the receipt scanning feature required coordinated
   changes on both sides, and it stranded as *two separate unmerged branches in
   two separate repositories* for six months. One repo makes that failure mode
   much harder to create — one branch, one pull request, one thing to remember.
4. **The scattered docs were partly an artifact of the split.** With no neutral
   home for a cross-repo spec, it dispersed into Obsidian. The alternative was
   inventing a third repository purely to create that neutral ground.

Atomic contract changes are the standing benefit: an endpoint change and its UI
consumer land in one commit, instead of a two-pull-request dance with a window
where both `master`s are mutually inconsistent.

## Consequences

- The CI workflows must be rewritten with path filters. This is the real cost of
  the migration, and it is a mechanical rewrite rather than a redesign.
- Independent release cadence is given up. Acceptable, since it was never
  exercised.
- Both histories survive the merge; nothing is lost.
- The deployment directory, currently unversioned at `~/docker-apps/homeapp/`,
  can finally be versioned alongside the code it deploys.
- A clone is larger and the tooling roots are no longer unambiguous — `/api` is
  a Gradle project and `/ui` is an npm project, and tooling must be pointed at
  the right one.
