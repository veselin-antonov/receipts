package dev.vasoft.homeapp.auth.services;

import dev.vasoft.homeapp.users.model.entities.User;
import dev.vasoft.homeapp.users.model.repositories.UserRepository;
import org.bson.types.ObjectId;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class AuthenticatedUserService {

    private final UserRepository userRepository;

    public AuthenticatedUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public ObjectId getUserId(Jwt jwt) {
        if (jwt == null) {
            throw new AuthenticationCredentialsNotFoundException("Missing authenticated JWT");
        }

        String subject = jwt.getSubject();
        if (ObjectId.isValid(subject)) {
            return new ObjectId(subject);
        }

        return userRepository.findByEmail(subject)
            .map(User::getId)
            .orElseThrow(() -> new UsernameNotFoundException("User doesn't exist: " + subject));
    }
}
