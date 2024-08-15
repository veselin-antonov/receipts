package com.homeapp.receipts.model.repositories;

import com.homeapp.receipts.model.entities.Product;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends MongoRepository<Product, ObjectId>, CustomProductsRepository {
	Optional<Product> findByName(String name);
}