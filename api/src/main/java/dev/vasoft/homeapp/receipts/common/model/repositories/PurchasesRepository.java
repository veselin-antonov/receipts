package dev.vasoft.homeapp.receipts.common.model.repositories;

import dev.vasoft.homeapp.receipts.purchases.model.entities.Purchase;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PurchasesRepository extends MongoRepository<Purchase, ObjectId> {
	Page<Purchase> findAllByUserId(ObjectId userId, Pageable pageable);

	// TODO Reimplement product details page
	//List<Purchase> findAllByProductId(String productId);
}