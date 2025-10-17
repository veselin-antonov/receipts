package dev.vasoft.homeapp.auth.config;

import static org.springframework.security.config.Customizer.withDefaults;

import dev.vasoft.homeapp.auth.config.AuthenticationConfig.Scope;
import dev.vasoft.homeapp.auth.config.filters.JwtCookieFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    private final JwtDecoder jwtDecoder;

    @Autowired
    public SecurityConfiguration(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain tokenSecurityFilterChain(HttpSecurity http) throws Exception {

        return http.securityMatcher("/api/auth/token")
            .sessionManagement(
                session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .httpBasic(withDefaults())
            .build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {

        return http.sessionManagement(
                session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(
                auth -> auth
                    .requestMatchers("/api/users/register", "/api/users/verify")
                    .permitAll()
                    .requestMatchers("/api/users/resend-verification")
                    .hasAuthority(Scope.INACTIVE_USER.getPrefixedName())
                    .requestMatchers("/api/auth/status")
                    .hasAnyAuthority(
                        Scope.ACTIVE_USER.getPrefixedName(),
                        Scope.INACTIVE_USER.getPrefixedName())
                    .anyRequest()
                    .hasAuthority(Scope.ACTIVE_USER.getPrefixedName()))
            .oauth2ResourceServer(oauth -> oauth.jwt(withDefaults()))
            .addFilterBefore(new JwtCookieFilter(jwtDecoder), UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
