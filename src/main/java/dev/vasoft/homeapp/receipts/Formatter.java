package dev.vasoft.homeapp.receipts;

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

	public static String formatDate(LocalDate date) {
		return DateTimeFormatter.ofPattern("dd.MM.yy г.").format(date);
	}
}
