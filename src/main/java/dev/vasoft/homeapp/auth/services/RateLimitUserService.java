package dev.vasoft.homeapp.auth.services;

import dev.vasoft.homeapp.auth.config.AuthenticationConfig;
import dev.vasoft.homeapp.auth.config.AuthenticationConfig.Scope;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/**
 * Service for extracting user identity information for rate limiting purposes.
 * Works with Bucket4j configuration to provide user-based rate limiting.
 *
 * <p>Methods in this service are referenced in application.yaml using SpEL expressions:
 * <ul>
 *   <li>{@code @rateLimitUserService.getUserId()} - Used as cache-key for per-user rate limiting</li>
 *   <li>{@code @rateLimitUserService.isInactiveUser()} - Used in skip-condition/execute-condition</li>
 * </ul>
 */
@Service
@SuppressWarnings("unused") // Methods are referenced in application.yaml via SpEL expressions
public class RateLimitUserService {

    /**
     * Extracts the user ID (subject) from the JWT token in the current security context.
     * Used as cache-key in Bucket4j configuration for per-user rate limiting.
     *
     * @return the user ID (email) from JWT subject, or "anonymous" if not authenticated
     */
    public String getUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return "anonymous";
        }

        // Extract from JWT token
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject(); // This is the email/username
        }

        // Fallback to authentication name
        return authentication.getName();
    }

    /**
     * Checks if the current user has inactive user scope (unverified account).
     * Used in Bucket4j skip-condition or execute-condition.
     *
     * @return true if user has SCOPE_INACTIVE_USER, false otherwise
     */
    public boolean isInactiveUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(Scope.INACTIVE_USER.getPrefixedName()));
    }

    /**
     * Checks if the current user has active user scope (verified account).
     * Can be used for conditional rate limiting logic.
     *
     * @return true if user has SCOPE_ACTIVE_USER, false otherwise
     */
    public boolean isActiveUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(Scope.ACTIVE_USER.getPrefixedName()));
    }
}
