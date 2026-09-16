# ADR-0001 — Receipts ships standalone, outside the Home App suite

- **Status:** accepted
- **Date:** 2026-09-16

## Context

Home App was conceived as an umbrella product for household records, with
Receipts as its first and only implemented module. Planned siblings: Discounts,
Tools & Appliances, Outfit Wear, Home Products. Mockups existed for a suite
shell — a sidebar with module groups and a dashboard landing page.

The receipts slice is the only one with code, and it has 14 months of real data
behind it. The others are single-page idea notes.

## Decision

Receipts ships as its own product. It is not a module of Home App, does not live
behind a suite shell, and does not share a navigation surface with the other
planned modules.

The Home App suite remains a separate, later project. Its notes stay in Obsidian
until it actually starts.

## Why

The deciding factor is the primary use case. Receipts is used **standing in a
shop, one-handed, deciding whether to put something in a basket.** That
interaction has a budget of a few seconds.

A suite shell is the wrong container for it:

- Every layer of module navigation is friction on the one path that must be
  instant.
- The other modules are browse-and-manage tools with no latency pressure. They
  have genuinely different design constraints, and averaging the two produces
  something mediocre for both.
- Receipts wants to be an installed, offline-capable, phone-first thing. A
  household records suite wants to be a desktop web app.

The suite shell mockup was also rejected on its own merits — it will be
redesigned rather than adapted.

## Consequences

- The Home App vision notes are demoted to parked ideas. No work is scheduled
  against them.
- Receipts gets its own spec, roadmap, and release cadence — this repository.
- The `dev.vasoft.homeapp` Java package name and the `homeapp-ui` container name
  are now misleading. Renaming is deferred as Q5/Q4 in the spec; it is invasive
  and buys nothing functional.
- If Home App is built later, it will need to decide whether to link out to
  Receipts or duplicate a thin read-only view of it. Linking out is the
  expectation.
- When Home App does start, it is a modular monolith — one Spring Boot app with
  a package per module — not the five-microservice split the old roadmap
  proposed. Five deploys and five pipelines for a single household is overhead
  with no return.
