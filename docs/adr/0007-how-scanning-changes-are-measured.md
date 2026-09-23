# ADR-0007 — Scanning changes are measured against ground truth, never L0 alone

- **Status:** accepted
- **Date:** 2026-09-23

## Context

Scanning accuracy is easy to fool yourself about. A synthetic receipt "passed"
early on while skipping the entire preprocessing pipeline. Several apparent
regressions later turned out to be run-to-run noise.

## Decision

Every change to scanning is measured by `scripts/scan-harness.py` over the full
64-fixture set in `receipt-fixtures/`, scored against `ground-truth.json`, and
compared to a recorded baseline. Three layers:

- **L0 — self-consistency.** The receipt's own arithmetic: line totals minus
  discounts must equal the printed total. Needs no ground truth.
- **L1 — headline.** Store, date, item count, total.
- **L2 — line items.** Per-item name, price, quantity, unit, discount.

**A change is only real if it moves the number by more than ±2 prices or ±3
quantities.** That floor was measured by running one identical configuration
twice, not assumed.

## Why

**L0 is necessary but not sufficient, and on some changes it is actively
misleading.** Routing photos to the vision model raised L0 from 24 to 30 while
line items with the right price fell from 253 to 165: the model fabricated
product names and attached them to correctly-read prices, and fabricated names
still reconcile to the total. L0 alone would have called that a clear win. See
ADR-0004.

The noise floor exists because a run was once spent chasing a pattern across
six "regressed" fixtures that turned out to be noise. Measuring the floor first
would have cost one extra run and saved the investigation.

Metrics must measure the thing, not a proxy for it. The harness originally
scored `date_present` — that a date came back and was not the 1970 sentinel —
which says nothing about whether the date is *correct*, and silently counted
fixtures with no ground-truth date as failures. It now scores correctness and
reports the untruthed count separately.

## Consequences

- **Ground truth is the bottleneck, and it is partly incomplete.** 29 of 56
  fixtures have no date recorded. Those are excluded from date scoring rather
  than counted as failures, so the metric is honest about its own coverage.
- Names are compared loosely and numbers strictly. Transcription error
  concentrates in Cyrillic product names, and `Р.тон в раст масло` versus
  `Р.тон в раст.масло` is a difference that matters to nobody.
- Fixtures whose own arithmetic does not reconcile are used for L0 signal only.
  Scoring line items against a label known to be wrong measures the label.
- **A full run costs real money and ~30–40 minutes.** Batch experiments; do not
  re-run the harness to confirm something a unit test can settle.
- `receipt-fixtures/` is deliberately outside git — the images are photographs
  of real purchases.
