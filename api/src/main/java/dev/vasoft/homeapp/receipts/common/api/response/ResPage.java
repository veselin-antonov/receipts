package dev.vasoft.homeapp.receipts.common.api.response;

import java.util.List;

public record ResPage<T>(
		List<T> contents,
		int pageId,
		int totalPages
) {
}