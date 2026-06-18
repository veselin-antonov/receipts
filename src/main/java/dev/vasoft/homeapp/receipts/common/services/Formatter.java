package dev.vasoft.homeapp.receipts.common.services;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class Formatter {
	private Formatter() {
	}

	public static String formatPrice(double price) {
		Locale bgLocale = new Locale.Builder().setRegion("BG").setLanguage("bg")
											  .build();
		return NumberFormat.getCurrencyInstance(bgLocale).format(price);
	}

	public static DateTimeFormatter getDateTimeFormatter() {
		return DateTimeFormatter.ofPattern("dd.MM.yy г.");
	}

	public static String formatDate(LocalDate date) {
		return getDateTimeFormatter().format(date);
	}
}