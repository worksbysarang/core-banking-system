package com.bank.system.security.audit;

import com.bank.system.security.SecurityContext;
import com.bank.system.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;

/**
 * AuditService – high-level audit logging API used throughout the security layer.
 *
 * <h3>Responsibilities</h3>
 * <ol>
 *   <li>Resolve the current authenticated user from {@link SecurityContext}
 *       (defaults to {@code "SYSTEM"} if no session is active).</li>
 *   <li>Delegate persistence to {@link AuditDAO}.</li>
 *   <li>Guarantee that audit write failures never propagate to callers –
 *       they are logged at ERROR level but swallowed.</li>
 *   <li>Provide read methods for the admin / auditor dashboard.</li>
 * </ol>
 *
 * <h3>Event types</h3>
 * <pre>
 *   LOGIN_SUCCESS    LOGIN_FAILURE    LOGIN_BLOCKED
 *   LOGOUT           ACCOUNT_LOCKED
 *   MFA_SUCCESS      MFA_FAILURE
 *   DEPOSIT          WITHDRAWAL       TRANSFER
 *   ACCOUNT_OPENED   ACCOUNT_FROZEN   ACCOUNT_CLOSED
 *   ACCESS_DENIED
 * </pre>
 *
 * <h3>Thread safety</h3>
 * <p>Stateless; delegates state to the thread-local {@link SecurityContext} and
 * the stateless {@link AuditDAO}.
 */
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditDAO auditDAO;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Production constructor. */
    public AuditService() {
        this(new AuditDAO());
    }

    /** DI constructor for testing. */
    public AuditService(AuditDAO auditDAO) {
        this.auditDAO = auditDAO;
    }

    // ── Write helpers ─────────────────────────────────────────────────────────

    /**
     * Logs a security event, resolving the acting username from the
     * current {@link SecurityContext}.
     *
     * @param eventType     short event label (e.g., {@code "DEPOSIT"})
     * @param accountNumber target account number, or {@code null}
     * @param details       human-readable description
     */
    public void logEvent(String eventType, Long accountNumber, String details) {
        String username = resolveUsername();
        logEvent(eventType, username, accountNumber, details, null);
    }

    /**
     * Logs a security event with all fields explicit.  Useful when the
     * acting username is not in the {@link SecurityContext} (e.g., failed login).
     *
     * @param eventType     short event label
     * @param username      acting user's name
     * @param accountNumber target account number, or {@code null}
     * @param details       human-readable description
     * @param ipAddress     client IP address, or {@code null}
     */
    public void logEvent(String eventType, String username,
                         Long accountNumber, String details,
                         String ipAddress) {
        try {
            long id = auditDAO.insertEvent(eventType, username,
                                           accountNumber, details, ipAddress);
            log.debug("Audit event persisted [type={}, user={}, account={}, id={}]",
                      eventType, username, accountNumber, id);
        } catch (Exception e) {
            // Never let a logging failure break a banking operation.
            log.error("Failed to persist audit event [type={}]: {}",
                      eventType, e.getMessage(), e);
        }
    }

    /**
     * Convenience overload that logs a transaction event (deposit/withdrawal/transfer).
     *
     * @param eventType     one of {@code DEPOSIT}, {@code WITHDRAWAL}, {@code TRANSFER}
     * @param accountNumber the primary account involved
     * @param details       description including amount / counterparty info
     */
    public void logTransactionEvent(String eventType, long accountNumber, String details) {
        logEvent(eventType, accountNumber, details);
    }

    /**
     * Logs an access-denied event.  Called by the secure service when
     * {@link com.bank.system.security.rbac.RbacPolicy} throws.
     *
     * @param accountNumber relevant account (nullable)
     * @param reason        reason for the denial
     */
    public void logAccessDenied(Long accountNumber, String reason) {
        logEvent("ACCESS_DENIED", accountNumber, reason);
    }

    // ── Read helpers ──────────────────────────────────────────────────────────

    /**
     * Returns the most recent security events.
     *
     * @param limit maximum number of records
     * @return list of {@link SecurityEvent} objects, newest first
     * @throws SQLException on database errors
     */
    public List<SecurityEvent> getRecentEvents(int limit) throws SQLException {
        return auditDAO.getRecentEvents(limit);
    }

    /**
     * Returns security events related to a specific account.
     *
     * @param accountNumber account to filter by
     * @param limit         maximum number of records
     * @return list of {@link SecurityEvent} objects
     * @throws SQLException on database errors
     */
    public List<SecurityEvent> getEventsByAccount(long accountNumber,
                                                   int limit) throws SQLException {
        return auditDAO.getEventsByAccount(accountNumber, limit);
    }

    /**
     * Returns security events of a specific type.
     *
     * @param eventType event type to filter by (e.g., {@code "LOGIN_FAILURE"})
     * @param limit     maximum number of records
     * @return list of {@link SecurityEvent} objects
     * @throws SQLException on database errors
     */
    public List<SecurityEvent> getEventsByType(String eventType,
                                               int limit) throws SQLException {
        return auditDAO.getEventsByType(eventType, limit);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Resolves the acting username from the current security context. */
    private static String resolveUsername() {
        UserPrincipal principal = SecurityContext.get();
        return (principal != null) ? principal.getUsername() : "SYSTEM";
    }
}
