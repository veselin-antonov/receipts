# ADR-0005 — The lookup loop is a PWA with an offline cache

- **Status:** accepted
- **Date:** 2026-09-16

## Context

The primary use case is checking a price while standing in a shop. That means a
phone, one hand, a few seconds of patience, and frequently **no usable mobile
signal** — supermarkets are steel and concrete boxes.

The current UI is a desktop-oriented SPA served by nginx, loaded fresh from the
network every time.

## Decision

The lookup loop is delivered as a Progressive Web App: installable to the home
screen, with a service worker that caches the full product and price dataset
locally. Search, price statistics, unit prices, and purchase history all work
with no network connection.

A `GET /api/lookup/snapshot` endpoint serves the whole dataset in one payload
for the cache to store.

## Why

The dataset makes this nearly free. **211 products, 14 stores, 717 purchases** —
well under a megabyte of JSON. There is no pagination problem, no sync protocol,
no partial-cache complexity. The entire corpus fits in memory on any phone.

Given that, the alternatives are hard to justify:

- **Responsive web only** — a cold network fetch every time the app opens, in
  exactly the environment where the network is least reliable. This is the
  failure mode that makes someone stop using the app.
- **A native app** — app store overhead, a second toolchain, a second codebase,
  for a problem a service worker solves.

An installed icon also removes the browser, the URL bar, and the tab switching
from the critical path.

## Consequences

- **The wire format must be data, not presentation.** An offline cache full of
  `"3,29 лв."` strings cannot compute a minimum or an average. This is the
  practical reason D2 in the spec is critical rather than cosmetic.
- **Statistics must be computable client-side,** or precomputed into the
  snapshot. Either is fine at this size; it must be decided when M3 is built.
- **Cache staleness is acceptable here.** Prices are historical, and a snapshot
  a few days old gives essentially the same verdict. Refresh opportunistically
  when there is signal; never block the UI on it.
- **Capture stays online.** Scanning needs OCR and an LLM call, both
  server-side. Only the lookup loop is offline-capable, which is the correct
  split — capture happens at home on wifi.
- Service worker lifecycle and cache invalidation are new complexity in the UI
  build, and a stale service worker serving stale assets is a genuine and
  annoying class of bug.
