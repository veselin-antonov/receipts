package com.homeapp.receipts.model.repositories;

import com.homeapp.receipts.model.entities.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class CustomProductsRepositoryImpl implements CustomProductsRepository {

	private final MongoTemplate mongoTemplate;

	@Autowired
	public CustomProductsRepositoryImpl(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public List<Product> getProductsForDisplay() {
		Query query = new Query();
		query.fields().include("_id", "name", "iconID");
		return mongoTemplate.find(query, Product.class);
	}
}
