package dev.vasoft.homeapp.receipts.api.controllers;

import dev.vasoft.homeapp.receipts.api.request.ReqPurchase;
import dev.vasoft.homeapp.receipts.api.response.ResPage;
import dev.vasoft.homeapp.receipts.api.response.ResPurchase;
import dev.vasoft.homeapp.receipts.services.PurchaseService;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
	private final Logger logger;
	private final PurchaseService purchaseServiceLogic;

	@Autowired
	public PurchasesController(PurchaseService purchaseServiceLogic) {
		this.logger = LoggerFactory.getLogger(PurchasesController.class);
		this.purchaseServiceLogic = purchaseServiceLogic;
	}

	@GetMapping
	public ResPage<ResPurchase> getAllPurchases(
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam(defaultValue = "0") int pageNumber,
			@RequestParam(defaultValue = "0") int pageSize,
			@RequestParam(defaultValue = "") String searchQuery) {
		ObjectId userId = new ObjectId(jwt.getSubject());
		return purchaseServiceLogic.getPurchasesPage(userId, pageNumber, pageSize,
													 searchQuery);
	}

	@PostMapping
	public ResPurchase registerPurchases(
			@AuthenticationPrincipal Jwt jwt,
			@RequestBody ReqPurchase reqPurchase) {
		ObjectId userId = new ObjectId(jwt.getSubject());
		return purchaseServiceLogic.registerPurchase(userId, reqPurchase);
	}
}