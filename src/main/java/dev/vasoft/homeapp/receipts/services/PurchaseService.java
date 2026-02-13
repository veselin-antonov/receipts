package dev.vasoft.homeapp.receipts.services;

import dev.vasoft.homeapp.receipts.api.request.ReqPurchase;
import dev.vasoft.homeapp.receipts.api.response.ResPage;
import dev.vasoft.homeapp.receipts.api.response.ResPurchase;
import dev.vasoft.homeapp.receipts.model.entities.Product;
import dev.vasoft.homeapp.receipts.model.entities.Purchase;
import dev.vasoft.homeapp.receipts.model.entities.Store;
import dev.vasoft.homeapp.receipts.model.repositories.CustomRepository;
import dev.vasoft.homeapp.receipts.model.repositories.ProductRepository;
import dev.vasoft.homeapp.receipts.model.repositories.PurchasesRepository;
import dev.vasoft.homeapp.receipts.model.repositories.StoresRepository;
import dev.vasoft.homeapp.receipts.services.mappers.PurchaseMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    private final StoresRepository storesRepository;

    @Autowired
    public PurchaseService(PurchasesRepository purchasesRepository,
        CustomRepository customRepository,
        ProductRepository productRepository,
        StoresRepository storesRepository) {
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

        Pageable paging = PageRequest.of(pageNumber, pageSize,
            Sort.by("date").descending());

        Page<Purchase> purchasesPage;

        if (searchQuery == null || searchQuery.isEmpty()) {
            purchasesPage = purchasesRepository.findAllByUserId(userId, paging);
        } else {
            purchasesPage = customRepository.findBySearchQuery(userId, searchQuery,
                paging);
        }

        List<ResPurchase> resPurchases = purchasesPage.stream()
            .map(PurchaseMapper::toResPurchase)
            .toList();

        return new ResPage<>(resPurchases, pageNumber,
            purchasesPage.getTotalPages());
    }


    public ResPurchase registerPurchase(ObjectId userId, ReqPurchase reqPurchase) {
        Store store = resolveStore(reqPurchase.store());
        Product product = resolveProduct(reqPurchase.product());

        Purchase purchase = new Purchase(userId, product, reqPurchase.price(),
            reqPurchase.date(), store,
            reqPurchase.discount());

        purchase = purchasesRepository.save(purchase);

        return PurchaseMapper.toResPurchase(purchase);
    }

    public List<ResPurchase> registerPurchases(ObjectId userId, List<ReqPurchase> reqPurchases) {
        List<ResPurchase> results = new ArrayList<>();

        for (ReqPurchase reqPurchase : reqPurchases) {
            Store store = resolveStore(reqPurchase.store());
            Product product = resolveProduct(reqPurchase.product());

            Purchase purchase = new Purchase(userId, product, reqPurchase.price(),
                reqPurchase.date(), store,
                reqPurchase.discount());

            purchase = purchasesRepository.save(purchase);
            results.add(PurchaseMapper.toResPurchase(purchase));
        }

        return results;
    }

    private Store resolveStore(String storeName) {
        Optional<Store> existingStore = storesRepository.findByName(storeName);
        if (existingStore.isPresent()) {
            return existingStore.get();
        } else {
            Store newStore = new Store(storeName);
            return storesRepository.save(newStore);
        }
    }

    private Product resolveProduct(String productName) {
        Optional<Product> existingProduct = productRepository.findByName(productName);
        if (existingProduct.isPresent()) {
            return existingProduct.get();
        } else {
            Product newProduct = new Product(productName);
            return productRepository.save(newProduct);
        }
    }
}