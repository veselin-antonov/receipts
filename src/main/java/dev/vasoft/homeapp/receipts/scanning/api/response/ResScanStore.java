package dev.vasoft.homeapp.receipts.scanning.api.response;

import dev.vasoft.homeapp.receipts.stores.api.response.ResStore;

public record ResScanStore(ResStore storeSuggestion, String rawStoreName) {

}