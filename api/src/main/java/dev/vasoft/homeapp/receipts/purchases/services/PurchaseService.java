package dev.vasoft.homeapp.receipts.purchases.services;

import dev.vasoft.homeapp.receipts.products.services.ProductService;
import dev.vasoft.homeapp.receipts.purchases.api.request.ReqPurchase;
import dev.vasoft.homeapp.receipts.common.api.response.ResPage;
import dev.vasoft.homeapp.receipts.purchases.api.response.ResPurchase;
import dev.vasoft.homeapp.receipts.products.model.entities.Product;
import dev.vasoft.homeapp.receipts.purchases.model.entities.Currency;
import dev.vasoft.homeapp.receipts.purchases.model.entities.Purchase;
import dev.vasoft.homeapp.receipts.scanning.services.NormalizationService;
import dev.vasoft.homeapp.receipts.stores.model.entities.Store;
import dev.vasoft.homeapp.receipts.common.model.repositories.CustomRepository;
import dev.vasoft.homeapp.receipts.common.model.repositories.ProductRepository;
import dev.vasoft.homeapp.receipts.common.model.repositories.PurchasesRepository;
import dev.vasoft.homeapp.receipts.common.model.repositories.StoresRepository;
import dev.vasoft.homeapp.receipts.stores.services.StoreService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class PurchaseService {

    private static final int DEFAULT_PAGE_NUMBER = 0;

    private final Logger logger;
    private final PurchasesRepository purchasesRepository;
    private final CustomRepository customRepository;
    private final ProductRepository productRepository;
    private final StoreService storeService;
    private final ProductService productService;
    private final StoresRepository storesRepository;

    @Autowired
    public PurchaseService(PurchasesRepository purchasesRepository,
        CustomRepository customRepository, ProductRepository productRepository,
        StoreService storeService, ProductService productService, StoresRepository storesRepository) {
        this.storeService = storeService;
        this.productService = productService;
        this.logger = LoggerFactory.getLogger(PurchaseService.class);
        this.purchasesRepository = purchasesRepository;
        this.customRepository = customRepository;
        this.productRepository = productRepository;
        this.storesRepository = storesRepository;
    }

    public ResPage<ResPurchase> getPurchasesPage(ObjectId userId, int pageNumber, int pageSize,
        String searchQuery) {
        if (pageSize == DEFAULT_PAGE_NUMBER) {
            pageSize = 10;
        }

        Pageable paging = PageRequest.of(pageNumber, pageSize, Sort.by("date").descending());

        Page<Purchase> purchasesPage;

        if (searchQuery == null || searchQuery.isEmpty()) {
            purchasesPage = purchasesRepository.findAllByUserId(userId, paging);
        } else {
            purchasesPage = customRepository.findBySearchQuery(userId, searchQuery, paging);
        }

        List<ResPurchase> resPurchases = purchasesPage.stream().map(PurchaseMapper::toResPurchase)
            .toList();

        return new ResPage<>(resPurchases, pageNumber, purchasesPage.getTotalPages());
    }


    /**
     * Same rules as {@link #registerPurchases}: a submitted id wins, a name is the
     * fallback. This used to resolve by name alone, so a request carrying only
     * ids created a product and a store with no name.
     */
    public ResPurchase registerPurchase(ObjectId userId, ReqPurchase reqPurchase) {
        return registerPurchases(userId, List.of(reqPurchase)).getFirst();
    }

    /** New purchases are EUR unless the client says otherwise; see SPEC §9.5. */
    private static Currency currencyOf(ReqPurchase reqPurchase) {
        return reqPurchase.currency() == null ? Currency.EUR : reqPurchase.currency();
    }

    private Store resolveStore(String storeName) {
        Optional<Store> existingStore = storesRepository.findFirstByCanonicalName(storeName);
        if (existingStore.isPresent()) {
            return existingStore.get();
        } else {
            Store newStore = new Store(storeName);
            newStore.setNormalizedCanonicalName(NormalizationService.normalizeStoreName(storeName));
            return storesRepository.save(newStore);
        }
    }

    private Product resolveProduct(String productName) {
        Optional<Product> existingProduct = productRepository.findFirstByCanonicalName(productName);
        if (existingProduct.isPresent()) {
            return existingProduct.get();
        } else {
            Product newProduct = new Product(productName);
            newProduct.setNormalizedCanonicalName(NormalizationService.normalizeProductName(productName));
            return productRepository.save(newProduct);
        }
    }

    public List<ResPurchase> registerPurchases(ObjectId userId, List<ReqPurchase> reqPurchases) {
        List<ResPurchase> results = new ArrayList<>();

        for (ReqPurchase reqPurchase : reqPurchases) {
            Store store = resolveSubmittedStore(reqPurchase);
            Product product = resolveSubmittedProduct(reqPurchase);

            Purchase purchase = new Purchase(userId, product, reqPurchase.price(),
                currencyOf(reqPurchase), reqPurchase.date(), store, reqPurchase.discountAmount());

            purchase = purchasesRepository.save(purchase);
            results.add(PurchaseMapper.toResPurchase(purchase));
        }

        return results;
    }

    private Store resolveSubmittedStore(ReqPurchase reqPurchase) {
        if (StringUtils.isNotEmpty(reqPurchase.storeId())) {
            if (!ObjectId.isValid(reqPurchase.storeId())) {
                throw new RuntimeException("Invalid ID for store");
            }
            return new Store(new ObjectId(reqPurchase.storeId()));
        } else if (StringUtils.isNotEmpty(reqPurchase.storeName())) {
            return resolveStore(reqPurchase.storeName());
        } else {
            throw new RuntimeException("Missing store for purchase");
        }
    }

    private Product resolveSubmittedProduct(ReqPurchase reqPurchase) {
        if (StringUtils.isNotEmpty(reqPurchase.productId())) {
            if (!ObjectId.isValid(reqPurchase.productId())) {
                throw new RuntimeException("Invalid ID for product");
            }
            return new Product(new ObjectId(reqPurchase.productId()));
        } else if (StringUtils.isNotEmpty(reqPurchase.productName())) {
            return resolveProduct(reqPurchase.productName());
        } else {
            throw new RuntimeException("Missing product for purchase");
        }
    }
}