package com.bank.system.security.auth;

import com.bank.system.config.DatabaseConfig;
import com.bank.system.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Upserts demo logins with real PBKDF2 hashes so the UI can sign in.
 * Placeholder rows in {@code sql/security_schema.sql} do not verify.
 */
public final class DemoUserSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);

    private record DemoUser(String username, String password, Role role, Integer customerId) {}

    private static final DemoUser[] USERS = {
        new DemoUser("admin",     "Admin@1234",    Role.ADMIN,          null),
        new DemoUser("teller01",  "Teller@1234",   Role.TELLER,         null),
        new DemoUser("mgr01",     "Manager@1234",  Role.BRANCH_MANAGER, null),
        new DemoUser("audit01",   "Auditor@1234",  Role.AUDITOR,        null),
        new DemoUser("alice",     "Customer@1234", Role.CUSTOMER,       1),
        new DemoUser("bob",       "Customer@1234", Role.CUSTOMER,       2)
    };

    private DemoUserSeeder() {}

    public static void seed() {
        String sql = """
            INSERT INTO bank_users (username, password_hash, role, customer_id)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (username) DO UPDATE SET
                password_hash   = EXCLUDED.password_hash,
                role            = EXCLUDED.role,
                customer_id     = EXCLUDED.customer_id,
                is_locked       = FALSE,
                failed_attempts = 0
            """;

        try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
            int seeded = 0;
            for (DemoUser user : USERS) {
                String hash = PasswordHasher.hash(user.password());
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, user.username());
                    ps.setString(2, hash);
                    ps.setString(3, user.role().name());
                    if (user.customerId() != null) {
                        ps.setInt(4, user.customerId());
                    } else {
                        ps.setNull(4, Types.INTEGER);
                    }
                    ps.executeUpdate();
                    seeded++;
                } catch (SQLException e) {
                    log.warn("Could not seed user '{}': {}", user.username(), e.getMessage());
                }
            }
            log.info("Demo users ready ({}/{}). Try alice/Customer@1234 or admin/Admin@1234.",
                seeded, USERS.length);
        } catch (SQLException e) {
            log.warn("Could not seed demo users (run sql/schema.sql and sql/security_schema.sql): {}",
                e.getMessage());
        }
    }
}
