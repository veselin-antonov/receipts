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
  orientation and rotate, and it treats PNGs as screenshots that should skip the
  heavy preprocessing that helps camera photos. This is real complexity that
  the vision-only approach would not have.
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
