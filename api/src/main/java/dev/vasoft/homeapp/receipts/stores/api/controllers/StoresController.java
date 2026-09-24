package dev.vasoft.homeapp.receipts.stores.api.controllers;

import dev.vasoft.homeapp.receipts.stores.api.response.ResStore;
import dev.vasoft.homeapp.receipts.stores.services.StoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stores")
public class StoresController {
	private final Logger logger;
	private final StoreService storeServiceLogic;

	@Autowired
	public StoresController(StoreService storeServiceLogic) {
		this.logger = LoggerFactory.getLogger(StoresController.class);
		this.storeServiceLogic = storeServiceLogic;
	}

	@GetMapping("")
	public List<ResStore> getStores() {
		return storeServiceLogic.getAll();
	}
}