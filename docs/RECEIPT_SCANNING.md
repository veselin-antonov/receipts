# Receipt Scanning

This document is the canonical backend feature doc for receipt scanning.

It covers:
- what the feature does
- which flows it owns
- the image vs PDF processing paths
- the key classes and responsibilities
- configuration for AI and OCR
- runtime requirements for development and production
- request/response contracts to keep in mind while changing the feature
- troubleshooting and operational risks

Use this doc when you are changing receipt upload, OCR, AI parsing, matching, or reviewed purchase submission.

## Feature purpose

The receipt scanning feature lets an authenticated user:
1. upload a receipt image or PDF
2. receive parsed store/date/line-item suggestions
3. review and edit the parsed result in the UI
4. submit the reviewed purchases for persistence under that user account

This feature exists because raw receipt images are noisy and inconsistent. The backend combines OCR and LLM-based parsing so the UI gets a structured review payload instead of raw text.

## Endpoints owned by this feature

```text
POST /api/receipts/scan
POST /api/receipts/submit
```

Related contracts live in:
- `API_CONTRACTS.md`

## Main user flow

```text
user uploads receipt
→ backend validates file type and size
→ backend routes by file type
→ receipt is parsed into structured data
→ store and product suggestions are matched
→ UI receives review payload
→ user edits/accepts rows
→ UI submits reviewed purchases
→ backend saves purchases for the authenticated user
```

## Processing paths

### Image receipts

Supported image formats:
- JPEG / JPG
- PNG
- GIF
- WebP

Image flow:

```text
multipart upload
→ ReceiptScanService.validateFile(...)
→ OcrService.extractText(...)
→ LlmReceiptParser.parseReceiptText(...)
→ MatcherService.matchStore(...)
→ MatcherService.matchProductsToPurchases(...)
→ ResScanResult
```

Why this path exists:
- receipt photos often confuse vision models on digits and totals
- OCR gives cleaner text for prices, quantities, and item rows
- the LLM is still useful for structuring OCR output into receipt semantics

### PDF receipts

PDF flow:

```text
multipart upload
→ ReceiptScanService.validateFile(...)
→ LlmReceiptParser.parseReceipt(file)
→ MatcherService.matchStore(...)
→ MatcherService.matchProductsToPurchases(...)
→ ResScanResult
```

Why PDFs bypass OCR:
- PDFs are usually cleaner inputs for LLM vision/document parsing
- OCR would add noise without helping much

## Key backend classes

### Controller layer

- `ReceiptScanController`
  - exposes `/api/receipts/scan`
  - exposes `/api/receipts/submit`
  - requires authenticated JWT user

- `ReceiptScanControllerAdvice`
  - turns `ReceiptParsingException` into `422 Unprocessable Entity`
  - returns `ProblemDetail` with `error=RECEIPT_PARSING_ERROR`

### Service layer

- `ReceiptScanService`
  - orchestrates the feature
  - validates the uploaded file
  - routes image vs PDF processing
  - submits reviewed purchases for persistence

- `OcrService`
  - used only for image receipts
  - reads EXIF orientation
  - rotates/flips images when needed
  - treats PNG screenshots differently from camera photos
  - preprocesses photos before OCR
  - runs Tesseract OCR
  - optionally writes debug images

- `LlmReceiptParser`
  - parses receipt content into structured receipt data
  - has a PDF/vision path
  - has an OCR-text path
  - uses specialized prompts for each input mode

- `MatcherService`
  - matches raw parsed names to known stores and products
  - produces suggestions for the UI review step

- `PurchaseService`
  - persists reviewed purchases after submit
  - must always remain user-scoped

### Config layer

- `OcrConfig`
  - creates/configures the Tesseract bean
- `OcrProperties`
  - binds `app.ocr.*` configuration
- `AiConfig`
  - configures Spring AI client usage for receipt parsing

## Request and response shapes that matter

### Scan request

- multipart form-data
- field name: `file`
- max size: 25MB by default, configurable with `MAX_UPLOAD_MB`
- supported types: JPEG, JPG, PNG, GIF, WebP, PDF

### Scan response

`ResScanResult` contains:
- `storeSuggestion`
- `rawStoreName`
- `purchaseDate`
- `purchases`

Each `ResScanPurchase` contains:
- `rawProductName`
- `productSuggestions`
- `price`
- `quantity`
- `quantityUnit`
- `discountAmount`

### Submit request

`ReqSubmitPurchases` contains:
- non-empty `purchases` list

Each submitted purchase uses the same request DTO shape as direct purchase creation:
- `productId`
- `productName`
- `storeId`
- `storeName`
- `price`
- `date`
- `quantity`
- `quantityUnit`
- `discountAmount`

For concrete examples, use:
- `API_CONTRACTS.md`

## Security and ownership rules

Important invariants:
- only authenticated active users may scan or submit receipts
- reviewed purchases must be saved for the authenticated user only
- receipt parsing should never bypass user ownership when it reaches persistence
- scan traffic is rate-limited more aggressively than normal API traffic because of AI cost

Related code:
- `SecurityConfiguration`
- `AuthenticatedUserService`
- `ReceiptScanController`
- `PurchasesController`

## AI and OCR configuration

The feature depends on two config surfaces:

### AI settings

From `application.yaml`:

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:}
      chat:
        options:
          model: ${OPENAI_MODEL:gpt-6-luna}
          temperature: 1
          reasoning-effort: ${OPENAI_REASONING_EFFORT:medium}
```

`gpt-6-luna` at medium reasoning effort was chosen by measurement over the
64-fixture set; see ADR-0006 in the receipts docs. `temperature` must stay 1:
these are reasoning models and reject any other value.

What matters:
- `OPENAI_API_KEY` must be present
- model or prompt changes can affect parsing accuracy and cost
- image vs text parsing are both handled inside this feature

### OCR settings

From `application.yaml` and `application-dev.yaml`:

```yaml
app:
  ocr:
    data-path: ${TESSDATA_PATH:/usr/share/tessdata}
    language: ${OCR_LANGUAGE:eng+bul}
    debug-output-path: ${OCR_DEBUG_OUTPUT_PATH:}
```

What matters:
- `TESSDATA_PATH` must point to trained data
- `OCR_LANGUAGE` defaults to `eng+bul`
- `OCR_DEBUG_OUTPUT_PATH` should usually be empty in production

## Runtime requirements

### Development

Needed locally:
- OpenAI API key
- Tesseract installed with English and Bulgarian trained data
- valid `TESSDATA_PATH`
- JWT dev keys and normal backend dependencies

Useful companion docs:
- `DEVELOPMENT_SETUP.md`
- `PRODUCTION_SETUP.md`

### Production

Needed in production:
- OpenAI API key
- Tesseract runtime available in the deployed environment
- valid tessdata path or container default
- correct upload limits and rate limits
- working secure-cookie deployment path

The repo Dockerfile already installs:
- `tesseract-ocr`
- `tesseract-ocr-data-eng`
- `tesseract-ocr-data-bul`

## Matching and review behavior

The scanner does not directly persist parsed OCR/LLM output from `/scan`.

Instead it returns a review-oriented payload:
- raw store name + matched store suggestion
- raw product names + product suggestions
- parsed amounts and quantities

This is important because:
- OCR/LLM output may be imperfect
- the UI is expected to let the user correct or confirm rows
- `/submit` is the point where persistence happens

## Failure modes and troubleshooting

### Receipt is rejected before parsing

Likely causes:
- missing file
- unsupported content type
- file over the upload limit (`MAX_UPLOAD_MB`, 25MB by default)

Source:
- `ReceiptScanService.validateFile(...)`

### Rotated or sideways image OCR

Likely cause:
- EXIF orientation mismatch

Check:
- `OcrService.applyExifOrientation(...)`
- OCR debug images if enabled

### OCR text is poor on photos

Check:
- is the input actually a low-quality photo?
- is `TESSDATA_PATH` correct?
- are the required language packs installed?
- are debug images readable after preprocessing?

### Screenshots versus photos

There is no screenshot branch any more (D17). Every image takes the same path:
crop to the receipt, then preprocess. On a screenshot the crop is a no-op and
thresholding clean rendered text is close to identity, so a separate path only
added a way to misclassify inputs.

### PDFs work but images fail

That usually points to OCR/runtime issues rather than LLM availability.

Check:
- Tesseract installation
- tessdata path
- language data
- OCR debug output

### Images work but PDFs fail

That usually points to AI or prompt/runtime issues.

Check:
- `OPENAI_API_KEY`
- Spring AI client setup
- prompt/model changes

## Cost and performance considerations

Important operational realities:
- scan traffic is more expensive than normal CRUD traffic
- image preprocessing cost is small compared with AI latency/cost
- scan endpoint rate limiting protects both cost and abuse surface
- debug image output is useful in dev but should be tightly controlled in production

## When to update this doc

Update this doc whenever you change:
- `/api/receipts/scan` or `/api/receipts/submit`
- image vs PDF routing logic
- OCR preprocessing behavior
- LLM parsing behavior or prompts
- accepted file types or upload limits
- matching behavior
- user-review vs persistence workflow
- OCR/AI runtime requirements
