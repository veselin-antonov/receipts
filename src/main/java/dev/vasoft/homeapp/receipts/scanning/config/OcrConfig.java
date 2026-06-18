package dev.vasoft.homeapp.receipts.scanning.config;

import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.ITessAPI.TessOcrEngineMode;
import net.sourceforge.tess4j.ITessAPI.TessPageSegMode;
import net.sourceforge.tess4j.Tesseract;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Tesseract OCR engine used in receipt image preprocessing.
 */
@Configuration
@EnableConfigurationProperties(OcrProperties.class)
public class OcrConfig {

    @Bean
    public Tesseract tesseract(OcrProperties ocrProperties) {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(ocrProperties.dataPath());
        tesseract.setLanguage(ocrProperties.language());
        // OEM 1: LSTM neural network engine (best accuracy for digits)
        tesseract.setOcrEngineMode(TessOcrEngineMode.OEM_LSTM_ONLY);
        // Page segmentation mode 6: assume a single uniform block of text
        tesseract.setPageSegMode(TessPageSegMode.PSM_SINGLE_BLOCK);
        // Hint expected DPI for better internal scaling
        tesseract.setVariable("user_defined_dpi", "300");
        return tesseract;
    }
}