package dev.vasoft.homeapp.receipts.api.controllers;

import dev.vasoft.homeapp.receipts.api.response.ResProduct;
import dev.vasoft.homeapp.receipts.business.Products;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/products")
public class ProductController {
	private final Logger logger;
	private final Products productsLogic;

	@Autowired
	public ProductController(Products productsLogic) {
		this.logger = LoggerFactory.getLogger(ProductController.class);
		this.productsLogic = productsLogic;
	}

	@GetMapping
	public List<ResProduct> getAllProducts() {
		return productsLogic.getAllProducts();
	}

	@PostMapping
	public ResProduct registerProduct(@RequestParam String name) {
		return productsLogic.registerProduct(name);
	}

// TODO - Reimplement product details page

//    @CrossOrigin(origins = "http://localhost:5173")
//    @GetMapping("/{productId}/details")
//    public ResponseEntity<ResProductDetails> purchases(@PathVariable String productId) {
//        logger.info("""
//                ProductDetails Request {
//                    productId: {}
//                }""", productId);
//
//        ResProductDetails productDetails = productsLogic.getDetailsById(productId);
//        return productDetails != null ? ResponseEntity.ok(productDetails) : new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
//    }

//    @CrossOrigin(origins = "http://localhost:5173")
//    @PostMapping(value = "/{productId}/details")
//    public ResPurchase registerPurchase(@RequestBody ReqPurchase reqPurchase, @PathVariable String productId) {
//        ResPurchase resPurchase = purchas.registerPurchase(reqPurchase, productId);
//        logger.info(resPurchase.toString());
//        return resPurchase;
//    }
}