# Spring AI & OCR Setup Guide

## Overview
The Receipts API uses a dual-path pipeline for receipt scanning:
- **Images** (JPEG, PNG, WebP, GIF): Tesseract OCR extracts text → LLM structures the data
- **PDFs**: Sent directly to the LLM vision model

This hybrid approach compensates for LLM vision models' poor digit recognition on images, while leveraging their strength with PDF documents.

## Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Spring AI OpenAI Starter | 1.1.2 (via BOM) | LLM integration (vision + text parsing) |
| Tess4J | 5.14.0 | Tesseract OCR engine (JNI wrapper) |
| metadata-extractor | 2.19.0 | EXIF orientation reading for camera photos |

## Architecture

### Processing Pipeline

```
Upload Receipt
    │
    ├─ IMAGE (JPEG/PNG/WebP/GIF)
    │   ├─ Read EXIF orientation → rotate if needed
    │   ├─ Preprocess (photos only, not screenshots):
    │   │   └─ grayscale → [upscale → sharpen → Otsu binarize]*
    │   ├─ Tesseract OCR → extracted text
    │   └─ LLM text prompt → ParsedReceipt
    │
    └─ PDF
        └─ LLM vision prompt (direct) → ParsedReceipt
```

*Preprocessing pipeline steps may be adjusted based on image quality needs.

### Components

| Component | Purpose |
|-----------|---------|
| `ReceiptScanController` | REST endpoints (`/scan`, `/submit`) |
| `ReceiptScanService` | Orchestration — routes images vs PDFs, validates files |
| `OcrService` | EXIF correction, image preprocessing, Tesseract OCR extraction |
| `LlmReceiptParser` | Dual-mode LLM parsing (vision prompt for PDFs, text prompt for OCR output) |
| `OcrConfig` | Tesseract bean setup (LSTM engine, PSM 6, 300 DPI) |
| `OcrProperties` | Config record for `app.ocr.*` properties |
| `ParsedReceipt` | Structured output schema (Jackson-annotated for LLM entity extraction) |

### Key Design Decisions

1. **OCR for images, vision for PDFs** — LLM vision models struggle with digit recognition on photos but handle PDFs well. OCR excels at text extraction from images.
2. **Screenshot bypass** — PNGs (screenshots/digital exports) skip preprocessing since they already have pixel-perfect text. Only camera photos (JPEG/WebP) get the preprocessing pipeline.
3. **EXIF orientation handling** — Phone cameras store images in sensor orientation with an EXIF tag. `ImageIO.read()` ignores this, so we read and apply the rotation before OCR.
4. **Otsu's binarization** — Adaptive threshold selection preserves subtle digit curves (0 vs 6 vs 8) better than a fixed threshold.
5. **Separate prompts** — The vision prompt includes visual pattern detection (crossed-out prices), while the text prompt focuses on textual discount patterns since OCR can't capture visual formatting.

## Configuration

### Environment Variables

| Variable | Default (prod) | Default (dev) | Description |
|----------|----------------|---------------|-------------|
| `OPENAI_API_KEY` | *(required)* | *(required)* | OpenAI API key |
| `TESSDATA_PATH` | `/usr/share/tessdata` | `C:/Program Files/Tesseract-OCR/tessdata` | Path to Tesseract trained data |
| `OCR_LANGUAGE` | `eng+bul` | `eng+bul` | Tesseract language codes |
| `OCR_DEBUG_OUTPUT_PATH` | *(empty = disabled)* | `./ocr-debug` | Directory for saving preprocessed images |

### Application Properties

```yaml
# application.yaml (production)
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:}
      chat:
        options:
          model: gpt-5-mini
          temperature: 1

app:
  ocr:
    data-path: ${TESSDATA_PATH:/usr/share/tessdata}
    language: ${OCR_LANGUAGE:eng+bul}
    debug-output-path: ${OCR_DEBUG_OUTPUT_PATH:}
```

```yaml
# application-dev.yaml (development)
app:
  ocr:
    data-path: ${TESSDATA_PATH:C:/Program Files/Tesseract-OCR/tessdata}
    language: ${OCR_LANGUAGE:eng+bul}
    debug-output-path: ${OCR_DEBUG_OUTPUT_PATH:./ocr-debug}
```

## Local Development Setup (Windows)

### 1. Install Tesseract OCR
Download from [UB Mannheim Tesseract builds](https://github.com/UB-Mannheim/tesseract/wiki).

During installation, select these language packs:
- **English** (included by default)
- **Bulgarian** (under Additional language data)

> **Note**: The "Additional script data → Cyrillic" option is NOT needed — the Bulgarian language pack already includes Cyrillic script recognition with Bulgarian-specific word dictionaries.

### 2. Configure Environment
Add to your `.env` file:
```
OPENAI_API_KEY=sk-...your-api-key...
TESSDATA_PATH=C:/Program Files/Tesseract-OCR/tessdata
OCR_LANGUAGE=eng+bul
```

### 3. Debug Preprocessed Images
In dev profile, preprocessed images are automatically saved to `./ocr-debug/` with timestamped filenames. This directory is gitignored. To disable, set `OCR_DEBUG_OUTPUT_PATH=` (empty).

## Docker Deployment

The Dockerfile installs Tesseract OCR and language data:
```dockerfile
RUN apk add --no-cache tesseract-ocr tesseract-ocr-data-eng tesseract-ocr-data-bul
```

Tess4J uses JNI (runs in-process), so no separate OCR service container is needed.

## API Endpoints

### POST /api/receipts/scan
Uploads and parses a receipt file.
- **Auth**: JWT (ACTIVE_USER scope)
- **Body**: Multipart form data with `file` field
- **Supported formats**: JPEG, PNG, GIF, WebP, PDF (max 10MB)
- **Response**: `ResScanResult` with parsed purchases for user review
- **Rate Limit**: 10 requests/hour per user

### POST /api/receipts/submit
Persists user-reviewed purchases to database.
- **Auth**: JWT (ACTIVE_USER scope)
- **Body**: `ReqSubmitPurchases` JSON with list of purchases
- **Response**: `List<ResPurchase>` — saved records

## Troubleshooting

### OCR output is garbled / rotated
The image likely has EXIF orientation data that wasn't being applied. This is handled automatically by `OcrService.applyExifOrientation()`. Check debug images in `./ocr-debug/` to verify correct rotation.

### Digits 0, 6, 8 confused
- Ensure Otsu binarization is active (not fixed threshold)
- Check that images are being upscaled if small (min height 2000px target)
- Inspect debug images to verify preprocessing quality

### "Cannot resolve symbol 'ChatClient'"
IDE caching issue. Run `./gradlew clean build` and invalidate IDE caches.

### Tesseract errors on startup
- Verify `TESSDATA_PATH` points to a valid tessdata directory
- Ensure `eng.traineddata` and `bul.traineddata` files exist in that directory
- On Windows, verify Tesseract was installed with the Bulgarian language pack

## Cost Considerations
Receipt scanning incurs OpenAI API charges. Rate limiting (10 scans/hour per user) helps control costs. The OCR preprocessing for images reduces LLM token usage since text prompts are cheaper than vision prompts.