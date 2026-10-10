package com.bank.system.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * DatabaseConfig – singleton that owns the HikariCP connection pool.
 *
 * <p>All connection parameters are read from environment variables so that
 * credentials are never committed to source control.  Set the following
 * variables before starting the application:
 *
 * <pre>
 *   DB_URL      – JDBC URL, e.g. jdbc:postgresql://host:5432/dbname
 *   DB_USER     – database username
 *   DB_PASSWORD – database password
 * </pre>
 *
 * <p>Usage:
 * <pre>
 *   Connection conn = DatabaseConfig.getInstance().getConnection();
 * </pre>
 *
 * <p>Call {@link #shutdown()} once when the application exits.
 */
public final class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    // ── Singleton ────────────────────────────────────────────────────────────
    private static volatile DatabaseConfig instance;
    private final HikariDataSource dataSource;

    // ── Private constructor ──────────────────────────────────────────────────
    private DatabaseConfig() {
        String jdbcUrl  = requireEnv("DB_URL");
        String user     = requireEnv("DB_USER");
        String password = requireEnv("DB_PASSWORD");

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(jdbcUrl);
        cfg.setUsername(user);
        cfg.setPassword(password);

        // Pool sizing (suitable for a single-JVM academic deployment)
        cfg.setMaximumPoolSize(10);
        cfg.setMinimumIdle(2);
        cfg.setConnectionTimeout(30_000);   // 30 s – time to wait for a free connection
        cfg.setIdleTimeout(600_000);        // 10 min – evict idle connections after this
        cfg.setMaxLifetime(1_800_000);      // 30 min – recycle connections to avoid stale handles

        // PostgreSQL-specific keep-alive
        cfg.setConnectionTestQuery("SELECT 1");
        cfg.setPoolName("CoreBankingPool");

        // HikariCP configuration that removes the "Failed to load driver" INFO noise
        cfg.addDataSourceProperty("reWriteBatchedInserts", "true");

        log.info("Initialising HikariCP pool → {}", jdbcUrl.replaceAll("password=[^&]*", "password=***"));
        this.dataSource = new HikariDataSource(cfg);
        log.info("HikariCP pool '{}' is ready.", cfg.getPoolName());
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Returns the singleton {@code DatabaseConfig} instance, creating it on
     * first call (double-checked locking, thread-safe).
     */
    public static DatabaseConfig getInstance() {
        if (instance == null) {
            synchronized (DatabaseConfig.class) {
                if (instance == null) {
                    instance = new DatabaseConfig();
                }
            }
        }
        return instance;
    }

    /**
     * Borrows a {@link Connection} from the pool.
     *
     * <p>Always use try-with-resources so the connection is returned to the
     * pool even if an exception is thrown:
     * <pre>
     *   try (Connection conn = DatabaseConfig.getInstance().getConnection()) { ... }
     * </pre>
     *
     * @return a JDBC {@link Connection}
     * @throws SQLException if the pool cannot provide a connection
     */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Shuts down the connection pool gracefully.  Call once at application exit.
     */
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            log.info("Shutting down HikariCP pool '{}'.", dataSource.getPoolName());
            dataSource.close();
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Reads a mandatory environment variable; throws a descriptive
     * {@link IllegalStateException} if it is missing or blank.
     */
    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Required environment variable '" + name + "' is not set. " +
                "Export DB_URL, DB_USER, and DB_PASSWORD before starting the application."
            );
        }
        return value;
    }
}
