package dev.vasoft.homeapp.receipts.purchases.model.entities;

import dev.vasoft.homeapp.receipts.stores.model.entities.Store;
import dev.vasoft.homeapp.receipts.products.model.entities.Product;
import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

import java.time.LocalDate;

@Data
@Document(collection = "purchases")
public class Purchase {
	@Id
	private ObjectId id;
	private ObjectId userId;
	@DocumentReference
	private Product product;
	private Double price;
	private Currency currency;
	private LocalDate date;
	@DocumentReference
	private Store store;
	private Double discountAmount;
	/**
	 * How much was bought, in {@link #quantityUnit}. {@link #price} is the line
	 * total, so the unit price is {@code price / quantity} (SPEC F2.0). Null only
	 * on a legacy row the quantity migration has not reached yet.
	 */
	private Double quantity;
	private QuantityUnit quantityUnit;
	/**
	 * True when the quantity was not recorded but assumed by
	 * {@code LegacyQuantityMigration}: one of the product's unit, so that the
	 * stored price stays the unit price. False on everything recorded since.
	 */
	private Boolean quantityAssumed;

	public Purchase(ObjectId userId, Product product, Double price, Currency currency,
		LocalDate date, Store store, Double discountAmount, Double quantity,
		QuantityUnit quantityUnit) {
		this.userId = userId;
		this.product = product;
		this.price = price;
		this.currency = currency;
		this.date = date;
		this.store = store;
		this.discountAmount = discountAmount;
		this.quantity = quantity;
		this.quantityUnit = quantityUnit;
		this.quantityAssumed = false;
	}
}