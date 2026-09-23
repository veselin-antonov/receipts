package dev.vasoft.homeapp.receipts.purchases.services;

import dev.vasoft.homeapp.receipts.products.services.ProductMapper;
import dev.vasoft.homeapp.receipts.purchases.api.response.ResPurchase;
import dev.vasoft.homeapp.receipts.purchases.model.entities.Currency;
import dev.vasoft.homeapp.receipts.purchases.model.entities.Purchase;
import dev.vasoft.homeapp.receipts.stores.services.StoreMapper;

public class PurchaseMapper {

    private PurchaseMapper() {
    }

    public static ResPurchase toResPurchase(Purchase p) {
        Currency currency = p.getCurrency();
        if (currency == null) {
            // Guessing here is exactly how D11 happened. The startup backfill
            // tags every legacy row, so a null means that did not run.
            throw new IllegalStateException(
                "Purchase " + p.getId() + " has no currency; the legacy BGN backfill has not run");
        }
        double discount = p.getDiscountAmount() == null ? 0.0 : p.getDiscountAmount();
        return new ResPurchase(p.getId().toHexString(),
            ProductMapper.toResProduct(p.getProduct()),
            currency.toEur(p.getPrice()),
            p.getDate(),
            StoreMapper.toResStore(p.getStore()),
            currency.toEur(discount));
    }

}