package dev.vasoft.homeapp.receipts.scanning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Tesseract OCR.
 *
 * @param dataPath Path to the tessdata directory containing trained language data files
 * @param language Tesseract language code (e.g. "eng", "bul", "eng+bul" for multiple)
 * @param debugOutputPath When set, preprocessed images are saved to this directory for debugging.
 *                        Leave null/empty to disable.
 */
@ConfigurationProperties(prefix = "app.ocr")
public record OcrProperties(
        String dataPath,
        String language,
        String debugOutputPath
) {
}
