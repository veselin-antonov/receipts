package dev.vasoft.homeapp.receipts.common.model.repositories;

import dev.vasoft.homeapp.receipts.products.model.entities.Product;

import java.util.List;

public interface CustomProductsRepository {
	List<Product> getProductsForDisplay();
}