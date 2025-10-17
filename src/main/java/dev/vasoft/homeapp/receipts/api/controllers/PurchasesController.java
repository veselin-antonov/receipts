package dev.vasoft.homeapp.receipts.api.controllers;

import dev.vasoft.homeapp.receipts.api.request.ReqPurchase;
import dev.vasoft.homeapp.receipts.api.response.ResPage;
import dev.vasoft.homeapp.receipts.api.response.ResPurchase;
import dev.vasoft.homeapp.receipts.services.PurchaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
			@RequestParam(defaultValue = "0") int pageNumber,
			@RequestParam(defaultValue = "0") int pageSize,
			@RequestParam(defaultValue = "") String searchQuery) {
		return purchaseServiceLogic.getPurchasesPage(pageNumber, pageSize,
													 searchQuery);
	}

	@PostMapping
	public ResPurchase registerPurchases(@RequestBody ReqPurchase reqPurchase) {
		return purchaseServiceLogic.registerPurchase(reqPurchase);
	}
}
