package dev.vasoft.homeapp.receipts.barcodes.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.client.result.UpdateResult;
import dev.vasoft.homeapp.receipts.products.model.entities.Product;
import java.util.List;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * The paths a real database rarely takes on its own: losing a race to the
 * unique index, and input refused before any query. The rest is covered against
 * MongoDB in {@code BarcodeMongoIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class BarcodeServiceTest {

    private static final String EAN = "3800178226137";

    @Mock
    private MongoTemplate mongoTemplate;

    private BarcodeService service;
    private final ObjectId milkId = new ObjectId();

    @BeforeEach
    void setUp() {
        service = new BarcodeService(mongoTemplate);
    }

    private static Product product(ObjectId id, String name, List<String> barcodes) {
        Product p = new Product(id);
        p.setCanonicalName(name);
        p.setBarcodes(barcodes);
        return p;
    }

    @Test
    void losingTheRaceToAnotherProductIsAConflict() {
        ObjectId breadId = new ObjectId();
        when(mongoTemplate.findOne(any(Query.class), eq(Product.class)))
            .thenReturn(null)                                           // before the write
            .thenReturn(product(breadId, "Хляб", List.of(EAN)));        // after it failed
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Product.class)))
            .thenThrow(new DuplicateKeyException("E11000"));

        assertThatThrownBy(() -> service.link(milkId.toHexString(), EAN))
            .isInstanceOfSatisfying(BarcodeAlreadyLinkedException.class,
                e -> assertThat(e.getLinkedProductId()).isEqualTo(breadId));
    }

    @Test
    void losingTheRaceToTheSameLinkIsSuccess() {
        when(mongoTemplate.findOne(any(Query.class), eq(Product.class)))
            .thenReturn(null)
            .thenReturn(product(milkId, "Прясно мляко", List.of(EAN)));
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Product.class)))
            .thenThrow(new DuplicateKeyException("E11000"));
        when(mongoTemplate.findById(milkId, Product.class))
            .thenReturn(product(milkId, "Прясно мляко", List.of(EAN)));

        assertThat(service.link(milkId.toHexString(), EAN).barcodes()).containsExactly(EAN);
    }

    @Test
    void anUnmatchedUpdateMeansTheProductIsMissing() {
        when(mongoTemplate.findOne(any(Query.class), eq(Product.class))).thenReturn(null);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Product.class)))
            .thenReturn(UpdateResult.acknowledged(0, 0L, null));

        assertThatThrownBy(() -> service.link(milkId.toHexString(), EAN))
            .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void anInvalidBarcodeNeverReachesTheDatabase() {
        assertThatThrownBy(() -> service.link(milkId.toHexString(), "3800178226138"))
            .isInstanceOf(InvalidBarcodeException.class);
        assertThatThrownBy(() -> service.findByBarcode("abc"))
            .isInstanceOf(InvalidBarcodeException.class);

        verify(mongoTemplate, never()).findOne(any(Query.class), eq(Product.class));
    }

    @Test
    void aProductWithoutBarcodesAnswersAnEmptyList() {
        assertThat(BarcodeService.toResponse(product(milkId, "Прясно мляко", null)).barcodes())
            .isEmpty();
    }
}
