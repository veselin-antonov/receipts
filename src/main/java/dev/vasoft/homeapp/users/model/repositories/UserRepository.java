package dev.vasoft.homeapp.users.model.repositories;

import dev.vasoft.homeapp.users.model.entities.User;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, ObjectId> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}