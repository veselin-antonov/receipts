package com.homeapp.receipts.api.controllers;

import com.homeapp.receipts.api.response.ResStore;
import com.homeapp.receipts.business.Stores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class StoresController {
	private final Logger logger;
	private final Stores storesLogic;

	@Autowired
	public StoresController(Stores storesLogic) {
		this.logger = LoggerFactory.getLogger(ProductController.class);
		this.storesLogic = storesLogic;
	}

	@CrossOrigin(origins = "http://localhost:5173")
	@GetMapping("/stores")
	public List<ResStore> stores() {
		return storesLogic.getALl();
	}
}
