package com.homeapp.receipts.api.request;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

public record ReqPurchase(
		String product,
		String store,
		double price,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy") LocalDate date,
		boolean discount
) {
}