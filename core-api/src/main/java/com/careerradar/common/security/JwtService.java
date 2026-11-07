package com.careerradar.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;

/**
 * Issues and verifies HS256 access tokens for the single configured user.
 */
public class JwtService {

    static final String ISSUER = "career-radar";
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long ttlSeconds;

    public JwtService(JwtProperties properties) {
        String secret = properties.secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET is not configured. Set it to a random value of at least 32 bytes.");
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes long.");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.ttlSeconds = properties.ttlSeconds() > 0 ? properties.ttlSeconds() : 3600;
    }

    public IssuedToken issue(String subject) {
        Instant now = Instant.now();
        String token = Jwts.builder()
                .subject(subject)
                .issuer(ISSUER)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(token, ttlSeconds);
    }

    /**
     * Returns the token subject when the signature, issuer and expiry are valid.
     */
    public Optional<String> validate(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(ISSUER)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.ofNullable(claims.getSubject());
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public record IssuedToken(String token, long expiresInSeconds) {
    }
}
