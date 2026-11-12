package com.example.book_store.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;

/** Rejects tokens revoked individually (by jti) or by a "revoke everything before T" mark on the user. */
@Component
@RequiredArgsConstructor
class RevocationValidator implements OAuth2TokenValidator<Jwt> {
    static final String REFRESH = "auth:refresh:";               // {jti} -> userId, deleted on use
    static final String REVOKED_ACCESS = "auth:revoked-access:"; // {jti} -> "1", lives until the token would expire
    static final String REVOKED_USER = "auth:revoked-user:";     // {userId} -> epochSecond

    private final StringRedisTemplate redis;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        List<String> v = redis.opsForValue().multiGet(List.of(REVOKED_ACCESS + jwt.getId(), REVOKED_USER + jwt.getSubject()));
        // <= not <: a token minted in the same second as the revoke can't be told apart, so it is revoked too
        boolean revoked = v != null && (v.get(0) != null
                || v.get(1) != null && jwt.getIssuedAt().getEpochSecond() <= Long.parseLong(v.get(1)));
        return revoked ? OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token revoked", null))
                : OAuth2TokenValidatorResult.success();
    }
}
