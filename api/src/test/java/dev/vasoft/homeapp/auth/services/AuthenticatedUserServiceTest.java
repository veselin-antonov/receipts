package dev.vasoft.homeapp.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.vasoft.homeapp.users.model.entities.User;
import dev.vasoft.homeapp.users.model.repositories.UserRepository;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class AuthenticatedUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private Jwt jwt;

    @Test
    void getUserIdUsesObjectIdSubjectWhenTokenSubjectIsAlreadyAUserId() {
        ObjectId userId = new ObjectId();
        AuthenticatedUserService service = new AuthenticatedUserService(userRepository);

        when(jwt.getSubject()).thenReturn(userId.toHexString());

        assertThat(service.getUserId(jwt)).isEqualTo(userId);
    }

    @Test
    void getUserIdResolvesLegacyEmailSubjectToUserId() {
        ObjectId userId = new ObjectId();
        User user = new User();
        user.setId(userId);
        user.setEmail("vesko@example.com");
        AuthenticatedUserService service = new AuthenticatedUserService(userRepository);

        when(jwt.getSubject()).thenReturn("vesko@example.com");
        when(userRepository.findByEmail("vesko@example.com")).thenReturn(Optional.of(user));

        assertThat(service.getUserId(jwt)).isEqualTo(userId);
        verify(userRepository).findByEmail("vesko@example.com");
    }

    @Test
    void getUserIdRejectsMissingJwt() {
        AuthenticatedUserService service = new AuthenticatedUserService(userRepository);

        assertThatThrownBy(() -> service.getUserId(null))
            .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
            .hasMessageContaining("Missing authenticated JWT");
    }

    @Test
    void getUserIdRejectsUnknownLegacyEmailSubject() {
        AuthenticatedUserService service = new AuthenticatedUserService(userRepository);

        when(jwt.getSubject()).thenReturn("missing@example.com");
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUserId(jwt))
            .isInstanceOf(UsernameNotFoundException.class)
            .hasMessageContaining("missing@example.com");
    }
}
