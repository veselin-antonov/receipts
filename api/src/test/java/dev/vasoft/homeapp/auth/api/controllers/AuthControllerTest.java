package dev.vasoft.homeapp.auth.api.controllers;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.vasoft.homeapp.auth.config.SecurityConfiguration;
import dev.vasoft.homeapp.auth.config.filters.JwtCookieFilter;
import dev.vasoft.homeapp.auth.services.TokenService;
import dev.vasoft.homeapp.receipts.common.config.WebConfig;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code POST /api/auth/token} must say "unverified" in the body, not only in
 * the status, because a 403 also comes from things that have nothing to do with
 * the account, such as a CORS rejection (D15).
 */
@WebMvcTest(AuthController.class)
@AutoConfigureCache
@Import({SecurityConfiguration.class, WebConfig.class, AuthControllerTest.Users.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "app.cors.allowed-origins=https://localhost:5173")
class AuthControllerTest {

    private static final String EMAIL = "vesko@example.com";
    private static final String PASSWORD = "correct-password";

    @TestConfiguration
    static class Users {
        @Bean
        PasswordEncoder passwordEncoder() {
            return PasswordEncoderFactories.createDelegatingPasswordEncoder();
        }

        @Bean
        UserDetailsService userDetailsService() {
            return new InMemoryUserDetailsManager(
                User.withUsername(EMAIL).password("{noop}" + PASSWORD).build());
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static String basic(String email, String password) {
        return "Basic " + Base64.getEncoder()
            .encodeToString((email + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private static Jwt token(boolean limited) {
        Instant now = Instant.now();
        Jwt.Builder builder = Jwt.withTokenValue("token-value")
            .header("alg", "none")
            .subject(EMAIL)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(3600));
        if (limited) {
            builder.claim(TokenService.CLAIM_TOKEN_TYPE, TokenService.TOKEN_TYPE_LIMITED);
        }
        return builder.build();
    }

    @Test
    void verifiedAccountGetsTheTokenLifetime() throws Exception {
        when(tokenService.generateToken(any())).thenReturn(token(false));

        mockMvc.perform(post("/api/auth/token")
                .header(HttpHeaders.AUTHORIZATION, basic(EMAIL, PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(JwtCookieFilter.JWT_COOKIE + "=token-value")))
            .andExpect(jsonPath("$").isNumber());
    }

    @Test
    void unverifiedAccountGetsAProblemDetailWithAStableCode() throws Exception {
        when(tokenService.generateToken(any())).thenReturn(token(true));

        mockMvc.perform(post("/api/auth/token")
                .header(HttpHeaders.AUTHORIZATION, basic(EMAIL, PASSWORD)))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.instance").value("/api/auth/token"))
            .andExpect(jsonPath("$.error").value(AuthControllerAdvice.ACCOUNT_NOT_VERIFIED))
            // The limited cookie is what authorises resending the verification e-mail
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(JwtCookieFilter.JWT_COOKIE + "=token-value")));
    }

    @Test
    void aCorsRejectionIsA403WithoutTheUnverifiedCode() throws Exception {
        when(tokenService.generateToken(any())).thenReturn(token(false));

        String body = mockMvc.perform(post("/api/auth/token")
                .header(HttpHeaders.ORIGIN, "http://192.168.1.20:7863")
                .header(HttpHeaders.AUTHORIZATION, basic(EMAIL, PASSWORD)))
            .andExpect(status().isForbidden())
            .andReturn().getResponse().getContentAsString();

        assertThat(body)
            .doesNotContain(AuthControllerAdvice.ACCOUNT_NOT_VERIFIED);
    }

    @Test
    void wrongPasswordIsA401WithoutTheUnverifiedCode() throws Exception {
        String body = mockMvc.perform(post("/api/auth/token")
                .header(HttpHeaders.AUTHORIZATION, basic(EMAIL, "wrong")))
            .andExpect(status().isUnauthorized())
            .andReturn().getResponse().getContentAsString();

        assertThat(body)
            .doesNotContain(AuthControllerAdvice.ACCOUNT_NOT_VERIFIED);
    }
}
