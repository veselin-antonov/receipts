package com.homeapp.receipts.model.repositories;

import com.homeapp.receipts.model.entities.Purchase;
import com.homeapp.receipts.model.entities.Store;
import com.mongodb.BasicDBObject;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.limit;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.sort;

@Repository
public class CustomRepository {
	private static final String PURCHASES_COUNT = "purchasesCount";
	private final MongoTemplate mongoTemplate;

	@Autowired
	public CustomRepository(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	public Page<Purchase> findBySearchQuery(String searchQuery,
											Pageable pageable) {
		List<AggregationOperation> operations = new ArrayList<>();

		// Perform a lookup to join the Product document
		operations.add(Aggregation.lookup("products", "product", "_id",
										  "productDetails"));
		operations.add(
				Aggregation.lookup("stores", "store", "_id", "storeDetails"));

		// Unwind the product details array
		operations.add(Aggregation.unwind("productDetails", true));
		operations.add(Aggregation.unwind("storeDetails", true));

		// Match the search query
		Criteria criteria = new Criteria().orOperator(
				Criteria.where("productDetails.name").regex(searchQuery, "i"),
				Criteria.where("storeDetails.name").regex(searchQuery, "i"));
		operations.add(Aggregation.match(criteria));

		List<AggregationOperation> totalOperations = new ArrayList<>(
				operations);
		totalOperations.add(Aggregation.count().as("total"));

		// Run aggregation for total count
		Aggregation totalAggregation = Aggregation.newAggregation(
				totalOperations);
		AggregationResults<BasicDBObject> totalResults = mongoTemplate.aggregate(
				totalAggregation, "purchases", BasicDBObject.class);

		BasicDBObject result = totalResults.getUniqueMappedResult();
		long total = result != null ? result.getLong("total") : 0;

		// Add pagination to the original operations
		operations.add(
				Aggregation.sort(Sort.by(Sort.Order.desc("purchaseDate"))));
		operations.add(Aggregation.skip(pageable.getOffset()));
		operations.add(Aggregation.limit(pageable.getPageSize()));

		// Perform the aggregation for paginated results
		Aggregation aggregation = Aggregation.newAggregation(operations);
		AggregationResults<Purchase> results = mongoTemplate.aggregate(
				aggregation, "purchases", Purchase.class);

		return new PageImpl<>(results.getMappedResults(), pageable, total);
	}

	public Store mostFrequentStore(@Param("productId") String productId,
								   @Param("afterDate") LocalDate afterDate) {
		Aggregation aggregation = newAggregation(
				match(new Criteria("date").gte(afterDate).and("product")
										  .is(new ObjectId(productId))),
				group("store").count().as(PURCHASES_COUNT),
				sort(Sort.Direction.DESC, PURCHASES_COUNT), limit(1));

		AggregationResults<Store> result = mongoTemplate.aggregate(aggregation,
																   "purchases",
																   Store.class);

		return result.getUniqueMappedResult();
	}
}
