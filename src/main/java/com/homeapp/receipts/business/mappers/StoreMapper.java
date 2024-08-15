package com.homeapp.receipts.business.mappers;

import com.homeapp.receipts.api.response.ResStore;
import com.homeapp.receipts.model.entities.Store;

public class StoreMapper {
	private StoreMapper() {
	}

	public static ResStore toResStore(Store store) {
		if (store == null) {
			return null;
		}
		return new ResStore(store.getId().toHexString(), store.getName(),
							store.getIconID());
	}
}
