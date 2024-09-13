package dev.vasoft.homeapp.receipts.model.repositories;

import dev.vasoft.homeapp.receipts.model.entities.Product;

import java.util.List;

public interface CustomProductsRepository {
	List<Product> getProductsForDisplay();
}
