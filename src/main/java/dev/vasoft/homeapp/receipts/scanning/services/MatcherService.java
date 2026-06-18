package dev.vasoft.homeapp.receipts.scanning.services;

import dev.vasoft.homeapp.receipts.common.model.repositories.ProductRepository;
import dev.vasoft.homeapp.receipts.common.model.repositories.StoresRepository;
import dev.vasoft.homeapp.receipts.products.api.response.ResProduct;
import dev.vasoft.homeapp.receipts.products.model.entities.Product;
import dev.vasoft.homeapp.receipts.products.services.ProductMapper;
import dev.vasoft.homeapp.receipts.scanning.api.response.ResScanPurchase;
import dev.vasoft.homeapp.receipts.scanning.api.response.ResScanStore;
import dev.vasoft.homeapp.receipts.scanning.services.ParsedReceipt.ParsedItem;
import dev.vasoft.homeapp.receipts.stores.model.entities.Store;
import dev.vasoft.homeapp.receipts.stores.services.StoreMapper;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MatcherService {

    private static final int MAX_PRODUCT_SUGGESTIONS = 5;
    private static final double MIN_FUZZY_SCORE = 0.45;

    private final StoresRepository storesRepository;
    private final ProductRepository productRepository;

    @Autowired
    public MatcherService(StoresRepository storesRepository, ProductRepository productRepository) {
        this.storesRepository = storesRepository;
        this.productRepository = productRepository;
    }


    /**
     * Matches a raw store name from a receipt to a known store in the database using
     * normalization.
     *
     * @param rawStoreName The stored name as parsed from a receipt
     * @return A ResScanStore containing the matched store (if any) and the original raw store name
     * for reference
     */
    public ResScanStore matchStore(String rawStoreName) {
        String normalizedStoreName = NormalizationService.normalizeStoreName(rawStoreName);
        Store matchedStore = storesRepository.findFirstByNormalizedCanonicalName(
            normalizedStoreName).orElse(null);
        return StoreMapper.toResScanStore(matchedStore, rawStoreName);
    }

    public List<ResScanPurchase> matchProductsToPurchases(List<ParsedItem> items) {
        return items.stream()
            .map(item -> new ResScanPurchase(
                item.productName(),
                matchProduct(item).stream().map(ProductMapper::toResProduct).toList(),
                item.price(),
                item.quantity(),
                item.quantityUnit().name(),
                item.discountAmount()))
            .toList();
    }

    private List<Product> matchProduct(ParsedItem item) {
        String normalizedProductName = NormalizationService.normalizeProductName(item.productName());

        return productRepository.findAll().stream()
            .map(product -> new ProductScore(product, scoreProduct(normalizedProductName, product)))
            .filter(productScore -> productScore.score() >= MIN_FUZZY_SCORE)
            .sorted(Comparator
                .comparingDouble(ProductScore::score).reversed()
                .thenComparing(productScore -> productScore.product().getCanonicalName(),
                    Comparator.nullsLast(String::compareToIgnoreCase)))
            .limit(MAX_PRODUCT_SUGGESTIONS)
            .map(ProductScore::product)
            .toList();
    }

    private double scoreProduct(String normalizedProductName, Product product) {
        String normalizedCanonicalName = normalizedValue(product.getNormalizedCanonicalName(),
            product.getCanonicalName());
        if (normalizedProductName.equals(normalizedCanonicalName)) {
            return 1.0;
        }

        List<String> normalizedAliases = product.getNormalizedAliases();
        if (normalizedAliases != null && normalizedAliases.contains(normalizedProductName)) {
            return 0.99;
        }

        double bestScore = similarity(normalizedProductName, normalizedCanonicalName);
        if (normalizedAliases != null) {
            for (String alias : normalizedAliases) {
                bestScore = Math.max(bestScore, similarity(normalizedProductName, alias));
            }
        }
        return bestScore;
    }

    private String normalizedValue(String preferredNormalizedValue, String rawFallbackValue) {
        if (preferredNormalizedValue != null && !preferredNormalizedValue.isBlank()) {
            return preferredNormalizedValue;
        }
        return NormalizationService.normalizeProductName(rawFallbackValue);
    }

    private double similarity(String left, String right) {
        if (left == null || right == null || left.isBlank() || right.isBlank()) {
            return 0.0;
        }
        if (Objects.equals(left, right)) {
            return 1.0;
        }

        int maxLength = Math.max(left.length(), right.length());
        if (maxLength == 0) {
            return 1.0;
        }

        return 1.0 - ((double) levenshteinDistance(left, right) / maxLength);
    }

    private int levenshteinDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];

        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int substitutionCost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                    Math.min(current[j - 1] + 1, previous[j] + 1),
                    previous[j - 1] + substitutionCost);
            }

            int[] temp = previous;
            previous = current;
            current = temp;
        }

        return previous[right.length()];
    }

    private record ProductScore(Product product, double score) {
    }
}
