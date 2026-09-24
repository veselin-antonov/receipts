package dev.vasoft.homeapp.users.model.repositories;

import dev.vasoft.homeapp.users.model.entities.User;
import dev.vasoft.homeapp.users.model.entities.VerificationToken;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface VerificationTokenRepository extends MongoRepository<VerificationToken, ObjectId> {
    Optional<VerificationToken> findByUser(User user);
}
