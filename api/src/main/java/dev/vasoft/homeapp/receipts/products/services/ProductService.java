package dev.vasoft.homeapp.receipts.products.services;

import dev.vasoft.homeapp.receipts.common.matching.TokenSetScorer;
import dev.vasoft.homeapp.receipts.products.api.controllers.ProductController;
import dev.vasoft.homeapp.receipts.products.api.response.ResProduct;
import dev.vasoft.homeapp.receipts.products.api.response.ResProductSearchResult;
import dev.vasoft.homeapp.receipts.products.model.ProductPurchaseSummary;
import dev.vasoft.homeapp.receipts.products.model.entities.Product;
import dev.vasoft.homeapp.receipts.common.model.repositories.ProductRepository;
import dev.vasoft.homeapp.receipts.scanning.services.NormalizationService;
import dev.vasoft.homeapp.receipts.stores.services.StoreMapper;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ProductService {

    /** Results scoring below this are not shown at all. */
    static final double MIN_RELEVANCE = 0.55;
    /**
     * How far buying habits can lift a result. Small on purpose: relevance
     * decides, and habit only reorders results of about equal relevance, so a
     * product bought weekly beats one never bought when both match equally well.
     */
    static final double USAGE_WEIGHT = 0.1;
    /** Days after which a purchase counts half as recent. */
    static final double RECENCY_HALF_LIFE_DAYS = 90;
    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 50;
    /** Longer queries are cut; nobody types a product name this long. */
    static final int MAX_QUERY_LENGTH = 100;

    private final ProductRepository productRepository;
    private final TokenSetScorer scorer = new TokenSetScorer();
    private final Clock clock;
    private final Logger logger;

    @Autowired
    public ProductService(ProductRepository productRepository) {
        this(productRepository, Clock.systemDefaultZone());
    }

    ProductService(ProductRepository productRepository, Clock clock) {
        this.productRepository = productRepository;
        this.clock = clock;
        this.logger = LoggerFactory.getLogger(ProductController.class);
    }

    public List<ResProduct> getAllProducts() {
        List<Product> products = productRepository.getProductsForDisplay();
        return products.stream().map(ProductMapper::toResProduct).toList();
    }

    public ResProduct registerProduct(String name) {
        Product newProduct = new Product(name);
        newProduct.setNormalizedCanonicalName(NormalizationService.normalizeProductName(name));
        newProduct = productRepository.save(newProduct);
        return new ResProduct(newProduct.getId().toHexString(),
            newProduct.getCanonicalName(), newProduct.getIconID());
    }

    /**
     * Searches the catalog for {@code query} (SPEC F2.1, defect D5). Each
     * product is scored on its canonical name and every alias by
     * {@link TokenSetScorer}, keeping its best; results under
     * {@value #MIN_RELEVANCE} are dropped. The order is relevance, lifted by at
     * most {@value #USAGE_WEIGHT} for how often and how recently this user buys
     * the product, then the name. Each result carries what the user paid last.
     *
     * @param limit how many results to return; 0 or less means the default,
     *              and more than {@value #MAX_LIMIT} is capped
     */
    public List<ResProductSearchResult> search(ObjectId userId, String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String text = query.length() > MAX_QUERY_LENGTH
            ? query.substring(0, MAX_QUERY_LENGTH) : query;
        TokenSetScorer.Query q = scorer.query(text);
        if (q.isEmpty()) {
            return List.of();
        }
        int max = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);

        List<Scored> matches = new ArrayList<>();
        for (Product product : productRepository.getProductsForSearch()) {
            double relevance = relevance(q, product);
            if (relevance >= MIN_RELEVANCE) {
                matches.add(new Scored(product, relevance));
            }
        }
        if (matches.isEmpty()) {
            return List.of();
        }

        Map<ObjectId, ProductPurchaseSummary> summaries = productRepository
            .getPurchaseSummaries(userId).stream()
            .filter(s -> s.getProductId() != null)
            .collect(Collectors.toMap(ProductPurchaseSummary::getProductId,
                Function.identity(), (a, b) -> a));
        long mostBought = summaries.values().stream()
            .mapToLong(ProductPurchaseSummary::getPurchaseCount).max().orElse(0);
        LocalDate today = LocalDate.now(clock);

        return matches.stream()
            .map(m -> {
                ProductPurchaseSummary summary = summaries.get(m.product().getId());
                double rank = m.relevance() + USAGE_WEIGHT * usage(summary, mostBought, today);
                return new Ranked(m, summary, rank);
            })
            .sorted(Comparator.comparingDouble(Ranked::rank).reversed()
                .thenComparing(r -> r.scored().product().getCanonicalName(),
                    Comparator.nullsLast(String::compareToIgnoreCase)))
            .limit(max)
            .map(r -> toResult(r.scored(), r.summary()))
            .toList();
    }

    /**
     * The catalog product {@code name} most likely means, or null when nothing
     * scores at least {@value #MIN_RELEVANCE}. Ties go to the earlier name
     * alphabetically, so the answer does not depend on storage order.
     */
    public Product findProductByNameFuzzy(String name) {
        TokenSetScorer.Query q = scorer.query(name);
        if (q.isEmpty()) {
            return null;
        }
        return productRepository.getProductsForSearch().stream()
            .map(p -> new Scored(p, relevance(q, p)))
            .filter(s -> s.relevance() >= MIN_RELEVANCE)
            .min(Comparator.comparingDouble(Scored::relevance).reversed()
                .thenComparing(s -> s.product().getCanonicalName(),
                    Comparator.nullsLast(String::compareToIgnoreCase)))
            .map(Scored::product)
            .orElse(null);
    }

    private double relevance(TokenSetScorer.Query q, Product product) {
        List<String> names = new ArrayList<>();
        if (product.getCanonicalName() != null) {
            names.add(product.getCanonicalName());
        }
        if (product.getAliases() != null) {
            names.addAll(product.getAliases());
        }
        return q.bestScore(names).value();
    }

    /**
     * Buying habit in [0, 1]: half frequency, against the user's most bought
     * product on a log scale so that one staple does not flatten the rest, and
     * half recency, halving every {@value #RECENCY_HALF_LIFE_DAYS} days.
     */
    static double usage(ProductPurchaseSummary summary, long mostBought, LocalDate today) {
        if (summary == null || summary.getPurchaseCount() <= 0 || mostBought <= 0) {
            return 0;
        }
        double frequency = Math.log1p(summary.getPurchaseCount()) / Math.log1p(mostBought);
        double recency = 0;
        if (summary.getLastDate() != null) {
            long days = Math.max(0, ChronoUnit.DAYS.between(summary.getLastDate(), today));
            recency = Math.pow(0.5, days / RECENCY_HALF_LIFE_DAYS);
        }
        return 0.5 * Math.min(1, frequency) + 0.5 * recency;
    }

    private static ResProductSearchResult toResult(Scored scored, ProductPurchaseSummary summary) {
        Product product = scored.product();
        return new ResProductSearchResult(
            product.getId().toHexString(),
            product.getCanonicalName(),
            product.getIconID(),
            scored.relevance(),
            summary != null ? summary.getPurchaseCount() : 0,
            lastPurchase(summary));
    }

    private static ResProductSearchResult.LastPurchase lastPurchase(
            ProductPurchaseSummary summary) {
        // No currency means a row the backfill has not reached; its amount
        // cannot be converted without guessing (SPEC §9.5), so it is left out
        if (summary == null || summary.getLastPrice() == null
                || summary.getLastCurrency() == null) {
            return null;
        }
        return new ResProductSearchResult.LastPurchase(
            summary.getLastCurrency().toEur(summary.getLastPrice()),
            summary.getLastDate(),
            StoreMapper.toResStore(summary.getLastStore()));
    }

    private record Scored(Product product, double relevance) {
    }

    private record Ranked(Scored scored, ProductPurchaseSummary summary, double rank) {
    }

// TODO Reimplement product details page

//    public ResProductDetails getDetailsById(String productId) {
//        Product relatedProduct = productRepository.findById(productId).orElse(null);
//
//        if (relatedProduct == null) {
//            return null;
//        }
//
//        Statistics statistics = relatedProduct.getStatistics();
//
//        if (statistics == null) {
//            statistics = updateStatistics(relatedProduct);
//        }
//
//        ResStore resMostFrequentStore = statistics.getUsualStore() != null ? new ResStore(statistics.getUsualStore().getId(), statistics.getUsualStore().getName(), statistics.getUsualStore().getIconID()) : null;
//
//        List<ResStatistics.Common> commonStatistics = List.of(new ResStatistics.Common(String.format("%.2f", statistics.getAveragePrice()), "Средна Цена"), new ResStatistics.Common(String.format("%.2f", statistics.getLowestPrice()), "Най-ниска Цена"), new ResStatistics.Common(String.valueOf(statistics.getAverageMonthlyCount()), "Среднен Брой Месечно"));
//
//        ResStatistics.Store usualStore = new ResStatistics.Store(resMostFrequentStore, "Обичаен Магазин");
//
//        //Try with findAllByProductId
//        List<Purchase> purchases = purchasesRepository.findAllByProductId(relatedProduct.getId());
//
//        List<ResPurchase> resPurchases = purchases.stream().map(p -> new ResPurchase(null, String.format("%.2f лв.", p.getPrice()), p.getDate() != null ? DateTimeFormatter.ofPattern("dd-MM-yy").format(p.getDate()) + "г." : null, p.getStore() != null ? new ResStore(p.getStore().getId(), p.getStore().getName(), p.getStore().getIconID()) : null, p.getIsDiscounted())).toList();
//
//        ResStatistics resStatistics = new ResStatistics(commonStatistics, usualStore);
//
//        return new ResProductDetails(resStatistics, resPurchases);
//    }
}