package com.homeapp.receipts.api.response;

import java.util.List;

public record ResPage<T>(
		List<T> contents,
		int pageId,
		int totalPages
) {
}
