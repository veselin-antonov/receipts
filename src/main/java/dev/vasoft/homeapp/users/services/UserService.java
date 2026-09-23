package dev.vasoft.homeapp.users.services;

import dev.vasoft.homeapp.auth.services.CustomUserDetails;
import dev.vasoft.homeapp.users.api.request.ReqRegisterUser;
import dev.vasoft.homeapp.users.api.response.ResRegisterUser;
import dev.vasoft.homeapp.users.exceptions.UserAlreadyExistsException;
import dev.vasoft.homeapp.users.model.entities.User;
import dev.vasoft.homeapp.users.model.entities.VerificationToken;
import dev.vasoft.homeapp.users.model.repositories.UserRepository;
import dev.vasoft.homeapp.users.model.repositories.VerificationTokenRepository;
import dev.vasoft.homeapp.users.services.exceptions.InvalidVerificationLinkException;
import dev.vasoft.homeapp.users.services.exceptions.MissingVerificationTokenException;
import dev.vasoft.homeapp.users.services.mappers.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final VerificationTokenRepository verificationTokenRepository;

    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    private final VerificationTokenService verificationTokenService;

    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
        ApplicationEventPublisher eventPublisher,
        VerificationTokenRepository verificationTokenRepository,
        VerificationTokenService verificationTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.verificationTokenRepository = verificationTokenRepository;
        this.verificationTokenService = verificationTokenService;
    }

    public ResRegisterUser registerUser(ReqRegisterUser reqRegisterUser,
        HttpServletRequest request) {
        if (userRepository.findByEmail(reqRegisterUser.email()).isPresent()) {
            throw new UserAlreadyExistsException();
        }

        User newUser = new User(reqRegisterUser.email(),
            passwordEncoder.encode(reqRegisterUser.password()));

        User createdUser = userRepository.save(newUser);

        eventPublisher.publishEvent(
            new UserRegistrationCompleted(createdUser, request.getContextPath()));

        return UserMapper.toRegisterUserResponse(createdUser);
    }

    public void verifyAccount(String userEmail, String token) {
        User user = userRepository.findByEmail(userEmail)
            .orElseThrow(InvalidVerificationLinkException::new);

        VerificationToken verificationToken = verificationTokenRepository.findByUser(user)
            .orElseThrow(MissingVerificationTokenException::new);

        if (!verificationToken.isSameAs(token) || hasTokenExpired(verificationToken)) {
            throw new InvalidVerificationLinkException();
        } else {
            user.setActive(true);
            userRepository.save(user);
        }
    }

    public boolean hasTokenExpired(VerificationToken token) {
        return token.getExpirationDate() == null
            || Instant.now().isAfter(token.getExpirationDate());
    }

    public void resendVerification(Authentication authentication) {
        String username;

        // Handle both JWT and UserDetails authentication
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            username = jwt.getSubject();
        } else if (authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            username = userDetails.getUsername();
        } else {
            throw new IllegalStateException("Unsupported authentication principal type");
        }

        Optional<User> user = userRepository.findByEmail(username);
        if (user.isEmpty()) {
            throw new UsernameNotFoundException("User doesn't exist");
        } else {
            verificationTokenService.sendVerification(user.get());
        }
    }
}