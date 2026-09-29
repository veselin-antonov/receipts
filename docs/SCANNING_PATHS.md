# Scanning: how an upload becomes a review payload

`POST /api/receipts/scan` takes one file and returns a `ResScanResult` for the
user to review. Nothing is saved; `/submit` writes. The file takes one of two
paths, chosen by its content type alone: a PDF goes to the LLM as a document,
and an image goes through OCR and then to the LLM as text. The two fail
differently, so "the scan works" is only ever true of one of them.

## The routing

```text
POST /api/receipts/scan
        │
        ▼
  validateFile()         not empty; content type present and one of
        │                JPEG, JPG, PNG, GIF, WebP, PDF; ≤ MAX_UPLOAD_MB (25)
        ▼
  isImage(file) && !vision-for-images ?
        │
   ┌────┴──────────────────────────────┐
   │ no (PDF, or flag on)              │ yes (every image)
   ▼                                   ▼
┌──────────────────────┐    ┌──────────────────────────────────┐
│ LlmReceiptParser     │    │ OcrService.extractText           │
│   .parseReceipt      │    │   EXIF orientation               │
│ file as media,       │    │   cropToReceipt                  │
│ document prompt      │    │   upscale, grayscale, sharpen,   │
│                      │    │   global Otsu → 1-bit            │
│                      │    │   Tesseract                      │
│                      │    │ LlmReceiptParser                 │
│                      │    │   .parseReceiptText, text prompt │
└──────────────────────┘    └──────────────────────────────────┘
        │                                   │
        └─────────────────┬─────────────────┘
                          ▼
            rejectIfNothingParsed    no items → 422
                          ▼
            MatcherService.matchStore(storeName)
            MatcherService.matchProductsToPurchases(items)
                          ▼
                    ResScanResult
```

`ReceiptScanService` owns the routing. `isImage` checks the lower-cased
content type against `IMAGE_CONTENT_TYPES` (`image/jpeg`, `image/jpg`,
`image/png`, `image/gif`, `image/webp`), so after `validateFile` the only thing
that is "not an image" is `application/pdf`. Nothing looks at the pixels or the
file extension to choose a path.

Every image takes the same path, whether it is a camera photo or a screenshot.
[ADR-0004](adr/0004-ocr-plus-llm-parsing.md) records why there is no
screenshot branch.

---

## PDFs: straight to the LLM

```text
file → LlmReceiptParser.parseReceipt(file) → ParsedReceipt
```

No OCR and no preprocessing. The file is attached to the prompt as media with
its content type (`application/pdf`), under the document prompt described
below. Rationale in [ADR-0004](adr/0004-ocr-plus-llm-parsing.md): a PDF is
usually clean extractable text, and OCR would add noise for nothing.

In the 2026-09-23 harness baseline, the four text PDFs in the fixture set
returned 200 with every item's price correct, and the two scanned-image PDFs
returned 422.

---

## Images: OCR, then the LLM

```text
file → EXIF orientation → cropToReceipt → preprocessImage → Tesseract
     → LlmReceiptParser.parseReceiptText(ocrText) → ParsedReceipt

preprocessImage:  upscale → grayscale → sharpen → otsuBinarize
```

All of this is `OcrService.extractText`.

### EXIF orientation

The EXIF `Orientation` tag is read with metadata-extractor and all eight values
are applied (rotations and mirrors), so a portrait phone photo reaches
Tesseract upright. A missing tag, or any error reading it, means orientation 1:
use the image as decoded.

### Crop to the receipt

`cropToReceipt` removes the table the receipt is lying on, so that
thresholding sees paper and ink rather than paper and furniture:

1. Sample the image's luminance on a grid (step `min(w, h) / 400`).
2. The **paper level** is the 92nd percentile of that histogram. A sample is
   paper if it is brighter than `max(140, paperLevel − 45)`.
3. Keep every row and every column in which more than 6% of the samples are
   paper. The crop is their bounding box plus a margin of `max(8, min(w, h) / 100)`
   pixels.
4. If no such rows or columns exist, or the crop would cover more than 95% or
   less than 4% of the frame, use the whole frame instead.

No edge detection, no perspective correction or deskew. On a screenshot the
receipt already fills the frame, so the crop is a no-op.

### Preprocessing

`preprocessImage` runs on the cropped image:

| Step | What it does |
|---|---|
| upscale | if the image is shorter than 2000 px, scale it up (bicubic) to 2000 px high |
| grayscale | convert to 8-bit gray |
| sharpen | 3×3 kernel, centre 5, edges −1 |
| `otsuBinarize` | one global Otsu threshold over the whole image; output is **1-bit** |

The threshold is computed once per image from the full histogram of the
sharpened grayscale and logged at DEBUG as `Otsu threshold: <n>`. Tesseract
receives the 1-bit image.

### Tesseract

Configured once in `OcrConfig`:

| Setting | Value |
|---|---|
| engine mode | OEM 1, LSTM only |
| page segmentation | PSM 6, a single uniform block of text |
| `user_defined_dpi` | 300 |
| language | `app.ocr.language`, from `OCR_LANGUAGE`, default `eng+bul` |
| tessdata | `app.ocr.data-path`, from `TESSDATA_PATH`, default `/usr/share/tessdata` |

The OCR text is logged at TRACE only, since it is the full text of a real
receipt.

### Debug images

When `app.ocr.debug-output-path` (`OCR_DEBUG_OUTPUT_PATH`) is set, the image
handed to Tesseract (cropped, preprocessed, 1-bit) is saved there as
`<name>_<yyyyMMdd_HHmmss>_preprocessed.png` for every image scan. The name is
reduced to a safe last path segment first. Unset, nothing is written.

---

## The vision flag

`receipts.scanning.vision-for-images`, default `false`. When `true`, images skip
OCR and go through `parseReceipt` like a PDF, as media under the document
prompt. It exists to re-measure the OCR-versus-vision choice for a future model
with a config change; vision was measured worse on real photos (see
[below](#measured-vision-vs-ocr-for-photographed-receipts-2026-09-23) and
[ADR-0004](adr/0004-ocr-plus-llm-parsing.md)), so it stays off.

---

## The two prompts

`LlmReceiptParser` holds two prompts, and both ask for the same structured
output: `ParsedReceipt`, whose schema Spring AI generates from the record
(every field marked required).

| | `RECEIPT_VISION_PROMPT` | `RECEIPT_TEXT_PARSING_PROMPT` |
|---|---|---|
| used by | `parseReceipt(file)`: PDFs, and images with the flag on | `parseReceiptText(ocrText)`: images |
| input | the file, attached as media | the OCR text, substituted into `{ocrText}` |
| framing | "analyze this receipt image" | "OCR-extracted text … may contain minor OCR errors, use context to correct" |

The body is otherwise the same in both: return the shop rather than the owning
company (with Bulgarian examples), the receipt date as `dd/MM/yyyy`, the item
fields (`productName`, `price` as the listed price **before** discount,
`quantity`, `quantityUnit`, `discountAmount`), the discount patterns to look
for, and what not to count as an item. The document prompt has one discount
pattern the text prompt lacks: a crossed-out original price.

A change to parsing behaviour usually means editing both. Any exception from
the model call becomes a `ReceiptParsingException`.

---

## Building the result

1. **Nothing parsed fails the scan.** If `ParsedReceipt` is null or has no
   items, `rejectIfNothingParsed` throws `ReceiptParsingException`, which
   `ReceiptScanControllerAdvice` maps to **422** with `RECEIPT_PARSING_ERROR`.
   The same mapping covers unreadable images, OCR errors and model errors.
2. **Store.** `matchStore` normalizes the parsed store name (Unicode NFD with
   combining marks removed, lower case, anything not a letter or digit to a
   space, whitespace collapsed) and looks for a store whose
   `normalizedCanonicalName` is exactly equal. Store aliases and fuzzy matching
   are not consulted. The result carries the match, or none, plus the raw name.
3. **Products.** For each item, `matchProductsToPurchases` scores **every**
   product in the catalog against the normalized item name: 1.0 for an exact
   canonical-name match, 0.99 for an exact alias match, otherwise the best
   Levenshtein similarity (`1 − distance / longer length`) over the canonical
   name and aliases. It keeps up to five suggestions scoring at least 0.45,
   best first, ties by name.
4. **Response.** `ResScanResult(storeSuggestion, rawStoreName, purchaseDate,
   purchases)`, where each purchase carries the parsed name, its product
   suggestions, and the parsed price, quantity, unit and discount unchanged.

NFD stripping also removes the breve from `й`, so it compares equal to `и`.

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
the real receipt, on the pipeline before `cropToReceipt` existed:

- the chosen threshold was **139**, which separated *receipt from wood*, not
  *ink from paper*
- wood grain straddles that cut-off, so the grain became high-contrast striping
  across most of the frame
- Tesseract ran layout analysis over that and read the texture: **10,911
  characters** from a receipt of roughly twenty lines, with **none** of the real
  text surviving

`cropToReceipt` now removes most of the table before Otsu runs. It still
applies whenever the crop falls back to the whole frame, and a receipt that is
not the brightest thing in the photo will not be found.

Being *global* is the problem cropping does not solve. One threshold cannot
serve a receipt with a shadow falling across it, or the curl most receipts
have — the lit half and the shadowed half need different cut-offs.

**The alternative is local (adaptive) thresholding** — Sauvola or Niblack are
the standard choices for document images. These compute a threshold per small
neighbourhood from its own local mean and variance, so a shadow simply shifts
the local threshold with it.

**But the stronger move may be to binarize less, not better.** Tesseract 4 and
5 binarize internally, per region. Handing it a pre-binarized 1-bit image
throws away information it would otherwise have used. Passing clean
**grayscale** and letting Tesseract decide is often better than any hand-rolled
binarization, and it is worth measuring before investing in Sauvola. Both are
open items in ROADMAP M0a.

---

## Upload requirements

Every constraint a file must satisfy to reach the parser. They live in three
different places and **the smallest one wins**, so raising any single limit in
isolation achieves nothing.

### The full chain

```text
browser
   │  1. nginx  client_max_body_size      (UI container, deployed only)
   ▼
nginx
   │  2. Spring spring.servlet.multipart  (application.yaml)
   ▼
Spring
   │  3. ReceiptScanService.validateFile  (type, emptiness, size)
   ▼
parser
```

| # | Layer | Where | Derived from | On breach |
|---|---|---|---|---|
| 1 | `client_max_body_size` | `ui/nginx/nginx.conf.template` | `${MAX_UPLOAD_MB}m` | `413` from nginx; never reaches the API |
| 2 | `max-file-size` / `max-request-size` | `api/src/main/resources/application.yaml` | `${MAX_UPLOAD_MB:25}MB` | `413` from Spring |
| 3 | the service's own check | `ReceiptScanService` | bound from layer 2 | `422` with `RECEIPT_PARSING_ERROR` |

### One value, three layers

All three derive from a single **`MAX_UPLOAD_MB`** environment variable, so
they cannot drift:

- **nginx** substitutes it at container start, alongside `BACKEND_HOST`
- **Spring** reads it in `application.yaml`, defaulting to 25
- **`ReceiptScanService`** binds `spring.servlet.multipart.max-file-size` as a
  `DataSize` rather than holding a constant

Raising the ceiling means changing one number in `.env`. Layer 3 is the only
one that returns a useful message, so it should remain the one that trips —
which is why the layers are equal rather than tiered.

One trap worth knowing: the UI Dockerfile's `envsubst` takes an **explicit
whitelist**. A `${VAR}` used in the template but missing from that list
survives into the generated config verbatim, and nginx then refuses to start.
Every template variable must appear in the `CMD`.

### Tests

| Layer | Test | Runs |
|---|---|---|
| Service | `ReceiptScanServiceUploadLimitTest` (6 tests) | `./gradlew test` |
| nginx + agreement | `scripts/test-upload-limits.sh` | one nginx container, ~5 s |

The script needs no API, no database and no LLM call. nginx answers `413` when
it rejects a body on size and anything else when it accepts one, so a dead
upstream is a perfectly good upstream — a `502` proves the body got through.

It checks four things: that `client_max_body_size` is present at all, that
every template variable is in the envsubst whitelist, that one megabyte under
the limit passes, and that one over is rejected. Each check was confirmed to
fail by reintroducing its fault.

It is the only guard against nginx's 1 MB default: sharing `MAX_UPLOAD_MB`
cannot catch a missing directive, because a default is never written down
anywhere to be shared.

### Accepted content types

Checked against `MultipartFile.getContentType()`, not the file extension:

```text
image/jpeg   image/jpg   image/png   image/gif   image/webp   application/pdf
```

Anything else is rejected with `Unsupported file type: <type>`. A missing
content type is rejected outright. An empty file is rejected before type is
considered.

Note that `image/heic` is **not** accepted, although it is the default camera
format on iOS. Phones normally convert on share, but a direct HEIC upload
fails.

### Rate limit

`POST /api/receipts/scan` is capped at **10 per hour per user**, deliberately
stricter than the general 100/minute because each scan costs an LLM call.
Breach returns `429` with `RATE_LIMIT_EXCEEDED`.

### Timeouts

A scan runs OCR and an LLM call. Measured **34–78 s** on real photos in the
2026-09-18 baseline below.

| Layer | Setting | Value |
|---|---|---|
| nginx → API | `proxy_read_timeout`, `proxy_send_timeout` | **180s** |
| nginx → API | `proxy_connect_timeout` | 30s |

### Why both nginx values are set explicitly

Removing either directive silently restores an nginx default that breaks
scanning, and neither shows up in development:

- **`client_max_body_size` defaults to 1 MB.** Checked against the running
  container: a 2 MB upload returns `413`, a 0.5 MB upload passes. Every photo
  fixture is 2.3–4.0 MB.
- **`proxy_read_timeout` defaults to 60 s**, against scans that measure
  34–78 s. Slower scans would return `504`.

**The dev proxy and the production proxy have different defaults**, and testing
through Vite exercises neither of nginx's. Upload limits and timeouts have to be
verified against the container.

### Why 25 MB

| Input | Typical size |
|---|---|
| Phone photo, JPEG | 2–4 MB |
| **Same photo exported as PNG** | **10–12 MB** |
| Scrolling app screenshot | 2–3 MB |
| Photo wrapped in a PDF | up to 14 MB |

A 10 MB limit rejects ordinary receipts, not abusive ones: a lossless PNG
export of a 12 MP photo exceeds it, and some share sheets produce PNG by
default. The full-resolution export behind the `probe_photo-as.png` fixture
came out at 11.8 MB.

Abuse is bounded by the rate limit (10 scans/hour/user), not by file size.

## Measured baseline, 2026-09-18

Four photographs of **one** Kaufland receipt — 7 items, per-line discounts,
mixed piece and weight quantities, dual BGN/EUR totals — differing only in
surface and angle. All four took the preprocessed OCR route (none was a PNG), on
the pipeline before `cropToReceipt` existed, when images were still split by a
PNG test. This is what M0a must improve on.

| | Store extracted | Date | Items (of 7) | Time |
|---|---|---|---|---|
| **white** | `Кауфланд България ЕООД и Ко. КД` | none | **7/7, all correct** | 52 s |
| **shadow** | `Хипермаркет Кауфланд Варна-Трошево` | `2021-05-21` (invented) | 4/7, **rows cross-contaminated** | 74 s |
| **angled** | `Авто Султан ТА` (**invented**) | none | 8 rows, mostly wrong | 78 s |
| **wood** | `Кауфланд` | `1970-01-01` (null) | **0/7** | 34 s |

### The parser is good; the image pipeline is not

On the clean shot, extraction is close to flawless: every price, every
quantity — including `0.318` and `0.636` **KILOGRAM** for weighed goods — and
every per-line discount. Only two trivial OCR slips (`кр. сир.` read as
`kp. cup`). The LLM parsing stage is not the problem.

Everything below that row is the image pipeline degrading its input.

### Failure severity is the opposite of what it looks like

- **wood — fails loudly.** Zero items. Useless, but obvious; nobody submits it.
- **shadow — fails quietly.** Four rows, but data is mismatched *across* rows:
  `Йоанна закв. сметана` is given `19.74` and quantity `6.0`, which belong to
  `Р.тон в раст.масло`. A plausible row carrying another row's numbers.
- **angled — fails dangerously.** Eight rows of confident fiction. `Ширацаца`
  is not a word. `Р.тон в раст.масло` (tuna in oil) became `Растително масло`
  (vegetable oil) — a different product at the same price. The store is
  invented outright.

A wrong price entering price history is worse than no price, because the
lookup loop will later present it as fact. **Angled and shadow are more
dangerous than wood**, and any confidence threshold must treat quiet corruption
as the primary risk, not empty results.

### Four names for one store

The same receipt produced four different store strings:

```text
Кауфланд
Кауфланд България ЕООД и Ко. КД
Хипермаркет Кауфланд Варна-Трошево
Авто Султан ТА          ← invented
```

**`matched` was `None` for all four**, including the bare `Кауфланд`, which
should match the catalog's `Kaufland` on transliteration alone (D13).

This is the empirical case for the alias design in D18: no parsing rule
normalises those four into one store, but a human picking once — and the raw
string being stored as an alias — handles every one of them, and the next
receipt from that shop matches directly.

### Other confirmations

- **`price` is the line total *before* discount.** The seven extracted prices
  sum to 84.32; the seven discounts sum to 2.00; the receipt total is 82.32.
  Exactly consistent, so `discountAmount` is deducted separately, not baked in.
- **Product matching is weak (D12).** `Хляб с лимец 500г` matched nothing,
  though the catalog holds `Вита хляб лимец`.
- **Date extraction is broken on all four**, and missing dates serialise
  inconsistently as both `null` and `1970-01-01`.
- **Every run exceeded the 30 s target**, at 34–78 s.
- All prices came from the BGN column, which is correct — but by luck, since
  nothing records a currency. *(2026-09-23: purchases now store a currency,
  but the parser still does not emit one, so a scanned submission is recorded
  as EUR whichever column was read. Tracked in ROADMAP M2a.)*

## Testing implications

The two paths fail differently, which is diagnostically useful:

| Symptom | Likely cause |
|---|---|
| PDFs work, images fail | OCR: the Tesseract runtime, tessdata, or `OCR_LANGUAGE` |
| Images work, PDFs fail | the LLM client, API key, or the document prompt |
| Text PDFs work, scanned PDFs return 422 | the document path has no OCR; seen in the 2026-09-23 baseline |
| A photo returns 422, "No purchases could be read" | nothing usable came out of OCR or the model; set `OCR_DEBUG_OUTPUT_PATH` and look at what Tesseract was given |
| Plausible rows with wrong or swapped numbers | image quality (shadow, angle); see the 2026-09-18 baseline — this is the dangerous case |

A fixture set needs PDFs (text and scanned) as well as images, and images in
more than one format, including a photo exported as PNG and a screenshot saved
as JPEG: the pipeline must not depend on the format. The fixtures live outside
the repository, next to the checkout; see `receipt-fixtures/README.md` there.

## Measured: vision vs OCR for photographed receipts (2026-09-23)

Photos go OCR text to LLM; PDFs go straight to the vision model. That asymmetry
means Tesseract's output is the ceiling for every photo, so it was worth asking
whether photos should use vision too. They should not.

Run on all 64 fixtures, `receipts.scanning.vision-for-images=true`, everything
else held constant (gpt-6-luna, medium reasoning, same prompt). Restricted to
the 42 fixtures scored in both runs, so the denominator is identical:

| | OCR text | vision |
|---|---|---|
| line items matched | 263 | 179 |
| items with the right price | **253** | **165** |
| ground-truth prices present anywhere in the output | 75% | 69% |
| passed L0 (self-consistency) | 24 | **30** |
| HTTP 200 | 53 | 56 |

Vision reads the price column about as well (75% vs 69%) but **invents the
product names**. From `kaufland_17_flat.jpeg`, same prices, same positions:

| ground truth | OCR text | vision |
|---|---|---|
| `Жарено филе, кг` | `Жарено филе` | `Картофи фини, кг` |
| `DrKeskin св трици 200` | `Drkeskin ов трици200` | `Бисквити` |
| `DorBlu Синьо сирене` | `DorBlu Синьо сирене` | `Добруджа бяло саламурено сирене` |

The model recognises the receipt's layout and fills the name column with
plausible Bulgarian grocery items instead of reading the small dense text.
OCR's names are mangled but recoverable; vision's are clean, confident and
wrong, which is worse for a price-history product whose entire value is
knowing *which* product a price belongs to.

### L0 is not a safe metric on the vision path

L0 went **up** (24 to 30) while the data got materially worse. L0 checks the
receipt's own arithmetic, and hallucinated names attached to correctly-read
prices still reconcile to the total. Any future comparison involving vision
must be scored against ground-truth names, never L0 alone.

The flag stays in `ReceiptScanService` and stays `false`. It costs nothing and
makes re-running this against a future model a config change. Re-test before
assuming the result still holds; do not re-derive it from scratch.
