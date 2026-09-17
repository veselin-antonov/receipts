# Scanning: the three paths

> Written because this keeps getting rediscovered. An uploaded file can take
> one of **three** routes, chosen by two separate branches, and they behave
> differently enough that "the scan works" is never a meaningful statement on
> its own — it is only ever true of one path.

## The routing

```text
POST /api/receipts/scan
        │
        ▼
  validateFile()          ≤10 MB; JPEG, JPG, PNG, GIF, WebP, PDF
        │
        ▼
  isImage(file)?          ← branch 1: contentType in IMAGE_CONTENT_TYPES
        │
   ┌────┴─────────────────────────────┐
   │ no                               │ yes
   ▼                                  ▼
┌─────────────┐              isScreenshot(file)?   ← branch 2: contentType
│  PATH A     │                       │               == "image/png"
│  PDF / LLM  │            ┌──────────┴──────────┐
│  document   │            │ yes                 │ no
└─────────────┘            ▼                     ▼
                    ┌─────────────┐      ┌──────────────┐
                    │  PATH B     │      │  PATH C      │
                    │  OCR, raw   │      │  OCR, pre-   │
                    │  no preproc │      │  processed   │
                    └─────────────┘      └──────────────┘
        │                   │                     │
        └───────────────────┴─────────────────────┘
                            ▼
              MatcherService.matchStore(...)
              MatcherService.matchProductsToPurchases(...)
                            ▼
                      ResScanResult
```

Both branches key off **`contentType` alone**. Neither looks at the image.

---

## Path A — PDF, straight to the LLM

```text
file → LlmReceiptParser.parseReceipt(file) → ParsedReceipt
```

No OCR, no preprocessing. The file goes to the model's document/vision path
with its own prompt, separate from the OCR-text prompt.

Rationale in [ADR-0004](adr/0004-ocr-plus-llm-parsing.md): PDFs are already
clean extractable text, and OCR would add noise for nothing.

**Reached by:** anything whose content type is not in `IMAGE_CONTENT_TYPES`.
After `validateFile` that means PDF, but note the code's condition is "not an
image", not "is a PDF".

**Status: never executed.** Not once, in any session. Entirely unverified.

---

## Path B — PNG, OCR with no preprocessing

```text
file → EXIF orientation → Tesseract (raw) → LlmReceiptParser.parseReceiptText
```

`isScreenshot()` returns true for any `image/png`, and the image goes to
Tesseract untouched.

**Reached by:** any PNG. The intent is screen captures, which have crisp
rendered text that sharpening and binarization would only damage.

**Status: this is the path the synthetic test actually took.** The test image
was written as `receipt.png`, so the "end-to-end scan verified" result came
from here — the preprocessing code never ran. Worth remembering when reading
any earlier claim that scanning works.

**Note:** `saveDebugImage` is only called on Path C, so a Path B failure leaves
no debug artefact.

---

## Path C — other images, OCR with preprocessing

```text
file → EXIF orientation → preprocessImage() → Tesseract → parseReceiptText

preprocessImage:  upscale → grayscale → sharpen → otsuBinarize
```

**Reached by:** JPEG, JPG, GIF, WebP. In practice: every phone photo, which is
the primary way receipts actually arrive.

**Status: broken on real photos.** See D17. The first real receipt through this
path returned zero items. The pipeline ends in **global Otsu binarization**,
which is unsound for photos with a background (see below).

**Also note:** the `OcrService` class comment claims the pipeline uses
"adaptive binarization". It does not — `preprocessImage` calls `otsuBinarize`,
which is global. The comment has been wrong long enough to mislead.

---

## What Otsu is, and why it fails here

Otsu's method picks **one** brightness cut-off for the **whole image**. It
scans every possible threshold and chooses the one that best splits pixels into
two groups — maximising the variance *between* the groups while minimising it
*within* each. It is fully automatic, which is its appeal.

It rests on one assumption: the histogram is **bimodal** — two clear peaks, one
for dark ink, one for light paper.

A receipt photographed on a table breaks that assumption, because the image is
not ink-and-paper. It is three things: ink, paper, and **table**. Observed on
the real receipt:

- the chosen threshold was **139**, which separated *receipt from wood*, not
  *ink from paper*
- wood grain straddles that cut-off, so the grain became high-contrast striping
  across most of the frame
- Tesseract ran layout analysis over that and read the texture: **10,911
  characters** from a receipt of roughly twenty lines, with **none** of the real
  text surviving

Being *global* is the second problem. One threshold cannot serve a receipt with
a shadow falling across it, or the curl most receipts have — the lit half and
the shadowed half need different cut-offs.

**The alternative is local (adaptive) thresholding** — Sauvola or Niblack are
the standard choices for document images. These compute a threshold per small
neighbourhood from its own local mean and variance, so a shadow simply shifts
the local threshold with it.

**But the stronger move is to binarize less, not better.** Tesseract 4 and 5
binarize internally, per region. Handing it a pre-binarized 1-bit image throws
away information it would otherwise have used. Passing clean **grayscale** and
letting Tesseract decide is often better than any hand-rolled binarization, and
it is worth measuring before investing in Sauvola.

Cropping to the receipt first matters more than either choice. While 80% of the
frame is furniture, no thresholding strategy can save it.

---

## Telling a screenshot from a photo

`isScreenshot()` uses the file extension, which carries no information about
how an image was produced (D19). Real signals, most to least reliable:

| Signal | Why it works | Caveat |
|---|---|---|
| **Distinct colour count** | Rendered text uses a handful of colours; photos have tens of thousands | Very strong, and cheap to compute |
| **Perfectly uniform regions** | Screenshots contain runs of byte-identical pixels; camera sensors essentially never produce them | Strong |
| **EXIF camera tags** | `Make`, `Model`, `ExposureTime`, `ISO`, `FNumber` mean a camera | **Stripped by messaging apps**, so absence proves nothing |
| **Sensor noise** | Photos carry high-frequency noise in flat areas; screenshots are noiseless | Reliable, slightly more work |
| **Edge profile** | Rendered glyph edges are crisp; photographed ones are blurred by optics | Reliable |
| **Dimensions match a known screen size** | 1179×2556 and friends | Weak alone, fine as a tie-breaker |

EXIF is nearly free here, since `OcrService` already reads EXIF for
orientation. Colour count plus uniform-region detection is the strongest cheap
pair.

### The better answer: stop needing the branch

The classification only exists because the preprocessing is **destructive**. A
pipeline that is safe on both kinds of input needs no classification at all:

- **crop to the document** — on a screenshot the document is the whole frame, so
  cropping is close to a no-op
- **adaptive thresholding, or no thresholding** — on already-clean rendered text
  this is near-identity, rather than the damage sharpening plus global Otsu does

Fix the preprocessing and Path B and Path C converge. That removes a branch, a
misclassification bug, and a whole category of "it works on my file" — which is
worth more than making the detection cleverer.

---

## Testing implications

The paths fail differently, which is diagnostically useful:

| Symptom | Likely path and cause |
|---|---|
| PDFs work, images fail | OCR, Tesseract runtime, or tessdata |
| Images work, PDFs fail | LLM client, API key, or the document prompt |
| PNG works, JPEG fails | Preprocessing (D17) — the PNG skipped it |
| Everything returns empty | Check whether the parse actually failed; a total failure currently returns **HTTP 200** with an empty list and a 1970 epoch date |

A fixture set needs all three paths represented, plus the deliberately
mismatched cases — a photo exported as PNG, and a screenshot saved as JPEG —
because those are what expose D19. See `receipt-fixtures/README.md`.
