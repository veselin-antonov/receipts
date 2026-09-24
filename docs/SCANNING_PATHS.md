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
the limit passes, and that one over is rejected. Verified against both
regressions by reintroducing them deliberately.

This is the test that would have caught the 1 MB default, which no amount of
config-sharing could have flagged — the value was never written down anywhere
to be shared.

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

A scan runs OCR and an LLM call. Measured **34–78 s** on real photos.

| Layer | Setting | Value |
|---|---|---|
| nginx → API | `proxy_read_timeout`, `proxy_send_timeout` | **180s** |
| nginx → API | `proxy_connect_timeout` | 30s |

### Two defaults that were silently wrong

Both were found by testing the deployed container rather than the dev proxy,
and neither was visible in development.

**`client_max_body_size` was unset, so nginx applied its default of 1 MB.**
Verified against the running container: a 2 MB upload returned `413`, a 0.5 MB
upload passed through. Every one of the 48 photo fixtures is 2.3–4.0 MB, so
none of them could have reached the API. This was latent rather than live —
the deployed UI image predates the scan panel, so there was no upload path to
exercise it — but it would have fired on the first deploy of the merged UI, and
presented as a bug in newly shipped UI code rather than in nginx configuration.

**`proxy_read_timeout` was unset, so nginx applied its default of 60 s** against
scans that measure 34–78 s. Slower scans would have returned `504`.

The general lesson, which applies beyond these two: **the dev proxy and the
production proxy have different defaults**, and testing through Vite exercises
neither of nginx's. Upload limits and timeouts have to be verified against the
container.

### Why 25 MB

| Input | Typical size |
|---|---|
| Phone photo, JPEG | 2–4 MB |
| **Same photo exported as PNG** | **10–12 MB** |
| Scrolling app screenshot | 2–3 MB |
| Photo wrapped in a PDF | up to 14 MB |

The old 10 MB limit rejected ordinary receipts, not abusive ones: a lossless
PNG export of a 12 MP photo exceeds it, and some share sheets produce PNG by
default. This was hit in practice while building fixtures — the full-resolution
`probe_photo-as.png` came out at 11.8 MB and had to be downscaled to get under
the limit.

Abuse is bounded by the rate limit (10 scans/hour/user), not by file size.

## Measured baseline, 2026-09-18

Four photographs of **one** Kaufland receipt — 7 items, per-line discounts,
mixed piece and weight quantities, dual BGN/EUR totals — differing only in
surface and angle. All Path C. This is what M0a must improve on.

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
