package dev.vasoft.homeapp.receipts.barcodes.api.controllers;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.vasoft.homeapp.receipts.barcodes.api.response.ResBarcodeProduct;
import dev.vasoft.homeapp.receipts.barcodes.services.BarcodeAlreadyLinkedException;
import dev.vasoft.homeapp.receipts.barcodes.services.BarcodeNotLinkedException;
import dev.vasoft.homeapp.receipts.barcodes.services.BarcodeService;
import dev.vasoft.homeapp.receipts.barcodes.services.InvalidBarcodeException;
import dev.vasoft.homeapp.receipts.barcodes.services.ProductNotFoundException;
import java.util.List;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Routes, response shape and the ProblemDetail for each failure. */
@ExtendWith(MockitoExtension.class)
class BarcodeControllerTest {

    private static final String PRODUCT_ID = "6840c5c4bcb1f563dc1d0001";
    private static final String EAN = "3800178226137";

    @Mock
    private BarcodeService barcodeService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new BarcodeController(barcodeService))
            .setControllerAdvice(new BarcodeControllerAdvice())
            .build();
    }

    private static ResBarcodeProduct milk() {
        return new ResBarcodeProduct(PRODUCT_ID, "Прясно мляко", "milk", List.of(EAN));
    }

    @Test
    void findsAProductByBarcode() throws Exception {
        when(barcodeService.findByBarcode(EAN)).thenReturn(milk());

        mvc.perform(get("/api/products/by-barcode/{code}", EAN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(PRODUCT_ID))
            .andExpect(jsonPath("$.name").value("Прясно мляко"))
            .andExpect(jsonPath("$.barcodes[0]").value(EAN));
    }

    @Test
    void anUnknownBarcodeIs404WithItsOwnErrorCode() throws Exception {
        when(barcodeService.findByBarcode(EAN)).thenThrow(new BarcodeNotLinkedException(EAN));

        mvc.perform(get("/api/products/by-barcode/{code}", EAN))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("BARCODE_NOT_LINKED"))
            .andExpect(jsonPath("$.barcode").value(EAN));
    }

    @Test
    void anInvalidBarcodeIs400() throws Exception {
        when(barcodeService.findByBarcode("123"))
            .thenThrow(new InvalidBarcodeException("123", "expected 8, 12, 13 or 14 digits, got 3"));

        mvc.perform(get("/api/products/by-barcode/{code}", "123"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("INVALID_BARCODE"));
    }

    @Test
    void linksWithPut() throws Exception {
        when(barcodeService.link(PRODUCT_ID, EAN)).thenReturn(milk());

        mvc.perform(put("/api/products/{id}/barcodes/{code}", PRODUCT_ID, EAN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.barcodes[0]").value(EAN));
    }

    @Test
    void aBarcodeOnAnotherProductIs409NamingThatProduct() throws Exception {
        ObjectId other = new ObjectId();
        when(barcodeService.link(PRODUCT_ID, EAN))
            .thenThrow(new BarcodeAlreadyLinkedException(EAN, other));

        mvc.perform(put("/api/products/{id}/barcodes/{code}", PRODUCT_ID, EAN))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("BARCODE_ALREADY_LINKED"))
            .andExpect(jsonPath("$.productId").value(other.toHexString()));
    }

    @Test
    void linkingToAMissingProductIs404() throws Exception {
        when(barcodeService.link(PRODUCT_ID, EAN))
            .thenThrow(new ProductNotFoundException(PRODUCT_ID));

        mvc.perform(put("/api/products/{id}/barcodes/{code}", PRODUCT_ID, EAN))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void unlinksWithDelete() throws Exception {
        when(barcodeService.unlink(PRODUCT_ID, EAN)).thenReturn(
            new ResBarcodeProduct(PRODUCT_ID, "Прясно мляко", "milk", List.of()));

        mvc.perform(delete("/api/products/{id}/barcodes/{code}", PRODUCT_ID, EAN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.barcodes").isEmpty());
    }
}
