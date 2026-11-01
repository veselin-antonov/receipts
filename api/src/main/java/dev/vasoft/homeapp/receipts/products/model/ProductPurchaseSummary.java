package dev.vasoft.homeapp.receipts.products.model;

import dev.vasoft.homeapp.receipts.purchases.model.entities.Currency;
import dev.vasoft.homeapp.receipts.stores.model.entities.Store;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;

/**
 * One user's purchases of one product, summed up: how often they bought it and
 * what they paid the last time. Computed by aggregation on every read
 * (SPEC §9.3), never stored.
 *
 * <p>{@code lastPrice} is in {@code lastCurrency}, as recorded; converting it is
 * the reader's job (SPEC §9.5).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductPurchaseSummary {
    @Id
    private ObjectId productId;
    private long purchaseCount;
    private LocalDate lastDate;
    private Double lastPrice;
    private Currency lastCurrency;
    private Store lastStore;
}
