package dev.vasoft.homeapp.receipts.scanning.services;

import java.text.Normalizer;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class NormalizationService {

    public static String normalizeStoreName(String rawStoreName) {
        return normalize(rawStoreName);
    }

    public static String normalizeProductName(String rawProductName) {
        return normalize(rawProductName);
    }

    private static String normalize(String rawValue) {
        if (rawValue == null) {
            return "";
        }

        String withoutDiacritics = Normalizer.normalize(rawValue, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");

        return withoutDiacritics
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", " ")
            .trim()
            .replaceAll("\\s+", " ");
    }
}
