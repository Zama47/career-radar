package com.careerradar.support;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * One PostgreSQL container shared by every integration test in the JVM.
 * Ryuk removes it when the test JVM exits.
 */
public final class PostgresTestContainer {

    private static PostgreSQLContainer<?> instance;

    private PostgresTestContainer() {
    }

    public static synchronized PostgreSQLContainer<?> get() {
        if (instance == null) {
            PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16-alpine");
            container.start();
            instance = container;
        }
        return instance;
    }
}
