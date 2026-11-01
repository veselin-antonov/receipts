package dev.vasoft.homeapp.receipts.common.model.repositories;

import dev.vasoft.homeapp.receipts.prices.model.PurchasePriceRow;
import dev.vasoft.homeapp.receipts.purchases.model.entities.Purchase;
import java.util.List;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PurchasesRepository extends MongoRepository<Purchase, ObjectId> {
	Page<Purchase> findAllByUserId(ObjectId userId, Pageable pageable);

	/**
	 * Every purchase of one product by one user, newest first, with its store
	 * joined in. Same-day purchases are ordered by id, so the order is stable
	 * and the most recently saved row counts as the last one.
	 */
	@Aggregation(pipeline = {
		"{ $match: { userId: ?0, product: ?1 } }",
		"{ $sort: { date: -1, _id: -1 } }",
		"{ $lookup: { from: 'stores', localField: 'store', foreignField: '_id', as: 'store' } }",
		"{ $unwind: { path: '$store', preserveNullAndEmptyArrays: true } }",
		"{ $project: { date: 1, price: 1, currency: 1, discountAmount: 1, quantity: 1, quantityUnit: 1, quantityAssumed: 1, store: 1 } }"
	})
	List<PurchasePriceRow> findPriceRows(ObjectId userId, ObjectId productId);
}
