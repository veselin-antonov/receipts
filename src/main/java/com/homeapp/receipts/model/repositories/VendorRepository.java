package com.homeapp.receipts.model.repositories;

import com.homeapp.receipts.model.entities.Store;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface VendorRepository extends MongoRepository<Store, String> {
}
