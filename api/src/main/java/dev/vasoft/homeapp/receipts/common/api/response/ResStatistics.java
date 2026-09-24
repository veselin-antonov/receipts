package dev.vasoft.homeapp.receipts.common.api.response;

import dev.vasoft.homeapp.receipts.stores.api.response.ResStore;
import java.util.List;

public record ResStatistics(
		List<Common> commons,
		Store usualStore
) {
	public record Common(
			String value,
			String label
	) {
	}

	public record Store(
			ResStore store,
			String label
	) {
	}
}