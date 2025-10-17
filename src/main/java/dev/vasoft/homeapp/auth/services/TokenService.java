package dev.vasoft.homeapp.auth.services;

import dev.vasoft.homeapp.auth.config.AuthenticationConfig.Scope;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

    public static final String CLAIM_TOKEN_TYPE = "token_type";
    public static final String CLAIM_SCOPE = "scope";

    public static final String TOKEN_TYPE_LIMITED = "limited";

    private final JwtEncoder encoder;

    public TokenService(JwtEncoder encoder) {
        this.encoder = encoder;
    }

    public Jwt generateToken(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        boolean isActive = userDetails.isActive();

        Instant now = Instant.now();
        Instant expiration = isActive
            ? now.plus(1, ChronoUnit.HOURS)
            : now.plus(15, ChronoUnit.MINUTES);
        String scope = isActive
            ? Scope.ACTIVE_USER.getName()
            : Scope.INACTIVE_USER.getName();

        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .issuer("self")
                .issuedAt(now)
                .expiresAt(expiration)
                .subject(authentication.getName())
                .claim(CLAIM_SCOPE, scope);

        // Add token_type claim for inactive users
        if (!isActive) {
            claimsBuilder.claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_LIMITED);
        }

        JwtClaimsSet claims = claimsBuilder.build();
        JwtEncoderParameters encoderParameters = JwtEncoderParameters.from(claims);
        return encoder.encode(encoderParameters);
    }

    public long getExpirationTime(Jwt jwt) {
        Instant jwtExpiration = jwt.getExpiresAt();

        if (jwtExpiration == null) {
            throw new InvalidBearerTokenException("JWT does not have an expiration claim.");
        }

        Instant currentTime = Instant.now();

        return Duration.between(currentTime, jwtExpiration).toMillis();
    }
}
