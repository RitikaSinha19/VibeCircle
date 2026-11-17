package com.vibecircle.ops;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pure checks, so they can be unit-tested. ProductionSanityCheck runs them at startup in the "prod" profile. */
public final class ProductionSettingsValidator {

    private static final List<String> PLACEHOLDERS = List.of("dev-only", "change-me", "changeme", "test-only", "secret");
    private static final List<String> WEAK_DB_PASSWORDS = List.of("vibe_dev", "password", "postgres", "admin", "123456");

    private ProductionSettingsValidator() {
    }

    public static List<String> problems(String jwtSecret, String dbPassword, String corsOrigins, String wsOrigins) {
        List<String> problems = new ArrayList<>();

        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            problems.add("JWT_SECRET must be at least 32 characters");
        } else {
            String lower = jwtSecret.toLowerCase(Locale.ROOT);
            if (PLACEHOLDERS.stream().anyMatch(lower::contains)) {
                problems.add("JWT_SECRET still looks like a placeholder; generate a random value");
            }
        }
        if (dbPassword == null || dbPassword.isBlank() || WEAK_DB_PASSWORDS.contains(dbPassword.toLowerCase(Locale.ROOT))) {
            problems.add("DB_PASSWORD is missing or is a well-known default");
        }
        problems.addAll(originProblems("CORS_ORIGINS", corsOrigins));
        problems.addAll(originProblems("WS_ORIGINS", wsOrigins));
        return problems;
    }

    private static List<String> originProblems(String name, String origins) {
        List<String> problems = new ArrayList<>();
        if (origins == null || origins.isBlank()) {
            problems.add(name + " must list your real frontend origin(s)");
        } else if (origins.contains("*")) {
            problems.add(name + " must not contain a wildcard");
        } else if (origins.contains("localhost") || origins.contains("127.0.0.1")) {
            problems.add(name + " still points at localhost");
        }
        return problems;
    }
}
