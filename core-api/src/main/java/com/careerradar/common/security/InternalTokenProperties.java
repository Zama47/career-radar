package com.careerradar.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Shared secret that authenticates collector requests to {@code /internal/**}.
 * Bound from {@code internal.token} / env {@code INTERNAL_TOKEN}.
 */
@ConfigurationProperties(prefix = "internal")
public record InternalTokenProperties(String token) {

    public boolean isConfigured() {
        return token != null && !token.isBlank();
    }
}
