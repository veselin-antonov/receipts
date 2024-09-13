package dev.vasoft.homeapp.receipts.api.controllers;

import dev.vasoft.homeapp.receipts.api.request.ReqPurchase;
import dev.vasoft.homeapp.receipts.api.response.ResPage;
import dev.vasoft.homeapp.receipts.api.response.ResPurchase;
import dev.vasoft.homeapp.receipts.business.Purchases;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
@RequestMapping("/purchases")
public class PurchasesController {
	private final Logger logger;
	private final Purchases purchasesLogic;

	@Autowired
	public PurchasesController(Purchases purchasesLogic) {
		this.logger = LoggerFactory.getLogger(ProductController.class);
		this.purchasesLogic = purchasesLogic;
	}

	@GetMapping
	public ResPage<ResPurchase> getAllPurchases(
			@RequestParam(defaultValue = "0") int pageNumber,
			@RequestParam(defaultValue = "0") int pageSize,
			@RequestParam(defaultValue = "") String searchQuery) {
		return purchasesLogic.getPurchasesPage(pageNumber, pageSize,
											   searchQuery);
	}

	@PostMapping
	public ResPurchase registerPurchases(@RequestBody ReqPurchase reqPurchase) {
		return purchasesLogic.registerPurchase(reqPurchase);
	}
}
