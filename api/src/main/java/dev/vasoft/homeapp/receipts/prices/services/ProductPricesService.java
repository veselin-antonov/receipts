package dev.vasoft.homeapp.receipts.prices.services;

import dev.vasoft.homeapp.receipts.common.model.repositories.ProductRepository;
import dev.vasoft.homeapp.receipts.common.model.repositories.PurchasesRepository;
import dev.vasoft.homeapp.receipts.prices.api.response.ResProductPrices;
import dev.vasoft.homeapp.receipts.products.model.entities.Product;
import dev.vasoft.homeapp.receipts.products.services.ProductMapper;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

/**
 * A product's price history for one user, computed on every request from the
 * purchases themselves (SPEC §9.3): nothing is cached, so a figure can never
 * be stale.
 */
@Service
public class ProductPricesService {

    private final ProductRepository productRepository;
    private final PurchasesRepository purchasesRepository;

    public ProductPricesService(ProductRepository productRepository,
        PurchasesRepository purchasesRepository) {
        this.productRepository = productRepository;
        this.purchasesRepository = purchasesRepository;
    }

    /**
     * Empty when the product does not exist. A product the user never bought
     * is not an error: it gets an empty history.
     */
    public Optional<ResProductPrices> getPrices(ObjectId userId, ObjectId productId) {
        Optional<Product> product = productRepository.findById(productId);
        return product.map(p -> PriceStatistics.compute(ProductMapper.toResProduct(p),
            purchasesRepository.findPriceRows(userId, productId)));
    }
}
