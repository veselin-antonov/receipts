package dev.vasoft.homeapp.receipts.scanning.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;

/**
 * Service for parsing receipt images using LLM vision capabilities.
 * Uses Spring AI with structured output to extract purchase data from receipts.
 */
@Service
public class LlmReceiptParser {

    private static final String RECEIPT_PARSING_PROMPT = """
            Analyze this receipt image and extract all purchase information.
            
            Extract the following:
            1. Store name (the business name on the receipt)
            2. Receipt date (in dd/MM/yyyy format)
            3. All purchased items with:
               - Product name (as shown on receipt)
               - Price (as a decimal number, e.g., 12.99)
               - Whether a discount was applied (true/false)
            
            Important:
            - Use the exact product names as they appear on the receipt
            - Convert all prices to decimal numbers (remove currency symbols)
            - Mark items as discounted if they show any discount, sale price, or promotion
            - If the date format is ambiguous, prefer dd/MM/yyyy interpretation
            - If any field cannot be determined, use reasonable defaults (empty string for names, 0.0 for prices, false for discount)
            """;

    private final Logger logger;
    private final ChatClient chatClient;

    @Autowired
    public LlmReceiptParser(ChatClient chatClient) {
        this.logger = LoggerFactory.getLogger(LlmReceiptParser.class);
        this.chatClient = chatClient;
    }

    /**
     * Parses a receipt image using the LLM vision model.
     *
     * @param file The uploaded receipt image file
     * @return Parsed receipt data with store info and line items
     * @throws ReceiptParsingException if parsing fails
     */
    public ParsedReceipt parseReceipt(MultipartFile file) {
        logger.info("Starting receipt parsing for file: {}", file.getOriginalFilename());

        try {
            String base64Image = encodeFileToBase64(file);
            String mediaType = getMediaType(file);

            ParsedReceipt result = chatClient.prompt()
                    .user(userMessageSpec -> userMessageSpec
                            .text(RECEIPT_PARSING_PROMPT)
                            .media(mediaType, base64Image))
                    .call()
                    .entity(ParsedReceipt.class);

            logger.info("Successfully parsed receipt with {} items from store: {}",
                    result.items() != null ? result.items().size() : 0,
                    result.storeName());

            return result;

        } catch (IOException e) {
            logger.error("Failed to read receipt file: {}", e.getMessage(), e);
            throw new ReceiptParsingException("Failed to read receipt file", e);
        } catch (Exception e) {
            logger.error("Failed to parse receipt: {}", e.getMessage(), e);
            throw new ReceiptParsingException("Failed to parse receipt with LLM", e);
        }
    }

    private String encodeFileToBase64(MultipartFile file) throws IOException {
        return Base64.getEncoder().encodeToString(file.getBytes());
    }

    private String getMediaType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null) {
            return "image/jpeg";
        }

        return switch (contentType.toLowerCase()) {
            case "image/jpeg", "image/jpg" -> "image/jpeg";
            case "image/png" -> "image/png";
            case "image/gif" -> "image/gif";
            case "image/webp" -> "image/webp";
            case "application/pdf" -> "application/pdf";
            default -> "image/jpeg";
        };
    }
}