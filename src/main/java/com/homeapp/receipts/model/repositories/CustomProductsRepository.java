package com.homeapp.receipts.model.repositories;

import com.homeapp.receipts.model.entities.Product;

import java.util.List;

public interface CustomProductsRepository {
	List<Product> getProductsForDisplay();
}
