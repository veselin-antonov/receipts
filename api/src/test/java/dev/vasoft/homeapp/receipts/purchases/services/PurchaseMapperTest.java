package dev.vasoft.homeapp.receipts.purchases.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.vasoft.homeapp.receipts.products.model.entities.Product;
import dev.vasoft.homeapp.receipts.purchases.api.response.ResPurchase;
import dev.vasoft.homeapp.receipts.purchases.model.entities.Currency;
import dev.vasoft.homeapp.receipts.purchases.model.entities.Purchase;
import dev.vasoft.homeapp.receipts.purchases.model.entities.QuantityUnit;
import dev.vasoft.homeapp.receipts.stores.model.entities.Store;
import java.time.LocalDate;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

class PurchaseMapperTest {

    private static Purchase purchase(double price, Currency currency, Double discount) {
        Purchase p = new Purchase(new ObjectId(), new Product(new ObjectId()), price, currency,
            LocalDate.of(2025, 10, 3), new Store(new ObjectId()), discount, 2.0, QuantityUnit.PIECE);
        p.setId(new ObjectId());
        return p;
    }

    @Test
    void legacyBgnPurchaseIsServedInEur() {
        ResPurchase res = PurchaseMapper.toResPurchase(purchase(12.65, Currency.BGN, 1.95583));

        assertThat(res.priceEur()).isEqualTo(12.65 / 1.95583);
        assertThat(res.discountAmountEur()).isEqualTo(1.0);
    }

    @Test
    void eurPurchaseIsServedUnchanged() {
        ResPurchase res = PurchaseMapper.toResPurchase(purchase(3.29, Currency.EUR, 0.5));

        assertThat(res.priceEur()).isEqualTo(3.29);
        assertThat(res.discountAmountEur()).isEqualTo(0.5);
    }

    @Test
    void missingDiscountIsZero() {
        assertThat(PurchaseMapper.toResPurchase(purchase(1.0, Currency.EUR, null))
            .discountAmountEur()).isZero();
    }

    @Test
    void aPurchaseWithoutCurrencyIsRefusedRatherThanGuessed() {
        assertThatThrownBy(() -> PurchaseMapper.toResPurchase(purchase(12.65, null, 0.0)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no currency");
    }

    @Test
    void quantityAndUnitAreServedAsRecorded() {
        ResPurchase res = PurchaseMapper.toResPurchase(purchase(2.90, Currency.EUR, 0.0));

        assertThat(res.quantity()).isEqualTo(2.0);
        assertThat(res.quantityUnit()).isEqualTo(QuantityUnit.PIECE);
        assertThat(res.quantityAssumed()).isFalse();
    }

    @Test
    void aMigratedLegacyPurchaseIsMarkedAssumed() {
        Purchase p = purchase(4.99, Currency.BGN, 0.0);
        p.setQuantity(1.0);
        p.setQuantityUnit(QuantityUnit.KILOGRAM);
        p.setQuantityAssumed(true);

        ResPurchase res = PurchaseMapper.toResPurchase(p);

        assertThat(res.quantityUnit()).isEqualTo(QuantityUnit.KILOGRAM);
        assertThat(res.quantityAssumed()).isTrue();
    }

    @Test
    void aLegacyPurchaseTheMigrationHasNotReachedHasNoQuantity() {
        Purchase p = purchase(4.99, Currency.BGN, 0.0);
        p.setQuantity(null);
        p.setQuantityUnit(null);
        p.setQuantityAssumed(null);

        ResPurchase res = PurchaseMapper.toResPurchase(p);

        assertThat(res.quantity()).isNull();
        assertThat(res.quantityUnit()).isNull();
        assertThat(res.quantityAssumed()).isFalse();
    }
}
