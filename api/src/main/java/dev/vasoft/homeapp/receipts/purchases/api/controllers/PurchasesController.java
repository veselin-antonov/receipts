package dev.vasoft.homeapp.receipts.purchases.api.controllers;

import dev.vasoft.homeapp.auth.services.AuthenticatedUserService;
import dev.vasoft.homeapp.receipts.purchases.api.request.ReqPurchase;
import dev.vasoft.homeapp.receipts.common.api.response.ResPage;
import dev.vasoft.homeapp.receipts.purchases.api.response.ResPurchase;
import dev.vasoft.homeapp.receipts.purchases.services.PurchaseService;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/purchases")
public class PurchasesController {
	private final PurchaseService purchaseServiceLogic;
	private final AuthenticatedUserService authenticatedUserService;

	@Autowired
	public PurchasesController(PurchaseService purchaseServiceLogic,
			AuthenticatedUserService authenticatedUserService) {
		this.purchaseServiceLogic = purchaseServiceLogic;
		this.authenticatedUserService = authenticatedUserService;
	}

	@GetMapping
	public ResPage<ResPurchase> getAllPurchases(
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam(defaultValue = "0") int pageNumber,
			@RequestParam(defaultValue = "0") int pageSize,
			@RequestParam(defaultValue = "") String searchQuery) {
		ObjectId userId = authenticatedUserService.getUserId(jwt);
		return purchaseServiceLogic.getPurchasesPage(userId, pageNumber, pageSize,
													 searchQuery);
	}

	@PostMapping
	public ResPurchase registerPurchases(
			@AuthenticationPrincipal Jwt jwt,
			@RequestBody ReqPurchase reqPurchase) {
		ObjectId userId = authenticatedUserService.getUserId(jwt);
		return purchaseServiceLogic.registerPurchase(userId, reqPurchase);
	}
}