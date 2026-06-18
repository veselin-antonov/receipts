package dev.vasoft.homeapp.receipts.purchases.services;

import dev.vasoft.homeapp.receipts.common.services.Formatter;
import dev.vasoft.homeapp.receipts.products.services.ProductMapper;
import dev.vasoft.homeapp.receipts.purchases.api.response.ResPurchase;
import dev.vasoft.homeapp.receipts.purchases.model.entities.Purchase;
import dev.vasoft.homeapp.receipts.scanning.api.response.ResParsedPurchase;
import dev.vasoft.homeapp.receipts.scanning.services.ParsedReceipt;
import dev.vasoft.homeapp.receipts.stores.services.StoreMapper;
import java.util.List;

public class PurchaseMapper {

    private PurchaseMapper() {
    }

    public static ResPurchase toResPurchase(Purchase p) {
        return new ResPurchase(p.getId().toHexString(),
            ProductMapper.toResProduct(p.getProduct()),
            Formatter.formatPrice(p.getPrice()),
            Formatter.formatDate(p.getDate()),
            StoreMapper.toResStore(p.getStore()),
            p.getDiscountAmount());
    }

    public static List<ResParsedPurchase> toResParsedPurchases(ParsedReceipt parsedReceipt) {
        return parsedReceipt.items().stream()
            .map(item -> new ResParsedPurchase(
                item.productName(),
                parsedReceipt.storeName(),
                item.price(),
                parsedReceipt.receiptDate(),
                item.quantity(),
                item.quantityUnit().name(),
                item.discountAmount()
            ))
            .toList();
    }
}