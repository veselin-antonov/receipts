package com.homeapp.receipts.model.entities;

import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDate;

@Data
@Document(collection = "purchases")
public class Purchase {
	@Id
	private ObjectId id;
	@DocumentReference
	private Product product;
	private Double price;
	private LocalDate date;
	@DocumentReference
	private Store store;
	@Field(name = "discount")
	private Boolean isDiscounted;

	public Purchase(Product product, Double price, LocalDate date, Store store,
					Boolean isDiscounted) {
		this.product = product;
		this.price = price;
		this.date = date;
		this.store = store;
		this.isDiscounted = isDiscounted;
	}
}
