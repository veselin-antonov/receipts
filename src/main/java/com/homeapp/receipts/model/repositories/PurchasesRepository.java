package com.homeapp.receipts.model.repositories;

import com.homeapp.receipts.model.entities.Purchase;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PurchasesRepository extends MongoRepository<Purchase, String> {}