package dev.vasoft.homeapp.receipts.business.mappers;

import dev.vasoft.homeapp.receipts.api.response.ResProduct;
import dev.vasoft.homeapp.receipts.model.entities.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {
	private ProductMapper() {
	}

	public static ResProduct toResProduct(Product product) {
		if (product == null) {
			return null;
		}
		return new ResProduct(product.getId().toHexString(), product.getName(),
							  product.getIconID());
	}
}
