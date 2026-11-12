package com.example.book_store.auth;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class AuthService {
    record Session(String accessToken, String refreshToken, User user) {
    }

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder accessJwtDecoder;
    private final JwtDecoder refreshJwtDecoder;
    private final StringRedisTemplate redis;

    @Getter // read via the proxy by the controller
    @Value("${app.auth.access-token-ttl:15m}")
    private Duration accessTtl;
    @Getter
    @Value("${app.auth.refresh-token-ttl:7d}")
    private Duration refreshTtl;

    // lets login spend the same Argon2 time for unknown emails, so timing does not reveal which emails exist
    private String dummyHash;

    User signup(SignupRequest r) {
        String email = r.email().trim().toLowerCase();
        if (users.existsByEmail(email)) throw conflict();
        try {
            return users.saveAndFlush(User.builder().fullName(r.fullName().trim()).email(email)
                    .passwordHash(encoder.encode(r.password())).dateOfBirth(r.dateOfBirth()).sex(r.sex()).build());
        } catch (DataIntegrityViolationException e) { // concurrent signup with same email
            throw conflict();
        }
    }

    Session login(LoginRequest r) {
        if (dummyHash == null) dummyHash = encoder.encode("dummy");
        User user = users.findByEmail(r.email().trim().toLowerCase()).orElse(null);
        boolean ok = encoder.matches(r.password(), user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !ok) throw unauthorized();
        return issue(user);
    }

    /** One-time use: the refresh jti is deleted atomically, so a replayed token finds nothing. */
    Session refresh(String refreshToken) {
        Jwt jwt = decode(refreshJwtDecoder, refreshToken);
        String userId = redis.opsForValue().getAndDelete(RevocationValidator.REFRESH + jwt.getId());
        if (userId == null) throw unauthorized();
        return issue(users.findById(Long.valueOf(userId)).orElseThrow(AuthService::unauthorized));
    }

    /** Best effort and never fails: a dead or missing token has nothing left to revoke. */
    void logout(String accessToken, String refreshToken) {
        Jwt access = tryDecode(accessJwtDecoder, accessToken);
        if (access != null) { // decoding already rejected expired tokens, so nothing expired is stored
            redis.opsForValue().set(RevocationValidator.REVOKED_ACCESS + access.getId(), "1",
                    Duration.between(Instant.now(), access.getExpiresAt()));
        }
        Jwt refresh = tryDecode(refreshJwtDecoder, refreshToken);
        if (refresh != null) redis.delete(RevocationValidator.REFRESH + refresh.getId());
    }

    /** Invalidates every token (access and refresh) issued up to now. */
    void logoutAll(Long userId) {
        Duration ttl = accessTtl.compareTo(refreshTtl) > 0 ? accessTtl : refreshTtl; // by then all older tokens expired anyway
        redis.opsForValue().set(RevocationValidator.REVOKED_USER + userId, String.valueOf(Instant.now().getEpochSecond()), ttl);
    }

    User user(Long id) {
        return users.findById(id).orElseThrow(AuthService::unauthorized);
    }

    private Session issue(User user) {
        Instant now = Instant.now();
        String refreshJti = UUID.randomUUID().toString();
        String access = sign(user, "access", UUID.randomUUID().toString(), now, accessTtl);
        String refresh = sign(user, "refresh", refreshJti, now, refreshTtl);
        redis.opsForValue().set(RevocationValidator.REFRESH + refreshJti, user.getId().toString(), refreshTtl);
        return new Session(access, refresh, user);
    }

    private String sign(User user, String use, String jti, Instant now, Duration ttl) {
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(SecurityConfig.ISSUER).subject(user.getId().toString())
                .id(jti).issuedAt(now).expiresAt(now.plus(ttl))
                .claim(SecurityConfig.TOKEN_USE, use).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private static Jwt decode(JwtDecoder decoder, String token) {
        try {
            return decoder.decode(token);
        } catch (JwtException e) {
            throw unauthorized();
        }
    }

    private static Jwt tryDecode(JwtDecoder decoder, String token) {
        try {
            return token == null ? null : decoder.decode(token);
        } catch (JwtException e) {
            return null;
        }
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    private static ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
    }
}
