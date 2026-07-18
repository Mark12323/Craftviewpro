package org.example.aisurv.persistence;

import java.util.Objects;

public record DatabaseSettings(String jdbcUrl, String username, String password) {
    public DatabaseSettings {
        Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");
        if (!jdbcUrl.startsWith("jdbc:postgresql://")) {
            throw new IllegalArgumentException("AISURV_DB_URL must be a PostgreSQL JDBC URL");
        }
        if (username.isBlank()) {
            throw new IllegalArgumentException("AISURV_DB_USER must not be blank");
        }
    }

    public static DatabaseSettings fromEnvironment() {
        return new DatabaseSettings(
                environmentOrDefault("AISURV_DB_URL", "jdbc:postgresql://localhost:5432/aisurv"),
                environmentOrDefault("AISURV_DB_USER", "aisurv"),
                environmentOrDefault("AISURV_DB_PASSWORD", "aisurv_dev_password")
        );
    }

    private static String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
