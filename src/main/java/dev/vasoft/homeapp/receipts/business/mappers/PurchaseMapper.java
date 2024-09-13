package dev.vasoft.homeapp.receipts.business.mappers;

import dev.vasoft.homeapp.receipts.Formatter;
import dev.vasoft.homeapp.receipts.api.response.ResPurchase;
import dev.vasoft.homeapp.receipts.model.entities.Purchase;

public class PurchaseMapper {
	private PurchaseMapper() {
	}

	public static ResPurchase toResPurchase(Purchase p) {
		return new ResPurchase(p.getId().toHexString(),
							   ProductMapper.toResProduct(p.getProduct()),
							   Formatter.formatPrice(p.getPrice()),
							   Formatter.formatDate(p.getDate()),
							   StoreMapper.toResStore(p.getStore()),
							   p.getIsDiscounted());
	}
}
