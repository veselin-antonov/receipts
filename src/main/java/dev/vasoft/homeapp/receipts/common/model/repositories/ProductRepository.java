package dev.vasoft.homeapp.receipts.common.model.repositories;

import dev.vasoft.homeapp.receipts.products.model.entities.Product;
import java.util.List;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends MongoRepository<Product, ObjectId>, CustomProductsRepository {
    List<Product> findByNormalizedCanonicalName(String normalizedCanonicalName);

    Optional<Product> findFirstByCanonicalName(String canonicalName);
}
