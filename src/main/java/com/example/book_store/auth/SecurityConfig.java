package com.example.book_store.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;

@Configuration
class SecurityConfig {
    static final String ISSUER = "book-store";
    static final String ACCESS_COOKIE = "access_token";
    static final String TOKEN_USE = "token_use"; // "access" | "refresh": stops one being replayed as the other
    // these endpoints must work with a missing/expired/garbage access cookie, so it is not even parsed there
    private static final Set<String> NO_ACCESS_COOKIE = Set.of("/api/auth/signup", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout");

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtDecoder accessJwtDecoder) throws Exception {
        BearerTokenResolver fromCookie = req -> req.getCookies() == null || NO_ACCESS_COOKIE.contains(req.getRequestURI()) ? null
                : Arrays.stream(req.getCookies()).filter(c -> c.getName().equals(ACCESS_COOKIE))
                        .map(Cookie::getValue).findFirst().orElse(null);
        return http
                // shortcut: CSRF off because auth cookies are SameSite=Strict; add CSRF tokens if that is ever relaxed
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/auth/signup", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout",
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.bearerTokenResolver(fromCookie).jwt(j -> j.decoder(accessJwtDecoder)))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    /** Base64 secret from JWT_SECRET (`openssl rand -base64 48`); HS256 needs at least 256 bits. */
    @Bean
    SecretKey jwtKey(@Value("${app.auth.jwt-secret}") String secret) {
        byte[] bytes = Base64.getDecoder().decode(secret.trim());
        if (bytes.length < 32) throw new IllegalStateException("JWT_SECRET must decode to at least 32 bytes");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    /** Algorithm is pinned to HS256: tokens with alg=none, HS384, RS256, etc. are rejected. */
    @Bean
    JwtDecoder accessJwtDecoder(SecretKey key, RevocationValidator revocation) {
        return decoder(key, "access", revocation);
    }

    @Bean
    JwtDecoder refreshJwtDecoder(SecretKey key, RevocationValidator revocation) {
        return decoder(key, "refresh", revocation);
    }

    private static JwtDecoder decoder(SecretKey key, String use, RevocationValidator revocation) {
        NimbusJwtDecoder d = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        d.setJwtValidator(new DelegatingOAuth2TokenValidator<>(List.of(
                new JwtTimestampValidator(), new JwtIssuerValidator(ISSUER),
                new JwtClaimValidator<String>(TOKEN_USE, use::equals), revocation)));
        return d;
    }
}
