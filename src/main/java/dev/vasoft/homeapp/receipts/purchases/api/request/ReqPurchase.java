package dev.vasoft.homeapp.receipts.purchases.api.request;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

public record ReqPurchase(
		String productId,
    String productName,
		String storeId,
		String storeName,
		double price,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_FORMAT)
		LocalDate date,
		double quantity,
		QuantityUnit quantityUnit,
		double discountAmount
) {

	public static final String DATE_FORMAT = "dd/MM/yyyy";

	public enum QuantityUnit {
				PIECE,
				GRAM,
				KILOGRAM,
				MILLILITER,
				LITER;
		}}