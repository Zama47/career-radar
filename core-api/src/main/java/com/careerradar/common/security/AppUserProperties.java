package com.careerradar.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The single user of this personal tool. Credentials live in env only; the password is
 * stored as a BCrypt hash.
 */
@ConfigurationProperties(prefix = "app.user")
public record AppUserProperties(String email, String passwordHash) {
}
