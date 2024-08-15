package com.homeapp.receipts.model.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "stores")
public class Store {
	@Id
	private ObjectId id;
	@Field
	private String name;
	@Field
	private String iconID;

	public Store(String name) {
		this.name = name;
	}
}
