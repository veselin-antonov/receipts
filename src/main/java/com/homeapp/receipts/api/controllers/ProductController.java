package com.homeapp.receipts.api.controllers;

import com.homeapp.receipts.api.response.ResProduct;
import com.homeapp.receipts.api.response.ResProductDetails;
import com.homeapp.receipts.business.Products;
import com.homeapp.receipts.model.entities.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ProductController {
    Products productsLogic;
    private final Logger logger;

    @Autowired
    public ProductController(Products productsLogic) {
        this.logger = LoggerFactory.getLogger(ProductController.class);
        this.productsLogic = productsLogic;
    }

    @CrossOrigin(origins = "http://localhost:5173")
    @GetMapping("/products")
    public ResponseEntity<List<ResProduct>> root() {
        return new ResponseEntity<>(productsLogic.getAllProducts(), HttpStatus.OK);
    }

    @CrossOrigin(origins = "http://localhost:5173")
    @GetMapping("/{productId}/details")
    public ResponseEntity<ResProductDetails> purchases(@PathVariable String productId) {
        ResProductDetails productDetails = productsLogic.getDetailsById(productId);

        return productDetails != null ? ResponseEntity.ok(productDetails) : new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}

