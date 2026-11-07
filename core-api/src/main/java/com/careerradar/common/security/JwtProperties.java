package com.careerradar.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings. The secret has no default on purpose: a missing or short secret must fail
 * startup instead of silently issuing forgeable tokens.
 */
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(String secret, long ttlSeconds) {
}
