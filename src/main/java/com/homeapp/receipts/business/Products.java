package com.homeapp.receipts.business;

import com.homeapp.receipts.api.controllers.ProductController;
import com.homeapp.receipts.api.response.*;
import com.homeapp.receipts.model.entities.Product;
import com.homeapp.receipts.model.entities.Purchase;
import com.homeapp.receipts.model.entities.Statistics;
import com.homeapp.receipts.model.entities.Store;
import com.homeapp.receipts.model.repositories.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Component
public class Products {
    private final ProductRepository productRepository;
    private final VendorRepository vendorRepository;
    private final PurchasesRepository purchasesRepository;
    private final StatisticsRepository statisticsRepository;
    private final CustomRepository customRepository;
    private final Logger logger;

    @Autowired
    public Products(
        ProductRepository productRepository,
        VendorRepository vendorRepository,
        PurchasesRepository purchasesRepository,
        StatisticsRepository statisticsRepository,
        CustomRepository customRepository
    ) {
        this.productRepository = productRepository;
        this.vendorRepository = vendorRepository;
        this.purchasesRepository = purchasesRepository;
        this.statisticsRepository = statisticsRepository;
        this.customRepository = customRepository;
        this.logger = LoggerFactory.getLogger(ProductController.class);
    }


    private void generateTestData() {
        List<Store> stores = List.of(
            vendorRepository.save(new Store("Kaufland", "kaufland")),
            vendorRepository.save(new Store("Lidl", "lidl")),
            vendorRepository.save(new Store("Billa", "billa"))
        );

        List<Product> products = List.of(
            productRepository.save(new Product("Банани", "")),
            productRepository.save(new Product("Сирене", "")),
            productRepository.save(new Product("Шоколад", ""))
        );

        for (Product product : products) {
            List<Purchase> purchases = new LinkedList<>();
            for (int j = 0; j < 50; j++) {
                purchases.add(
                    purchasesRepository.save(new Purchase(
                        product,
                        ThreadLocalRandom.current().nextDouble(3, 7),
                        LocalDate.of(
                            ThreadLocalRandom.current().nextInt(2022, 2024),
                            ThreadLocalRandom.current().nextInt(1, 13),
                            ThreadLocalRandom.current().nextInt(3, 13)
                        ),
                        stores.get(ThreadLocalRandom.current().nextInt(0, 3)), ThreadLocalRandom.current().nextBoolean()
                    ))
                );
            }
            product.setPurchases(purchases);
            productRepository.save(product);
            updateStatistics(product);
        }
    }

    public List<ResProduct> getAllProducts() {
        List<Product> products = customRepository.getProductsForDisplay();
        return products.stream().map(prod -> new ResProduct(prod.getId(), prod.getName(), prod.getIconID())).toList();
    }

    public ResProductDetails getDetailsById(String productId) {
        Product relatedProduct = productRepository.findById(productId).orElse(null);

        if (relatedProduct == null) {
            return null;
        }

        Statistics statistics = relatedProduct.getStatistics();

        if (statistics == null) {
            statistics = updateStatistics(relatedProduct);
        }

        ResStore resMostFrequentStore = statistics.getUsualStore() != null ? new ResStore(
            statistics.getUsualStore().getName(),
            statistics.getUsualStore().getLogoURL()
        ) : null;

        List<ResStatistics.Common> commonStatistics = List.of(
            new ResStatistics.Common(String.format("%.2f", statistics.getAveragePrice()), "Средна Цена"),
            new ResStatistics.Common(String.format("%.2f", statistics.getLowestPrice()), "Най-ниска Цена"),
            new ResStatistics.Common(String.valueOf(statistics.getAverageMonthlyCount()), "Среднен Брой Месечно")
        );

        ResStatistics.Store usualStore = new ResStatistics.Store(
            resMostFrequentStore, "Обичаен Магазин"
        );

        List<Purchase> purchases = relatedProduct.getPurchases();

        ResProductDetails resProductDetails = new ResProductDetails();
        resProductDetails.setPurchases(purchases.stream().map(
            p -> new ResPurchase(
                String.format("%.2f лв.", p.getPrice()),
                p.getDate().format(DateTimeFormatter.ofPattern("dd.MM.yy")) + "г.",
                new ResStore(p.getStore().getName(), p.getStore().getLogoURL()),
                p.getDiscount())
        ).toList());
        resProductDetails.setStats(
            new ResStatistics(
                commonStatistics,
                usualStore
            )
        );

        return resProductDetails;
    }

    private Statistics updateStatistics(Product relatedProduct) {
        List<Purchase> purchases = relatedProduct.getPurchases();

        double averagePrice = purchases.stream().mapToDouble(Purchase::getPrice).average().orElse(0);

        double lowestPrice = purchases.stream().mapToDouble(Purchase::getPrice).min().orElse(0);

        int averageMonthlyCount = (int) Math.round(purchases
            .stream()
            .filter(p -> p.getDate().isAfter(p.getDate().minus(Period.ofYears(1).withDays(1))))
            .collect(Collectors.groupingBy(purchase -> purchase.getDate().getMonth()))
            .values()
            .stream()
            .mapToInt(List::size)
            .average().orElse(0));

        Store mostFrequentStore = customRepository.mostFrequentStore(
            relatedProduct.getId(),
            LocalDate
                .now()
                .minusYears(1)
                .withDayOfMonth(1)
        );

        Statistics statistics = new Statistics(
            averagePrice,
            lowestPrice,
            averageMonthlyCount,
            mostFrequentStore
        );

        relatedProduct.setStatistics(statisticsRepository.save(statistics));

        productRepository.save(relatedProduct);

        return statistics;
    }

    public List<ResStore> getVendors() {
        List<Store> stores = vendorRepository.findAll();
        return stores.stream()
            .map(s -> new ResStore(s.getName(), s.getLogoURL()))
            .toList();
    }
}
