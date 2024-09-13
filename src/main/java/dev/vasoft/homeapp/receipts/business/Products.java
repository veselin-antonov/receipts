package dev.vasoft.homeapp.receipts.business;

import dev.vasoft.homeapp.receipts.api.controllers.ProductController;
import dev.vasoft.homeapp.receipts.api.response.ResProduct;
import dev.vasoft.homeapp.receipts.business.mappers.ProductMapper;
import dev.vasoft.homeapp.receipts.model.entities.Product;
import dev.vasoft.homeapp.receipts.model.repositories.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class Products {
	private final ProductRepository productRepository;
	private final Logger logger;

	@Autowired
	public Products(ProductRepository productRepository) {
		this.productRepository = productRepository;
		this.logger = LoggerFactory.getLogger(ProductController.class);
	}

	public List<ResProduct> getAllProducts() {
		List<Product> products = productRepository.getProductsForDisplay();
		return products.stream().map(ProductMapper::toResProduct).toList();
	}

	public ResProduct registerProduct(String name) {
		Product newProduct = productRepository.save(new Product(name));
		return new ResProduct(newProduct.getId().toHexString(),
							  newProduct.getName(), newProduct.getIconID());
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

//    private Statistics updateStatistics(Product relatedProduct) {
//        List<Purchase> purchases = purchasesRepository.findAllByProductId(relatedProduct.getId());
//
//        double averagePrice = purchases.stream().mapToDouble(Purchase::getPrice).average().orElse(0);
//
//        double lowestPrice = purchases.stream().mapToDouble(Purchase::getPrice).min().orElse(0);
//
//        int averageMonthlyCount = (int) Math.round(purchases.stream().filter(p -> p.getDate().isAfter(p.getDate().minus(Period.ofYears(1).withDays(1)))).collect(Collectors.groupingBy(purchase -> purchase.getDate().getMonth())).values().stream().mapToInt(List::size).average().orElse(0));
//
//        Store mostFrequentStore = customRepository.mostFrequentStore(relatedProduct.getId(), LocalDate.now().minusYears(1).withDayOfMonth(1));
//
//        Statistics statistics = new Statistics(averagePrice, lowestPrice, averageMonthlyCount, mostFrequentStore);
//
//        relatedProduct.setStatistics(statisticsRepository.save(statistics));
//
//        productRepository.save(relatedProduct);
//
//        return statistics;
//    }
}
