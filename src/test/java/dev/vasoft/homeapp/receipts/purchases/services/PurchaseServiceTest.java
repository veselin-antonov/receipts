package dev.vasoft.homeapp.receipts.purchases.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.vasoft.homeapp.receipts.common.model.repositories.CustomRepository;
import dev.vasoft.homeapp.receipts.common.model.repositories.ProductRepository;
import dev.vasoft.homeapp.receipts.common.model.repositories.PurchasesRepository;
import dev.vasoft.homeapp.receipts.common.model.repositories.StoresRepository;
import dev.vasoft.homeapp.receipts.products.model.entities.Product;
import dev.vasoft.homeapp.receipts.products.services.ProductService;
import dev.vasoft.homeapp.receipts.purchases.api.request.ReqPurchase;
import dev.vasoft.homeapp.receipts.purchases.api.response.ResPurchase;
import dev.vasoft.homeapp.receipts.purchases.model.entities.Currency;
import dev.vasoft.homeapp.receipts.purchases.model.entities.Purchase;
import dev.vasoft.homeapp.receipts.scanning.services.NormalizationService;
import dev.vasoft.homeapp.receipts.stores.model.entities.Store;
import dev.vasoft.homeapp.receipts.stores.services.StoreService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PurchaseServiceTest {

    @Mock
    private PurchasesRepository purchasesRepository;

    @Mock
    private CustomRepository customRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StoreService storeService;

    @Mock
    private ProductService productService;

    @Mock
    private StoresRepository storesRepository;

    private PurchaseService purchaseService;

    @BeforeEach
    void setUp() {
        purchaseService = new PurchaseService(purchasesRepository, customRepository, productRepository,
            storeService, productService, storesRepository);
        when(purchasesRepository.save(any(Purchase.class))).thenAnswer(invocation -> {
            Purchase purchase = invocation.getArgument(0);
            purchase.setId(new ObjectId());
            return purchase;
        });
    }

    @Test
    void registerPurchasesUsesSubmittedProductAndStoreIdsWithoutCreatingNewRecords() {
        ObjectId userId = new ObjectId();
        ObjectId productId = new ObjectId();
        ObjectId storeId = new ObjectId();
        ReqPurchase request = new ReqPurchase(productId.toHexString(), null, storeId.toHexString(), null,
            12.30, null, LocalDate.of(2026, 3, 7), 1.0, ReqPurchase.QuantityUnit.PIECE, 0.50);

        List<ResPurchase> result = purchaseService.registerPurchases(userId, List.of(request));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().product().id()).isEqualTo(productId.toHexString());
        assertThat(result.getFirst().store().id()).isEqualTo(storeId.toHexString());

        ArgumentCaptor<Purchase> savedPurchase = ArgumentCaptor.forClass(Purchase.class);
        verify(purchasesRepository).save(savedPurchase.capture());
        assertThat(savedPurchase.getValue().getUserId()).isEqualTo(userId);
        assertThat(savedPurchase.getValue().getProduct().getId()).isEqualTo(productId);
        assertThat(savedPurchase.getValue().getStore().getId()).isEqualTo(storeId);
        assertThat(savedPurchase.getValue().getCurrency()).isEqualTo(Currency.EUR);
        assertThat(savedPurchase.getValue().getPrice()).isEqualTo(12.30);
        verify(productRepository, never()).save(any(Product.class));
        verify(storesRepository, never()).save(any(Store.class));
    }

    @Test
    void registerPurchasesCreatesNormalizedProductAndStoreWhenOnlyNamesAreSubmitted() {
        ObjectId userId = new ObjectId();
        ReqPurchase request = new ReqPurchase(null, "Coca Cola Zero", null, "Billa",
            2.50, null, LocalDate.of(2026, 3, 7), 1.0, ReqPurchase.QuantityUnit.PIECE, 0.0);

        when(productRepository.findFirstByCanonicalName("Coca Cola Zero")).thenReturn(Optional.empty());
        when(storesRepository.findFirstByCanonicalName("Billa")).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            product.setId(new ObjectId());
            return product;
        });
        when(storesRepository.save(any(Store.class))).thenAnswer(invocation -> {
            Store store = invocation.getArgument(0);
            store.setId(new ObjectId());
            return store;
        });

        List<ResPurchase> result = purchaseService.registerPurchases(userId, List.of(request));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().product().name()).isEqualTo("Coca Cola Zero");
        assertThat(result.getFirst().store().name()).isEqualTo("Billa");

        ArgumentCaptor<Product> savedProduct = ArgumentCaptor.forClass(Product.class);
        ArgumentCaptor<Store> savedStore = ArgumentCaptor.forClass(Store.class);
        verify(productRepository).save(savedProduct.capture());
        verify(storesRepository).save(savedStore.capture());
        assertThat(savedProduct.getValue().getNormalizedCanonicalName())
            .isEqualTo(NormalizationService.normalizeProductName("Coca Cola Zero"));
        assertThat(savedStore.getValue().getNormalizedCanonicalName())
            .isEqualTo(NormalizationService.normalizeStoreName("Billa"));
    }

    @Test
    void registerPurchasesStoresAnExplicitCurrencyAndReturnsEur() {
        ReqPurchase request = new ReqPurchase(new ObjectId().toHexString(), null,
            new ObjectId().toHexString(), null, 12.65, Currency.BGN, LocalDate.of(2025, 10, 3),
            1.0, ReqPurchase.QuantityUnit.PIECE, 0.0);

        List<ResPurchase> result = purchaseService.registerPurchases(new ObjectId(), List.of(request));

        ArgumentCaptor<Purchase> savedPurchase = ArgumentCaptor.forClass(Purchase.class);
        verify(purchasesRepository).save(savedPurchase.capture());
        // The recorded figure is never rewritten; conversion happens on the way out.
        assertThat(savedPurchase.getValue().getPrice()).isEqualTo(12.65);
        assertThat(savedPurchase.getValue().getCurrency()).isEqualTo(Currency.BGN);
        assertThat(result.getFirst().priceEur()).isEqualTo(12.65 / 1.95583);
    }

    @Test
    void registerPurchaseUsesSubmittedIdsLikeTheBatchPath() {
        ObjectId productId = new ObjectId();
        ObjectId storeId = new ObjectId();
        ReqPurchase request = new ReqPurchase(productId.toHexString(), null, storeId.toHexString(), null,
            3.29, null, LocalDate.of(2026, 9, 23), 1.0, ReqPurchase.QuantityUnit.PIECE, 0.0);

        ResPurchase result = purchaseService.registerPurchase(new ObjectId(), request);

        assertThat(result.product().id()).isEqualTo(productId.toHexString());
        assertThat(result.store().id()).isEqualTo(storeId.toHexString());
        verify(productRepository, never()).save(any(Product.class));
        verify(storesRepository, never()).save(any(Store.class));
    }
}
