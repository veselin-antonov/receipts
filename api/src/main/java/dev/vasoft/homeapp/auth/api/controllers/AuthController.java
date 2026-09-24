package dev.vasoft.homeapp.auth.api.controllers;

import dev.vasoft.homeapp.auth.config.filters.JwtCookieFilter;
import dev.vasoft.homeapp.auth.services.TokenService;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.server.Cookie.SameSite;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger LOG = LoggerFactory.getLogger(
        AuthController.class);

    private final TokenService tokenService;

    public AuthController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    private static ResponseCookie createCookie(String token, Duration maxAge) {
        return ResponseCookie.from(JwtCookieFilter.JWT_COOKIE)
            .value(token)
            .path("/")
            .maxAge(maxAge)
            .httpOnly(true)
            .secure(true)
            .sameSite(SameSite.STRICT.attributeValue())
            .build();
    }

    @PostMapping("/token")
    public ResponseEntity<Long> generateToken(Authentication authentication,
        HttpServletResponse response) {
        LOG.debug("Token requested for user: '{}'", authentication.getName());

        Jwt token = tokenService.generateToken(authentication);

        Instant expiration = token.getExpiresAt();
        Duration maxAge = Duration.between(Instant.now(), expiration);
        ResponseCookie jwtCookie = createCookie(token.getTokenValue(), maxAge);

        response.addHeader(HttpHeaders.SET_COOKIE, jwtCookie.toString());

        String tokenType = token.getClaim(TokenService.CLAIM_TOKEN_TYPE);
        HttpStatusCode httpStatus =
            TokenService.TOKEN_TYPE_LIMITED.equals(tokenType) ? HttpStatus.FORBIDDEN
                : HttpStatus.OK;

        return ResponseEntity.status(httpStatus).body(jwtCookie.getMaxAge().toMillis());
    }

    @GetMapping("/status")
    public ResponseEntity<Long> authenticationStatus(
        @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .build();
        }

        long expirationTime = tokenService.getExpirationTime(jwt);

        if (expirationTime <= 0) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .build();
        }

        return ResponseEntity.ok(expirationTime);
    }
}