package dev.vasoft.homeapp.receipts.business.mappers;

import dev.vasoft.homeapp.receipts.api.response.ResStore;
import dev.vasoft.homeapp.receipts.model.entities.Store;

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
