package dev.vasoft.homeapp.receipts.common.model.repositories;

import dev.vasoft.homeapp.receipts.products.model.ProductPurchaseSummary;
import dev.vasoft.homeapp.receipts.products.model.entities.Product;
import org.bson.types.ObjectId;

import java.util.List;

public interface CustomProductsRepository {
	List<Product> getProductsForDisplay();

	/** Every product with the fields search scores on: names, aliases and icon. */
	List<Product> getProductsForSearch();

	/**
	 * One summary per product the user has bought, with the last purchase taken
	 * by date (the newest row first when two share a date).
	 */
	List<ProductPurchaseSummary> getPurchaseSummaries(ObjectId userId);
}
