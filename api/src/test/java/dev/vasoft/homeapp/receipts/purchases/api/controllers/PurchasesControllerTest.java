package dev.vasoft.homeapp.receipts.purchases.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.vasoft.homeapp.auth.services.AuthenticatedUserService;
import dev.vasoft.homeapp.receipts.common.api.response.ResPage;
import dev.vasoft.homeapp.receipts.purchases.api.response.ResPurchase;
import dev.vasoft.homeapp.receipts.purchases.services.PurchaseService;
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
class PurchasesControllerTest {

    @Mock
    private PurchaseService purchaseService;

    @Mock
    private AuthenticatedUserService authenticatedUserService;

    @Mock
    private Jwt jwt;

    private PurchasesController controller;

    @BeforeEach
    void setUp() {
        controller = new PurchasesController(purchaseService, authenticatedUserService);
    }

    @Test
    void getAllPurchasesResolvesAuthenticatedUserIdBeforeCallingService() {
        ObjectId userId = new ObjectId();
        ResPage<ResPurchase> expectedPage = new ResPage<>(List.of(), 0, 0);

        when(authenticatedUserService.getUserId(jwt)).thenReturn(userId);
        when(purchaseService.getPurchasesPage(userId, 0, 10, "milk")).thenReturn(expectedPage);

        ResPage<ResPurchase> response = controller.getAllPurchases(jwt, 0, 10, "milk");

        assertThat(response).isSameAs(expectedPage);
        verify(authenticatedUserService).getUserId(jwt);
        ArgumentCaptor<ObjectId> submittedUserId = ArgumentCaptor.forClass(ObjectId.class);
        verify(purchaseService).getPurchasesPage(submittedUserId.capture(),
            org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.eq(10),
            org.mockito.ArgumentMatchers.eq("milk"));
        assertThat(submittedUserId.getValue()).isEqualTo(userId);
    }
}
