package com.homeapp.receipts.api.response;

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
