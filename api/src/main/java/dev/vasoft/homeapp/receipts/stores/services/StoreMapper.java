package dev.vasoft.homeapp.receipts.stores.services;

import dev.vasoft.homeapp.receipts.scanning.api.response.ResScanStore;
import dev.vasoft.homeapp.receipts.stores.api.response.ResStore;
import dev.vasoft.homeapp.receipts.stores.model.entities.Store;

public class StoreMapper {

    private StoreMapper() {
    }

    public static ResStore toResStore(Store store) {
        if (store == null) {
            return null;
        }
        return new ResStore(store.getId().toHexString(), store.getCanonicalName(),
            store.getIconID());
    }

    public static ResScanStore toResScanStore(Store matchedStore, String rawStoreName) {
        if (matchedStore != null) {
            return new ResScanStore(toResStore(matchedStore), rawStoreName);
        } else {
            return new ResScanStore(null, rawStoreName);
        }
    }
}