# Spring AI Setup Guide

## Overview
The Receipts API now includes LLM-powered receipt scanning using Spring AI with OpenAI's GPT-4o-mini model.

## Dependencies
- **Spring AI OpenAI Starter** (1.0.6) - Main Spring AI integration
- **Spring AI Core** (1.0.6) - Core functionality
- **Jackson Databind** (2.17.1) - JSON serialization for structured output

## Configuration

### Environment Variables
Add to your `.env` file:
```
OPENAI_API_KEY=sk-...your-api-key...
```

### Application Properties
The `application.yaml` contains:
```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:}
      chat:
        options:
          model: gpt-4o-mini
          temperature: 0.1
```

## Architecture

### Components
1. **LlmReceiptParser** - Uses Spring AI ChatClient to parse receipt images via LLM
2. **ParsedReceipt** - Data class with Jackson annotations for structured LLM output
3. **ReceiptScanService** - Orchestrates parsing and persistence
4. **ReceiptScanController** - Exposes `/api/receipts/scan` and `/api/receipts/submit` endpoints
5. **AiConfig** - Spring configuration that initializes the ChatClient bean

### Data Flow
1. User uploads receipt image to `/api/receipts/scan`
2. LlmReceiptParser converts image to base64 and sends to OpenAI with structured schema
3. OpenAI returns parsed receipt data (store, date, items)
4. ReceiptScanService validates and returns ResScanResult for user review
5. User can edit and submit via `/api/receipts/submit`
6. ReceiptScanService persists purchases to database

## API Endpoints

### POST /api/receipts/scan
Uploads and parses a receipt image.
- **Auth**: JWT (ACTIVE_USER scope)
- **Body**: Multipart form data with `file` field
- **Response**: ResScanResult containing parsed purchases
- **Rate Limit**: 10 requests/hour per user

### POST /api/receipts/submit
Persists parsed purchases to database.
- **Auth**: JWT (ACTIVE_USER scope)
- **Body**: ReqSubmitPurchases with list of purchases
- **Response**: List of ResPurchase records

## Supported File Formats
- JPEG/JPG
- PNG
- GIF
- WebP
- PDF

**Max file size**: 10MB

## Troubleshooting

### "Cannot resolve symbol 'ChatClient'"
This is often an IDE caching issue. The dependencies are correct in build.gradle.
- Run `./gradlew clean build` to clear caches
- Invalidate IDE caches (IntelliJ: File > Invalidate Caches)

### OpenAI API Errors
- Ensure `OPENAI_API_KEY` is set correctly
- Check API key has proper permissions
- Monitor API usage on OpenAI dashboard

### LLM Parsing Failures
- Check receipt image quality and legibility
- Ensure receipt is in supported format
- Try with a clearer, well-lit image

## Cost Considerations
Receipt scanning incurs OpenAI API charges. The rate limiting (10 scans/hour per user) helps control costs.

Current model (gpt-4o-mini) is the most cost-effective option for vision tasks while maintaining good accuracy.

## Future Improvements
- Add response caching for duplicate receipts
- Implement fallback to optical character recognition (OCR)
- Add manual correction UI for ambiguous items
- Implement batch receipt processing