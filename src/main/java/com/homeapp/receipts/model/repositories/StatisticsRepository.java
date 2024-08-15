package com.homeapp.receipts.model.repositories;

import com.homeapp.receipts.model.entities.Statistics;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface StatisticsRepository extends MongoRepository<Statistics, String> {
}
