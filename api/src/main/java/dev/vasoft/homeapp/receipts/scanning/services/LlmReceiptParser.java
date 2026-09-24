package dev.vasoft.homeapp.receipts.scanning.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service for parsing receipt content using LLM capabilities.
 * Supports two modes:
 * <ul>
 *   <li>Vision mode — sends image/PDF directly to the LLM for visual parsing</li>
 *   <li>Text mode — sends OCR-extracted text to the LLM for structuring</li>
 * </ul>
 */
@Service
public class LlmReceiptParser {

    private static final String RECEIPT_VISION_PROMPT = """
            You are an expert receipt parser. Analyze this receipt image and extract all purchase information into structured data.

            ## Store name
            Return the name of the SHOP, not the company that owns it.

            Bulgarian receipts print the registered company in the header and the \
            actual shop on a separate line. Prefer the shop:

            - "\"ЛАГАРДЕР ТРАВЕЛ РИТЕЙЛ\" ЕООД" in the header, "МАГАЗИН \"RELAY\"" \
              below it -> return Relay
            - "\"ТРЪНЧЕВ\" ООД" in the header, "Магазин за хр. стоки BulMag 17" \
              below it -> return BulMag
            - "ПОЛИМЕКС ЕООД" in the header, "АПТЕКА \"ПОЛИ ВАЛЕНС\"" below it \
              -> return Поли Валенс

            Look for lines beginning МАГАЗИН, АПТЕКА, Хипермаркет, or a chain name \
            with a branch number such as "Лидл Варна 144". A trailing ЕООД, ООД, АД \
            or КД marks the legal entity, so strip it and drop qualifiers like \
            "България". Where only the legal entity is printed, return its \
            distinctive part: "Кауфланд България ЕООД енд Ко. КД" -> Кауфланд.

            ## Receipt date
            The date is almost never in the header. On Bulgarian fiscal receipts it \
            appears BELOW the totals: usually inside the card-payment block prefixed \
            with '#', or after a "Дата:" label near the very bottom, with a time \
            beside it.

            Separators vary - 03.10.2025, 15-04-2025 and 24/03/2025 are all the same \
            shape. Years may be two digits: "Дата: 18.09.26" means 2026.

            Ignore dates inside terms-and-conditions text. A phrase such as \
            "за стоки, закупени след 01.01.22г" states a policy date, not a purchase \
            date. A date with a time next to it is almost always the right one.

            If several dates appear, take the transaction date from the payment block. \
            Return dd/MM/yyyy, preferring that reading when ambiguous. If no purchase \
            date is present, return null rather than guessing.

            ## Items
            Extract every purchased product line item. For each item provide:

            - **productName**: The product name exactly as printed on the receipt.
            - **price**: The ORIGINAL LISTED price for this line item BEFORE any discounts or coupons, \
              as a decimal number (e.g. 12.99). Remove any currency symbols. \
              If the item shows quantity × unit price, use the total line price before discounts. \
              CRITICAL: Do NOT subtract any discounts or coupons from this price. This must always be \
              the gross/full price as originally listed on the item line.
            - **quantity**: The quantity purchased. Look for patterns like "2 x", "×3", "0.500 kg", "1.5 L". \
              Default to 1 if not specified.
            - **quantityUnit**: One of: PIECE (default, sold by count or no unit), GRAM (g), \
              KILOGRAM (kg), MILLILITER (ml), LITER (l/L).
            - **discountAmount**: The total monetary savings as a positive decimal (e.g. 1.50). \
              This is the absolute difference between the original listed price and what the customer \
              actually paid for this item. Use 0.0 if no discount applies.

            ## Discount detection — READ CAREFULLY

            Receipts use many different formats for discounts. You MUST scan the ENTIRE receipt, including \
            the bottom section, before determining discounts. Common patterns:

            ### Pattern 1: Inline discount immediately below the item
            An item line is followed directly by a negative amount or a line mentioning a discount/coupon. \
            Example:
            ```
            Biscuits Pack          1.89
              Coupon              -1.88
            ```
            → Item "Biscuits Pack": price=1.89, discountAmount=1.88

            ### Pattern 2: Numbered discount/coupon section at the bottom
            A section at the end of the receipt lists discounts referencing items by their line number \
            or sequence number on the receipt. Examples:
            ```
            Discount 12           -1.55
            Discount 22           -4.56
            Discount 25           -0.83
            ```
            → Items at positions 12, 22, and 25 are discounted by the stated amounts. \
            Match these back to the corresponding item by counting item lines from top to bottom.

            ### Pattern 3: Crossed-out or struck-through original price
            The receipt shows both an old (higher) price and a new (lower) price on the same line, \
            or the original price is visually struck through. \
            → price = the higher/original price, discountAmount = higher − lower.

            ### Pattern 4: Percentage discount on the item line
            A percentage like "-20%", "10% OFF" appears next to or below the item. \
            → Compute discountAmount = price × percentage / 100.

            ### Pattern 5: "Was / Now" or "Regular / Sale" pricing
            Two prices appear: the regular price and a sale/promotional price. \
            → price = regular (higher) price, discountAmount = regular − sale.

            ### Pattern 6: Multi-buy / bundle promotions
            Promotions like "Buy 2 get 1 free", "3 for 2", or "2nd item 50% off". \
            → Distribute the total discount equally across the items in the promotion.

            ### Pattern 7: Loyalty card / member discounts
            A section showing member or card-based savings. These may be listed as a lump sum or per item. \
            → If per-item amounts are shown, apply them to the corresponding items. \
            If only a total member savings is shown and individual items are not identifiable, skip it.

            ### General discount keywords (multiple languages)
            Look for: discount, coupon, promo, sale, markdown, savings, отстъпка, промоция, намаление, \
            купон, rabatt, remise, descuento, sconto, Rabatt, korting, and any negative monetary value \
            (prefixed with "-" or in parentheses) appearing near or referencing an item.

            ## Important rules
            - Do NOT include subtotals, totals, tax lines, payment method lines, or change lines as items.
            - Process the ENTIRE receipt before outputting results — discounts at the bottom may apply to \
              items listed at the top.
            - The price field must ALWAYS be the original listed price BEFORE any discount is subtracted.
            - If a field cannot be determined, use defaults: empty string for names, 0.0 for price, \
              1 for quantity, PIECE for unit, 0.0 for discountAmount.
            """;

    private static final String RECEIPT_TEXT_PARSING_PROMPT = """
            You are an expert receipt parser. Analyze the following OCR-extracted text from a receipt \
            and extract all purchase information into structured data.

            The text below was extracted via OCR from a receipt image. It may contain minor OCR errors \
            (misread characters, merged lines, etc.) — use context to correct obvious mistakes.

            ## OCR Text
            ```
            {ocrText}
            ```

            ## Store name
            Return the name of the SHOP, not the company that owns it.

            Bulgarian receipts print the registered company in the header and the \
            actual shop on a separate line. Prefer the shop:

            - "\"ЛАГАРДЕР ТРАВЕЛ РИТЕЙЛ\" ЕООД" in the header, "МАГАЗИН \"RELAY\"" \
              below it -> return Relay
            - "\"ТРЪНЧЕВ\" ООД" in the header, "Магазин за хр. стоки BulMag 17" \
              below it -> return BulMag
            - "ПОЛИМЕКС ЕООД" in the header, "АПТЕКА \"ПОЛИ ВАЛЕНС\"" below it \
              -> return Поли Валенс

            Look for lines beginning МАГАЗИН, АПТЕКА, Хипермаркет, or a chain name \
            with a branch number such as "Лидл Варна 144". A trailing ЕООД, ООД, АД \
            or КД marks the legal entity, so strip it and drop qualifiers like \
            "България". Where only the legal entity is printed, return its \
            distinctive part: "Кауфланд България ЕООД енд Ко. КД" -> Кауфланд.

            ## Receipt date
            The date is almost never in the header. On Bulgarian fiscal receipts it \
            appears BELOW the totals: usually inside the card-payment block prefixed \
            with '#', or after a "Дата:" label near the very bottom, with a time \
            beside it.

            Separators vary - 03.10.2025, 15-04-2025 and 24/03/2025 are all the same \
            shape. Years may be two digits: "Дата: 18.09.26" means 2026.

            Ignore dates inside terms-and-conditions text. A phrase such as \
            "за стоки, закупени след 01.01.22г" states a policy date, not a purchase \
            date. A date with a time next to it is almost always the right one.

            If several dates appear, take the transaction date from the payment block. \
            Return dd/MM/yyyy, preferring that reading when ambiguous. If no purchase \
            date is present, return null rather than guessing.

            ## Items
            Extract every purchased product line item. For each item provide:

            - **productName**: The product name exactly as printed on the receipt.
            - **price**: The ORIGINAL LISTED price for this line item BEFORE any discounts or coupons, \
              as a decimal number (e.g. 12.99). Remove any currency symbols. \
              If the item shows quantity × unit price, use the total line price before discounts. \
              CRITICAL: Do NOT subtract any discounts or coupons from this price. This must always be \
              the gross/full price as originally listed on the item line.
            - **quantity**: The quantity purchased. Look for patterns like "2 x", "×3", "0.500 kg", "1.5 L". \
              Default to 1 if not specified.
            - **quantityUnit**: One of: PIECE (default, sold by count or no unit), GRAM (g), \
              KILOGRAM (kg), MILLILITER (ml), LITER (l/L).
            - **discountAmount**: The total monetary savings as a positive decimal (e.g. 1.50). \
              This is the absolute difference between the original listed price and what the customer \
              actually paid for this item. Use 0.0 only when no discount applies.

            ## Discount detection — READ CAREFULLY

            Receipts use many different formats for discounts. You MUST scan the ENTIRE text, including \
            the bottom section, before determining discounts. Common patterns:

            ### Pattern 1: Inline discount immediately below the item
            An item line is followed directly by a negative amount or a line mentioning a discount/coupon. \
            Example:
            ```
            Biscuits Pack          1.89
              Coupon              -1.88
            ```
            → Item "Biscuits Pack": price=1.89, discountAmount=1.88

            ### Pattern 2: Numbered discount/coupon section at the bottom
            A section at the end of the receipt lists discounts referencing items by their line number \
            or sequence number on the receipt. Examples:
            ```
            Discount 12           -1.55
            Discount 22           -4.56
            Discount 25           -0.83
            ```
            → Items at positions 12, 22, and 25 are discounted by the stated amounts. \
            Match these back to the corresponding item by counting item lines from top to bottom.

            ### Pattern 3: Percentage discount on the item line
            A percentage like "-20%", "10% OFF" appears next to or below the item. \
            → Compute discountAmount = price × percentage / 100.

            ### Pattern 4: "Was / Now" or "Regular / Sale" pricing
            Two prices appear: the regular price and a sale/promotional price. \
            → price = regular (higher) price, discountAmount = regular − sale.

            ### Pattern 5: Multi-buy / bundle promotions
            Promotions like "Buy 2 get 1 free", "3 for 2", or "2nd item 50% off". \
            → Distribute the total discount equally across the items in the promotion.

            ### Pattern 6: Loyalty card / member discounts
            A section showing member or card-based savings. These may be listed as a lump sum or per item. \
            → If per-item amounts are shown, apply them to the corresponding items. \
            If only a total member savings is shown and individual items are not identifiable, skip it.

            ### General discount keywords (multiple languages)
            Look for: discount, coupon, promo, sale, markdown, savings, отстъпка, промоция, намаление, \
            купон, rabatt, remise, descuento, sconto, Rabatt, korting, and any negative monetary value \
            (prefixed with "-" or in parentheses) appearing near or referencing an item.

            ## Important rules
            - Do NOT include subtotals, totals, tax lines, payment method lines, or change lines as items.
            - Process the ENTIRE text before outputting results — discounts at the bottom may apply to \
              items listed at the top.
            - The price field must ALWAYS be the original listed price BEFORE any discount is subtracted.
            - If a field cannot be determined, use defaults: empty string for names, 0.0 for price, \
              1 for quantity, PIECE for unit, 0.0 for discountAmount.
            """;

    private final Logger logger;
    private final ChatClient chatClient;

    @Autowired
    public LlmReceiptParser(ChatClient chatClient) {
        this.logger = LoggerFactory.getLogger(LlmReceiptParser.class);
        this.chatClient = chatClient;
    }

    /**
     * Parses a receipt file (PDF) using the LLM vision model.
     *
     * @param file The uploaded receipt file
     * @return Parsed receipt data with store info and line items
     * @throws ReceiptParsingException if parsing fails
     */
    public ParsedReceipt parseReceipt(MultipartFile file) {
        logger.info("Starting vision-based receipt parsing for file: {}", file.getOriginalFilename());

        try {
            MimeType mimeType = getMimeType(file);
            Resource resource = file.getResource();

            ParsedReceipt result = chatClient.prompt()
                    .user(userMessageSpec -> userMessageSpec
                            .text(RECEIPT_VISION_PROMPT)
                            .media(mimeType, resource))
                    .call()
                    .entity(ParsedReceipt.class);

            logResult(result);
            return result;

        } catch (Exception e) {
            throw new ReceiptParsingException("Failed to parse receipt with LLM", e);
        }
    }

    /**
     * Parses OCR-extracted receipt text using the LLM (text-only, no vision).
     *
     * @param ocrText The text extracted from a receipt image via OCR
     * @return Parsed receipt data with store info and line items
     * @throws ReceiptParsingException if parsing fails
     */
    public ParsedReceipt parseReceiptText(String ocrText) {
        logger.info("Starting text-based receipt parsing from OCR output ({} chars)", ocrText.length());

        try {
            String prompt = RECEIPT_TEXT_PARSING_PROMPT.replace("{ocrText}", ocrText);

            ParsedReceipt result = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .entity(ParsedReceipt.class);

            logResult(result);
            return result;

        } catch (Exception e) {
            throw new ReceiptParsingException("Failed to parse receipt text with LLM", e);
        }
    }

    private void logResult(ParsedReceipt result) {
        int itemCount = (result != null && result.items() != null) ? result.items().size() : 0;
        String storeName = result != null ? result.storeName() : "unknown";
        logger.info("Successfully parsed receipt with {} items from store: {}", itemCount, storeName);
    }

    private MimeType getMimeType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null) {
            return MimeTypeUtils.IMAGE_JPEG;
        }

        return switch (contentType.toLowerCase()) {
            case "image/png" -> MimeTypeUtils.IMAGE_PNG;
            case "image/gif" -> MimeTypeUtils.IMAGE_GIF;
            case "image/webp" -> MimeType.valueOf("image/webp");
            case "application/pdf" -> MimeType.valueOf("application/pdf");
            default -> MimeTypeUtils.IMAGE_JPEG;
        };
    }
}