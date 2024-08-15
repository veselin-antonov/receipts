package com.homeapp.receipts.business.mappers;

import com.homeapp.receipts.Formatter;
import com.homeapp.receipts.api.response.ResPurchase;
import com.homeapp.receipts.model.entities.Purchase;

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
