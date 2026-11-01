package dev.vasoft.homeapp.receipts.common.model.repositories;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.lookup;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.sort;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.unwind;

import dev.vasoft.homeapp.receipts.products.model.ProductPurchaseSummary;
import dev.vasoft.homeapp.receipts.products.model.entities.Product;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
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
		query.fields().include("_id", "canonicalName", "iconID");
		return mongoTemplate.find(query, Product.class);
	}

	@Override
	public List<Product> getProductsForSearch() {
		Query query = new Query();
		query.fields().include("_id", "canonicalName", "aliases", "iconID");
		return mongoTemplate.find(query, Product.class);
	}

	@Override
	public List<ProductPurchaseSummary> getPurchaseSummaries(ObjectId userId) {
		Aggregation aggregation = newAggregation(
				match(Criteria.where("userId").is(userId).and("product").ne(null)),
				// Newest first, so that $first in the group is the last purchase
				sort(Sort.by(Sort.Direction.DESC, "date", "_id")),
				group("product")
						.count().as("purchaseCount")
						.first("date").as("lastDate")
						.first("price").as("lastPrice")
						.first("currency").as("lastCurrency")
						.first("store").as("lastStore"),
				lookup("stores", "lastStore", "_id", "lastStore"),
				unwind("lastStore", true));
		return mongoTemplate.aggregate(aggregation, "purchases", ProductPurchaseSummary.class)
				.getMappedResults();
	}
}
