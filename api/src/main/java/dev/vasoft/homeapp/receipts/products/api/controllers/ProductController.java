package dev.vasoft.homeapp.receipts.products.api.controllers;

import dev.vasoft.homeapp.auth.services.AuthenticatedUserService;
import dev.vasoft.homeapp.receipts.products.api.response.ResProduct;
import dev.vasoft.homeapp.receipts.products.api.response.ResProductSearchResult;
import dev.vasoft.homeapp.receipts.products.services.ProductService;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {
	private final Logger logger;
	private final ProductService productServiceLogic;
	private final AuthenticatedUserService authenticatedUserService;

	@Autowired
	public ProductController(ProductService productServiceLogic,
			AuthenticatedUserService authenticatedUserService) {
		this.logger = LoggerFactory.getLogger(ProductController.class);
		this.productServiceLogic = productServiceLogic;
		this.authenticatedUserService = authenticatedUserService;
	}

	@GetMapping
	public List<ResProduct> getAllProducts() {
		return productServiceLogic.getAllProducts();
	}

	/**
	 * Products whose name or an alias matches {@code q}, best first, each with
	 * what the authenticated user last paid for it (SPEC F2.1).
	 */
	@GetMapping("/search")
	public List<ResProductSearchResult> search(
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam(defaultValue = "") String q,
			@RequestParam(defaultValue = "0") int limit) {
		ObjectId userId = authenticatedUserService.getUserId(jwt);
		return productServiceLogic.search(userId, q, limit);
	}

	@PostMapping
	public ResProduct registerProduct(@RequestParam String name) {
		return productServiceLogic.registerProduct(name);
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