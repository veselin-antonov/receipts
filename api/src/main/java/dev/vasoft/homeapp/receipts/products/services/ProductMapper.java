package dev.vasoft.homeapp.receipts.products.services;

import dev.vasoft.homeapp.receipts.products.api.response.ResProduct;
import dev.vasoft.homeapp.receipts.products.model.entities.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {
	private ProductMapper() {
	}

	public static ResProduct toResProduct(Product product) {
		if (product == null) {
			return null;
		}
		return new ResProduct(product.getId().toHexString(), product.getCanonicalName(),
							  product.getIconID());
	}
}