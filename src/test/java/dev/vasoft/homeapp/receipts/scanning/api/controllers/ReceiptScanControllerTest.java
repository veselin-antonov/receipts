package dev.vasoft.homeapp.receipts.scanning.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.vasoft.homeapp.auth.services.AuthenticatedUserService;
import dev.vasoft.homeapp.receipts.purchases.api.request.ReqPurchase;
import dev.vasoft.homeapp.receipts.purchases.api.response.ResPurchase;
import dev.vasoft.homeapp.receipts.scanning.api.request.ReqSubmitPurchases;
import dev.vasoft.homeapp.receipts.scanning.services.ReceiptScanService;
import java.time.LocalDate;
import java.util.List;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class ReceiptScanControllerTest {

    @Mock
    private ReceiptScanService receiptScanService;

    @Mock
    private Jwt jwt;

    @Mock
    private AuthenticatedUserService authenticatedUserService;

    private ReceiptScanController controller;

    @BeforeEach
    void setUp() {
        controller = new ReceiptScanController(receiptScanService, authenticatedUserService);
    }

    @Test
    void submitPurchasesDelegatesAuthenticatedUserIdAndReviewedPurchasesToService() {
        ObjectId userId = new ObjectId();
        ReqPurchase reviewedPurchase = new ReqPurchase(null, "Coca Cola Zero", null, "Billa",
            2.50, null, LocalDate.of(2026, 3, 7), 1.0, ReqPurchase.QuantityUnit.PIECE, 0.0);
        ReqSubmitPurchases request = new ReqSubmitPurchases(List.of(reviewedPurchase));
        List<ResPurchase> expectedResponse = List.of();

        when(authenticatedUserService.getUserId(jwt)).thenReturn(userId);
        when(receiptScanService.submitPurchases(userId, request.purchases())).thenReturn(expectedResponse);

        List<ResPurchase> response = controller.submitPurchases(jwt, request);

        assertThat(response).isSameAs(expectedResponse);
        ArgumentCaptor<ObjectId> submittedUserId = ArgumentCaptor.forClass(ObjectId.class);
        ArgumentCaptor<List<ReqPurchase>> submittedPurchases = ArgumentCaptor.captor();
        verify(receiptScanService).submitPurchases(submittedUserId.capture(), submittedPurchases.capture());
        assertThat(submittedUserId.getValue()).isEqualTo(userId);
        assertThat(submittedPurchases.getValue()).containsExactly(reviewedPurchase);
    }
}
