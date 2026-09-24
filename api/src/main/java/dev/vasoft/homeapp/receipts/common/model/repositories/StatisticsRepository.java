package dev.vasoft.homeapp.receipts.common.model.repositories;

import dev.vasoft.homeapp.receipts.common.model.entities.Statistics;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface StatisticsRepository extends MongoRepository<Statistics, String> {
}