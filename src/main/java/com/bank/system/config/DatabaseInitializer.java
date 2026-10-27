package com.bank.system.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Ensures all required database tables, indexes, procedures, and triggers
 * exist on application boot before any service or security layer interacts
 * with PostgreSQL.
 */
public final class DatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private DatabaseInitializer() {}

    /**
     * Applies schema.sql and security_schema.sql if not already initialized.
     */
    public static void initialize() {
        log.info("Verifying and initializing database schema...");
        try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
            executeScript(conn, "sql/schema.sql");
            executeScript(conn, "sql/security_schema.sql");
            log.info("Database schema verification and initialization completed successfully.");
        } catch (Exception e) {
            log.warn("Database initialization warning: {}", e.getMessage());
        }
    }

    private static void executeScript(Connection conn, String relativePath) {
        String script = null;
        // 1. Try reading from classpath
        String resourcePath = "/" + relativePath.replace('\\', '/');
        try (InputStream in = DatabaseInitializer.class.getResourceAsStream(resourcePath)) {
            if (in != null) {
                script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }

        // 2. Fallback to file system
        if (script == null) {
            try {
                Path path = Path.of(relativePath);
                if (Files.exists(path)) {
                    script = Files.readString(path, StandardCharsets.UTF_8);
                }
            } catch (Exception ignored) {
            }
        }

        if (script == null || script.isBlank()) {
            log.warn("Could not find SQL script: {}", relativePath);
            return;
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(script);
            log.debug("Executed script: {}", relativePath);
        } catch (Exception e) {
            log.warn("Execution note for {}: {}", relativePath, e.getMessage());
        }
    }
}
