package dev.vasoft.homeapp.receipts.scanning.services;

import dev.vasoft.homeapp.receipts.common.services.Formatter;
import dev.vasoft.homeapp.receipts.purchases.api.request.ReqPurchase;
import dev.vasoft.homeapp.receipts.purchases.api.response.ResPurchase;
import dev.vasoft.homeapp.receipts.scanning.api.response.ResParsedPurchase;
import dev.vasoft.homeapp.receipts.scanning.api.response.ResScanPurchase;
import dev.vasoft.homeapp.receipts.scanning.api.response.ResScanResult;
import dev.vasoft.homeapp.receipts.purchases.services.PurchaseService;
import dev.vasoft.homeapp.receipts.scanning.api.response.ResScanStore;
import dev.vasoft.homeapp.receipts.stores.model.entities.Store;
import dev.vasoft.homeapp.receipts.stores.services.StoreService;
import dev.vasoft.homeapp.receipts.purchases.services.PurchaseMapper;
import java.util.ArrayList;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

/**
 * Service for orchestrating receipt scanning operations. Routes images through OCR preprocessing
 * before LLM parsing, while PDFs are sent directly to the LLM vision model.
 */
@Service
public class ReceiptScanService {

    private static final Set<String> IMAGE_CONTENT_TYPES = Set.of("image/jpeg", "image/jpg",
        "image/png", "image/gif", "image/webp");

    private final Logger logger;
    private final OcrService ocrService;
    private final LlmReceiptParser llmReceiptParser;
    private final PurchaseService purchaseService;
    private final StoreService storeService;
    private final MatcherService matcherService;

    @Autowired
    public ReceiptScanService(OcrService ocrService, LlmReceiptParser llmReceiptParser,
        PurchaseService purchaseService, StoreService storeService, MatcherService matcherService) {
        this.storeService = storeService;
        this.logger = LoggerFactory.getLogger(ReceiptScanService.class);
        this.ocrService = ocrService;
        this.llmReceiptParser = llmReceiptParser;
        this.purchaseService = purchaseService;
        this.matcherService = matcherService;
    }

    /**
     * Scans a receipt file and returns parsed purchase data for user review. Images are
     * preprocessed with OCR before LLM parsing; PDFs use direct LLM vision.
     *
     * @param file The uploaded receipt file (image or PDF)
     * @return Parsed receipt data ready for user review/editing
     */
    public ResScanResult scanReceipt(MultipartFile file) {
        logger.info("Scanning receipt: {}", file.getOriginalFilename());

        validateFile(file);

        ParsedReceipt parsedReceipt =
            isImage(file) ? parseImageReceipt(file) : llmReceiptParser.parseReceipt(file);

        ResScanStore store = matcherService.matchStore(parsedReceipt.storeName());

        List<ResScanPurchase> purchases = matcherService.matchProductsToPurchases(
            parsedReceipt.items());

        return new ResScanResult(store, parsedReceipt.storeName(), parsedReceipt.receiptDate(),
            purchases);
    }

    /**
     * Parses an image receipt through OCR preprocessing followed by LLM text parsing.
     */
    private ParsedReceipt parseImageReceipt(MultipartFile file) {
        logger.info("Image detected — routing through OCR preprocessing: {}",
            file.getOriginalFilename());
        String ocrText = ocrService.extractText(file);
        return llmReceiptParser.parseReceiptText(ocrText);
    }

    private boolean isImage(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && IMAGE_CONTENT_TYPES.contains(contentType.toLowerCase());
    }

    /**
     * Submits parsed and potentially user-edited purchases for persistence.
     *
     * @param userId    The ID of the user submitting the purchases
     * @param purchases The list of purchases to save
     * @return List of saved purchase records
     */
    public List<ResPurchase> submitPurchases(ObjectId userId, List<ReqPurchase> purchases) {
        logger.info("Submitting {} purchases for user: {}", purchases.size(), userId);

        return purchaseService.registerPurchases(userId, purchases);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ReceiptParsingException("File is empty or not provided");
        }

        String contentType = file.getContentType();
        if (contentType == null) {
            throw new ReceiptParsingException("File content type is not specified");
        }

        List<String> allowedTypes = new ArrayList<>(IMAGE_CONTENT_TYPES);
        allowedTypes.add("application/pdf");

        if (!allowedTypes.contains(contentType.toLowerCase())) {
            throw new ReceiptParsingException("Unsupported file type: " + contentType
                + ". Allowed types: JPEG, PNG, GIF, WebP, PDF");
        }

        // 10MB max file size
        long maxSize = 10L * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new ReceiptParsingException("File size exceeds maximum allowed size of 10MB");
        }
    }
}