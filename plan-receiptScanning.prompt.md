# Plan: Receipt Scanning Feature with User Data Isolation

Introduce an LLM-powered receipt scanning feature that processes uploaded receipts (images/PDFs), returns structured purchase data for user review/edit, and saves to user-specific data. Requires adding user ownership to entities and new receipt processing module.

## Steps

1. **Add user reference to [Purchase](src/main/java/dev/vasoft/homeapp/receipts/model/entities/Purchase.java)** — Add `userId` field (ObjectId) and update [PurchasesRepository](src/main/java/dev/vasoft/homeapp/receipts/model/repositories/PurchasesRepository.java) with query methods filtered by user; update [PurchaseService](src/main/java/dev/vasoft/homeapp/receipts/services/PurchaseService.java) to extract `userId` from JWT `subject` claim.

2. **Create `receipts/scanning/` submodule** — New bounded context for receipt scanning with:
   - `api/controllers/ReceiptScanController.java` — Endpoints: `POST /api/receipts/scan` (upload file), `POST /api/receipts/submit` (batch save parsed purchases)
   - `api/request/ReqScanReceipt.java` — Multipart file upload request
   - `api/response/ResScanResult.java` — Structured response with parsed purchase list
   - `services/ReceiptScanService.java` — Orchestrates file processing and LLM call
   - `services/LlmReceiptParser.java` — Wraps LLM API calls with structured output parsing

3. **Integrate LLM provider** — Add Spring AI dependency for OpenAI/Azure OpenAI integration; configure API keys via environment variables; define structured output schema for receipt data extraction (store name, items with name/price/discount flag, date).

4. **Extend [PurchaseService](src/main/java/dev/vasoft/homeapp/receipts/services/PurchaseService.java)** — Add `registerPurchases(List<ReqPurchase>, userId)` method for batch insert; reuse existing store/product resolution logic.

5. **Update security rules in [SecurityConfiguration](src/main/java/dev/vasoft/homeapp/auth/config/SecurityConfiguration.java)** — Add `/api/receipts/**` endpoints requiring `SCOPE_ACTIVE_USER`; add rate limiting for scan endpoint (costly LLM calls).

## Further Considerations

1. **LLM Provider Selection** — OpenAI `gpt-4o-mini` offers good cost/performance for OCR+parsing tasks; alternatively, Claude 3 Haiku or Google Gemini Flash for lower latency. Which provider do you prefer?

2. **Sessionless Architecture** — Current JWT stateless approach works well; parsed results returned directly in response (no server-side state needed); user edits happen client-side before batch submit.

3. **Existing Data Migration** — Current purchases have no `userId`; need migration strategy — Option A: backfill with null (shared legacy data) / Option B: assign to admin user / Option C: delete existing test data.