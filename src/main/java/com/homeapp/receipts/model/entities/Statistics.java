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
@Document("statistics")
public class Statistics {
	@Id
	private ObjectId id;
	private Double averagePrice;
	private Double lowestPrice;
	private Integer averageMonthlyCount;
	@DocumentReference
	private Store usualStore;

	public Statistics(Double averagePrice, Double lowestPrice,
					  Integer averageMonthlyCount, Store usualStore) {
		this.averagePrice = averagePrice;
		this.lowestPrice = lowestPrice;
		this.averageMonthlyCount = averageMonthlyCount;
		this.usualStore = usualStore;
	}
}
