package dev.vasoft.homeapp.receipts.purchases.api.request;

import static org.assertj.core.api.Assertions.assertThat;

import dev.vasoft.homeapp.receipts.purchases.model.entities.QuantityUnit;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * A purchase without a quantity has no unit price (D1), so it is a 400 rather
 * than a silent default. Both POST /api/purchases and the scan submit validate
 * with these constraints.
 */
class ReqPurchaseValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static ReqPurchase purchase(Double quantity, QuantityUnit unit) {
        return new ReqPurchase(null, "Банани", null, "Lidl", 1.85, null,
            LocalDate.of(2026, 9, 23), quantity, unit, 0.0);
    }

    private static Set<String> invalidFields(ReqPurchase req) {
        return validator.validate(req).stream()
            .map(ConstraintViolation::getPropertyPath)
            .map(Object::toString)
            .collect(Collectors.toSet());
    }

    @Test
    void aQuantityAndUnitAreValid() {
        assertThat(invalidFields(purchase(0.742, QuantityUnit.KILOGRAM))).isEmpty();
    }

    @Test
    void aMissingQuantityOrUnitIsRejected() {
        assertThat(invalidFields(purchase(null, null)))
            .containsExactlyInAnyOrder("quantity", "quantityUnit");
    }

    @Test
    void aZeroOrNegativeQuantityIsRejected() {
        assertThat(invalidFields(purchase(0.0, QuantityUnit.PIECE))).containsExactly("quantity");
        assertThat(invalidFields(purchase(-1.0, QuantityUnit.PIECE))).containsExactly("quantity");
    }
}
