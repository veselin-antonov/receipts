package dev.vasoft.homeapp.receipts.scanning.services;

import dev.vasoft.homeapp.receipts.api.request.ReqPurchase;
import dev.vasoft.homeapp.receipts.api.response.ResPurchase;
import dev.vasoft.homeapp.receipts.scanning.api.response.ResParsedPurchase;
import dev.vasoft.homeapp.receipts.scanning.api.response.ResScanResult;
import dev.vasoft.homeapp.receipts.services.PurchaseService;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service for orchestrating receipt scanning operations.
 * Coordinates file processing, LLM parsing, and persistence of purchases.
 */
@Service
public class ReceiptScanService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final Logger logger;
    private final LlmReceiptParser llmReceiptParser;
    private final PurchaseService purchaseService;

    @Autowired
    public ReceiptScanService(LlmReceiptParser llmReceiptParser, PurchaseService purchaseService) {
        this.logger = LoggerFactory.getLogger(ReceiptScanService.class);
        this.llmReceiptParser = llmReceiptParser;
        this.purchaseService = purchaseService;
    }

    /**
     * Scans a receipt image and returns parsed purchase data for user review.
     *
     * @param file The uploaded receipt image
     * @return Parsed receipt data ready for user review/editing
     */
    public ResScanResult scanReceipt(MultipartFile file) {
        logger.info("Scanning receipt: {}", file.getOriginalFilename());

        validateFile(file);

        ParsedReceipt parsedReceipt = llmReceiptParser.parseReceipt(file);

        List<ResParsedPurchase> purchases = parsedReceipt.items().stream()
                .map(item -> new ResParsedPurchase(
                        item.productName(),
                        parsedReceipt.storeName(),
                        item.price(),
                        parsedReceipt.receiptDate(),
                        item.hasDiscount()
                ))
                .toList();

        String formattedDate = parsedReceipt.receiptDate() != null
                ? parsedReceipt.receiptDate().format(DATE_FORMATTER)
                : "";

        return new ResScanResult(
                parsedReceipt.storeName(),
                formattedDate,
                purchases,
                null // rawText not exposed in current implementation
        );
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

        List<String> allowedTypes = List.of(
                "image/jpeg",
                "image/jpg",
                "image/png",
                "image/gif",
                "image/webp",
                "application/pdf"
        );

        if (!allowedTypes.contains(contentType.toLowerCase())) {
            throw new ReceiptParsingException(
                    "Unsupported file type: " + contentType + ". Allowed types: JPEG, PNG, GIF, WebP, PDF");
        }

        // 10MB max file size
        long maxSize = 10L * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new ReceiptParsingException("File size exceeds maximum allowed size of 10MB");
        }
    }
}