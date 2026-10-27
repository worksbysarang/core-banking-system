package com.bank.system.security.auth;

import com.bank.system.config.DatabaseConfig;
import com.bank.system.security.Role;
import com.bank.system.security.SecurityContext;
import com.bank.system.security.UserPrincipal;
import com.bank.system.security.audit.AuditService;
import com.bank.system.security.exception.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;

/**
 * AuthService – handles user login, logout, MFA step-up, and account locking.
 *
 * <h3>Responsibilities</h3>
 * <ol>
 *   <li>Look up the user record by username from {@code bank_users}.</li>
 *   <li>Verify the password using {@link PasswordHasher}.</li>
 *   <li>Enforce account-lockout after {@value #MAX_FAILED_ATTEMPTS} consecutive failures.</li>
 *   <li>Build a {@link UserPrincipal} and bind it to the {@link SecurityContext}.</li>
 *   <li>Write success/failure audit events via {@link AuditService}.</li>
 *   <li>Simulate MFA step-up verification (TOTP check).</li>
 * </ol>
 *
 * <h3>High-value transaction MFA</h3>
 * <p>Transfers at or above {@value #HIGH_VALUE_THRESHOLD} require the caller
 * to have completed MFA step-up ({@link #verifyMfa}) before the secure
 * service layer will permit the operation.
 *
 * <h3>Thread safety</h3>
 * <p>Stateless – all state is stored in the thread-local
 * {@link SecurityContext} or in the database.
 */
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** Number of consecutive failed logins before the account is locked. */
    public static final int MAX_FAILED_ATTEMPTS = 5;

    /** Minimum transfer amount that triggers a mandatory MFA step-up check. */
    public static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("10000.00");

    private final AuditService auditService;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Production constructor – uses the live {@link AuditService}. */
    public AuthService() {
        this(new AuditService());
    }

    /** DI constructor – allows injecting a mock/stub for tests. */
    public AuthService(AuditService auditService) {
        this.auditService = auditService;
    }

    // ── Login / Logout ────────────────────────────────────────────────────────

    /**
     * Authenticates a user by username and plaintext password.
     *
     * <p>On success the resulting {@link UserPrincipal} is stored in the
     * thread-local {@link SecurityContext}.  On failure the failed-attempt
     * counter is incremented and an {@link AuthenticationException} is thrown.
     *
     * @param username  the login name
     * @param plaintext the raw password supplied by the user
     * @return the authenticated {@link UserPrincipal}
     * @throws AuthenticationException if credentials are invalid or account is locked
     */
    public UserPrincipal login(String username, String plaintext) {
        if (username == null || username.isBlank()) {
            throw new AuthenticationException("Username must not be blank.");
        }
        if (plaintext == null || plaintext.isBlank()) {
            throw new AuthenticationException("Password must not be blank.");
        }

        try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
            // ── 1. Fetch user row ──────────────────────────────────────────
            UserRow row = fetchUser(conn, username);

            if (row == null) {
                // Don't reveal whether the username exists (timing-safe dummy compare)
                PasswordHasher.hash("dummy_to_prevent_timing_attack");
                auditService.logEvent("LOGIN_FAILURE",
                    null, "Unknown username: " + sanitise(username));
                throw new AuthenticationException("Invalid username or password.");
            }

            // ── 2. Check lock ──────────────────────────────────────────────
            if (row.locked) {
                auditService.logEvent("LOGIN_BLOCKED",
                    null, "Locked account login attempt: " + username);
                throw new AuthenticationException(
                    "Account is locked due to too many failed attempts. "
                    + "Contact your administrator.");
            }

            // ── 3. Verify password ─────────────────────────────────────────
            boolean passwordOk = PasswordHasher.verify(plaintext, row.passwordHash);
            if (!passwordOk) {
                int attempts = incrementFailedAttempts(conn, row.userId);
                if (attempts >= MAX_FAILED_ATTEMPTS) {
                    lockAccount(conn, row.userId);
                    auditService.logEvent("ACCOUNT_LOCKED",
                        null, "Account locked after " + attempts + " failures: " + username);
                    throw new AuthenticationException(
                        "Too many failed attempts. Account has been locked.");
                }
                auditService.logEvent("LOGIN_FAILURE",
                    null, "Bad password for user: " + username
                          + " (attempt " + attempts + "/" + MAX_FAILED_ATTEMPTS + ")");
                throw new AuthenticationException("Invalid username or password.");
            }

            // ── 4. Success – reset failure counter ─────────────────────────
            resetFailedAttempts(conn, row.userId);
            updateLastLogin(conn, row.userId);

            // ── 5. Build principal & set context ──────────────────────────
            UserPrincipal principal = new UserPrincipal(
                row.userId, row.username, row.role,
                row.customerId, LocalDateTime.now());

            SecurityContext.set(principal);

            auditService.logEvent("LOGIN_SUCCESS",
                null, "Successful login: " + username + " [" + row.role + "]");
            log.info("User '{}' logged in with role {}", username, row.role);

            return principal;

        } catch (SQLException e) {
            throw new AuthenticationException(
                "Authentication failed due to a database error.", e);
        }
    }

    /**
     * Logs out the current user and clears the {@link SecurityContext}.
     * A logout audit event is recorded if a principal was present.
     */
    public void logout() {
        UserPrincipal principal = SecurityContext.get();
        if (principal != null) {
            auditService.logEvent("LOGOUT",
                null, "User logged out: " + principal.getUsername());
            log.info("User '{}' logged out.", principal.getUsername());
        }
        SecurityContext.clear();
    }

    // ── MFA step-up ───────────────────────────────────────────────────────────

    /**
     * Verifies a TOTP-style MFA code for the currently authenticated user.
     *
     * <p>In this academic implementation the check simulates TOTP verification
     * using a static demonstration code ({@code "123456"}).  In production this
     * would call a real TOTP library (e.g., Google Authenticator / FIDO2 WebAuthn).
     *
     * <p>On success, {@link UserPrincipal#markMfaVerified()} is called so that
     * high-value operations proceed without re-prompting.
     *
     * @param totpCode the 6-digit code provided by the user
     * @throws AuthenticationException if no session is active or the code is wrong
     */
    public void verifyMfa(String totpCode) {
        UserPrincipal principal = SecurityContext.require();

        // ── Simulated TOTP validation ──────────────────────────────────────
        // In production: replace with RFC 6238 TOTP or WebAuthn assertion.
        boolean valid = "123456".equals(totpCode);

        if (!valid) {
            auditService.logEvent("MFA_FAILURE",
                null, "MFA verification failed for: " + principal.getUsername());
            throw new AuthenticationException("MFA verification failed – invalid code.");
        }

        principal.markMfaVerified();
        auditService.logEvent("MFA_SUCCESS",
            null, "MFA verified for: " + principal.getUsername());
        log.info("MFA step-up verified for '{}'.", principal.getUsername());
    }

    /**
     * Returns {@code true} if the given transfer amount requires an MFA step-up
     * check before proceeding.
     *
     * @param amount transfer amount to evaluate
     * @return {@code true} if amount ≥ {@value #HIGH_VALUE_THRESHOLD}
     */
    public static boolean requiresMfa(BigDecimal amount) {
        return amount != null
            && amount.compareTo(HIGH_VALUE_THRESHOLD) >= 0;
    }

    // ── User registration (admin only) ────────────────────────────────────────

    /**
     * Creates a new system user in the {@code bank_users} table.
     *
     * <p>The caller must hold the {@link com.bank.system.security.Permission#MANAGE_USERS}
     * permission; enforcement is delegated to the
     * {@link com.bank.system.security.guard.SecureBankingService} layer.
     *
     * @param conn       active JDBC connection (for transactional use)
     * @param username   unique login name
     * @param plaintext  raw password (will be hashed before storage)
     * @param role       assigned role
     * @param customerId linked customer row, or {@code null} for staff
     * @return the new user's surrogate ID
     * @throws SQLException on any persistence error
     */
    public int createUser(Connection conn, String username, String plaintext,
                          Role role, Integer customerId) throws SQLException {
        String hash = PasswordHasher.hash(plaintext);
        String sql = """
            INSERT INTO bank_users (username, password_hash, role, customer_id)
            VALUES (?, ?, ?, ?)
            RETURNING user_id
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, hash);
            ps.setString(3, role.name());
            if (customerId != null) ps.setInt(4, customerId);
            else                   ps.setNull(4, Types.INTEGER);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("user_id");
                throw new SQLException("User insert did not return a generated ID.");
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Fetches a single user row by username, or null if not found. */
    private UserRow fetchUser(Connection conn, String username) throws SQLException {
        String sql = """
            SELECT user_id, username, password_hash, role,
                   customer_id, failed_attempts, is_locked
              FROM bank_users
             WHERE username = ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                UserRow row = new UserRow();
                row.userId       = rs.getInt("user_id");
                row.username     = rs.getString("username");
                row.passwordHash = rs.getString("password_hash");
                row.role         = Role.valueOf(rs.getString("role"));
                int cid = rs.getInt("customer_id");
                row.customerId   = rs.wasNull() ? null : cid;
                row.failedAttempts = rs.getInt("failed_attempts");
                row.locked       = rs.getBoolean("is_locked");
                return row;
            }
        }
    }

    /** Increments the failed-attempt counter and returns the new value. */
    private int incrementFailedAttempts(Connection conn, int userId) throws SQLException {
        String sql = """
            UPDATE bank_users
               SET failed_attempts = failed_attempts + 1
             WHERE user_id = ?
            RETURNING failed_attempts
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : MAX_FAILED_ATTEMPTS;
            }
        }
    }

    /** Locks the account and resets the failed-attempt counter. */
    private void lockAccount(Connection conn, int userId) throws SQLException {
        String sql = "UPDATE bank_users SET is_locked = true WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
    }

    /** Resets the failed-attempt counter after a successful login. */
    private void resetFailedAttempts(Connection conn, int userId) throws SQLException {
        String sql = "UPDATE bank_users SET failed_attempts = 0 WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
    }

    /** Records the last successful login timestamp. */
    private void updateLastLogin(Connection conn, int userId) throws SQLException {
        String sql = "UPDATE bank_users SET last_login_at = NOW() WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
    }

    /** Strips characters that could be used for log injection. */
    private static String sanitise(String input) {
        if (input == null) return "(null)";
        return input.replaceAll("[\r\n\t]", "_").substring(0, Math.min(50, input.length()));
    }

    // ── Inner record ──────────────────────────────────────────────────────────

    /** Lightweight value object to carry the JDBC result set data. */
    private static class UserRow {
        int     userId;
        String  username;
        String  passwordHash;
        Role    role;
        Integer customerId;
        int     failedAttempts;
        boolean locked;
    }
}
