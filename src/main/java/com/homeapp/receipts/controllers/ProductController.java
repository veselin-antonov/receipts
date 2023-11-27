package com.homeapp.receipts.controllers;

import com.homeapp.receipts.model.Product;
import com.homeapp.receipts.model.ProductRepository;
import com.homeapp.receipts.model.Vendor;
import com.homeapp.receipts.model.VendorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ProductController {
    private final ProductRepository productRepository;
    private final VendorRepository vendorRepository;
    private final Logger logger;

    @Autowired
    public ProductController(ProductRepository productRepository, VendorRepository vendorRepository) {
        this.productRepository = productRepository;
        this.vendorRepository = vendorRepository;
        this.logger = LoggerFactory.getLogger(ProductController.class);
    }

    @GetMapping("/receipts")
    public ResponseEntity<List<Product>> root() {
        if (productRepository.count() < 1) {
            generateTestData();
        }

        List<Product> products = productRepository.findAll();

        logger.info("Found the following products:");
        products.forEach(p -> logger.info(p.toString()));

        ResponseEntity<List<Product>> response = new ResponseEntity<>(
                products,
                HttpStatus.OK
        );

        logger.info("ProductController returning the following response:");
        logger.info(response.toString());

        return response;
    }

    private void generateTestData() {
        Vendor kaufland = vendorRepository.save(new Vendor("Kaufland", ""));
        Vendor lidl = vendorRepository.save(new Vendor("Lidl", ""));
        Vendor billa = vendorRepository.save(new Vendor("Billa", ""));
        productRepository.save(new Product("Banani", 10.0, kaufland));
        productRepository.save(new Product("Cheese", 18.4, lidl));
        productRepository.save(new Product("Chocolate", 4.57, billa));
    }
}

