package com.careerradar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * The default in-memory user is disabled: the only account is the env-configured one checked
 * by {@code AuthController}, so Spring must not log a generated password.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class CareerRadarApplication {

    public static void main(String[] args) {
        SpringApplication.run(CareerRadarApplication.class, args);
    }
}
