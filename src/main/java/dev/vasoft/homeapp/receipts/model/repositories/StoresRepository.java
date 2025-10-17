package dev.vasoft.homeapp.receipts.model.repositories;

import dev.vasoft.homeapp.receipts.model.entities.Store;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface StoresRepository extends MongoRepository<Store, String> {
	Optional<Store> findByName(String name);
}