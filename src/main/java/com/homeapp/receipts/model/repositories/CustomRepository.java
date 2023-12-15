package com.homeapp.receipts.model.repositories;

import com.homeapp.receipts.model.entities.Product;
import com.homeapp.receipts.model.entities.Store;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.logging.Logger;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Repository
public class CustomRepository {
    private static final String PURCHASES_COUNT = "purchasesCount";
    private final MongoTemplate mongoTemplate;

    @Autowired
    public CustomRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Store mostFrequentStore(@Param("productId") String productId, @Param("afterDate") LocalDate afterDate) {
        Aggregation aggregation = newAggregation(
                match(
                        new Criteria("date")
                                .gte(afterDate)
                                .and("product")
                                .is(new ObjectId(productId))
                ),
                group("store").count().as(PURCHASES_COUNT),
                sort(Sort.Direction.DESC, PURCHASES_COUNT),
                limit(1),
                lookup()
                        .from("stores")
                        .localField("_id")
                        .foreignField("_id")
                        .as("store"),
                unwind("store"),
                project()
                        .andExpression("$store.name").as("name")
                        .andExpression("$store.logoURL").as("logoURL")
                        .andExpression("$store._class").as("_class")
        );

        AggregationResults<Store> result = mongoTemplate
                .aggregate(aggregation, "purchases", Store.class);

        Logger.getAnonymousLogger().info(() -> String.valueOf(result.getRawResults()));
        Logger.getAnonymousLogger().info(() -> String.valueOf(result.getUniqueMappedResult()));

        return result.getUniqueMappedResult();
    }

    public List<Product> getProductsForDisplay() {
        Query query = new Query();
        query.fields().include("_id", "name", "iconID");
        return mongoTemplate.find(query, Product.class);
    }
}
