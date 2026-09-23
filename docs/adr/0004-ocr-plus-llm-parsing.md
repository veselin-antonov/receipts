# ADR-0004 — OCR for images, direct LLM for PDFs

- **Status:** accepted
- **Date:** implemented June 2026, recorded 2026-09-16

## Context

A receipt arrives as either a phone photo or a PDF, and must become structured
rows: product name, price, quantity, unit, discount, plus the store and date.

The obvious approach is to hand the file to a multimodal model and ask for JSON.

## Decision

Route by file type:

- **Images** (JPEG, PNG, GIF, WebP) → Tesseract OCR via Tess4J → the extracted
  text goes to the LLM with a text-parsing prompt.
- **PDFs** → straight to the LLM's document/vision path, no OCR.

Two different prompts, one for each input mode. `ReceiptScanService` owns the
routing.

## Why

Vision models misread digits on receipt photos — crumpled thermal paper, low
contrast, skew, glare. Getting a product *name* slightly wrong is recoverable
because the user reviews it; getting a *price* wrong produces silently corrupt
price history, which is the one thing this product cannot tolerate. OCR is more
reliable on digits, and the LLM is still valuable for turning OCR's flat text
into receipt structure.

PDFs are already clean, extractable text. Running OCR over a rendered PDF adds
noise and latency for no gain.

## Consequences

- **A native dependency.** Tesseract must be installed in every environment,
  with `eng+bul` language data, and `TESSDATA_PATH` pointing at it. The
  Dockerfile handles this; local development has to be set up by hand, and it is
  the most common cause of "PDFs work but images fail".
- **Image preprocessing is its own problem.** `OcrService` has to read EXIF
  orientation and rotate, crop to the paper, and binarize. This is real
  complexity that the vision-only approach would not have. It once also
  branched on whether an upload "looked like a screenshot"; that branch was
  removed in September 2026 because neither signal it used (the PNG extension,
  then a colour count) actually separated the two classes.
- **Two prompts to maintain.** A change to receipt parsing behaviour usually
  means touching both.
- **The two paths fail differently, which is a diagnostic asset:** images
  failing while PDFs work points at OCR and the native runtime; PDFs failing
  while images work points at the LLM client, key, or prompt.
- Each scan costs money at the provider, so the scan endpoint is rate-limited
  far more aggressively than ordinary API traffic.
- Parsed output is never persisted directly. `/scan` returns a review payload
  and `/submit` writes, because neither OCR nor the LLM is trustworthy enough to
  save unreviewed.

## Confirmed by measurement, 2026-09-23

This was challenged - if PDFs go to vision and photos do not, Tesseract is the
ceiling on every photo - and re-tested by routing photos to vision too, on all
64 fixtures with the model, reasoning effort and prompt held constant.

Restricted to the 42 fixtures scored in both runs:

| | OCR text | vision |
|---|---|---|
| items with the right price | **253** | **165** |
| line items matched | 263 | 179 |
| ground-truth prices present anywhere in the output | 75% | 69% |

The decision stands. **The reasoning above was right in conclusion and wrong in
mechanism**, which matters for anyone re-testing it later. This ADR predicted
that vision would misread digits and that names would be the recoverable part.
The opposite happened: vision read the price column about as well as OCR did,
and fabricated the *names*. From `kaufland_17_flat.jpeg`, identical prices in
identical positions, `Жарено филе, кг` came back as `Картофи фини, кг` and
`DrKeskin св трици 200` as `Бисквити`.

So the real argument for OCR is not digit accuracy. It is that OCR fails
*legibly* - `Drkeskin ов трици200` is mangled but matchable - while vision
fails *plausibly*, and a confident wrong product name is unreviewable. The user
cannot catch an error that looks like a correct answer.

Re-test before assuming this still holds for a newer model:
`receipts.scanning.vision-for-images=true` in `receipts-api`. Score against
ground-truth names, never L0 - see ADR-0007.

