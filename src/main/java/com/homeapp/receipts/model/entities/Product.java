package com.homeapp.receipts.model.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "products")
public class Product {
	@Id
	private ObjectId id;
	private String name;
	private String iconID;
	@DocumentReference
	private Statistics statistics;

	public Product(String name) {
		this.name = name;
	}
}
