package dev.vasoft.homeapp.receipts.common.model.repositories;

import dev.vasoft.homeapp.receipts.stores.model.entities.Store;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface StoresRepository extends MongoRepository<Store, ObjectId> {

    Optional<Store> findFirstByNormalizedCanonicalName(String normalizedCanonicalName);

    Optional<Store> findFirstByCanonicalName(String canonicalName);
}
