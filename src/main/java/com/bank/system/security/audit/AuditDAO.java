package com.bank.system.security.audit;

import com.bank.system.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AuditDAO – persistence layer for the {@code security_audit_log} table.
 *
 * <h3>Design notes</h3>
 * <ul>
 *   <li>All writes use {@code PreparedStatement} to prevent SQL injection.</li>
 *   <li>Insert failures are logged and <em>swallowed</em> so that a logging
 *       failure never blocks a legitimate banking operation.</li>
 *   <li>Read methods propagate {@link SQLException} to the caller
 *       ({@link AuditService}) for transparent error handling.</li>
 * </ul>
 */
public class AuditDAO {

    private static final Logger log = LoggerFactory.getLogger(AuditDAO.class);

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Inserts a new audit event into {@code security_audit_log}.
     *
     * <p>This method uses its own independent connection so that audit events
     * are persisted even if the main business transaction is rolled back.
     *
     * @param eventType     short label (e.g., {@code "LOGIN_SUCCESS"})
     * @param username      acting user's login name
     * @param accountNumber target account number, or {@code null}
     * @param details       human-readable event description
     * @param ipAddress     client IP address, or {@code null}
     * @return the generated {@code event_id}, or {@code -1} on failure
     */
    public long insertEvent(String eventType, String username,
                            Long accountNumber, String details,
                            String ipAddress) {
        String sql = """
            INSERT INTO security_audit_log
                (event_type, username, account_number, details, ip_address)
            VALUES (?, ?, ?, ?, ?)
            RETURNING event_id
            """;

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, truncate(eventType, 50));
            ps.setString(2, truncate(username,  100));

            if (accountNumber != null) ps.setLong(3, accountNumber);
            else                       ps.setNull(3, Types.BIGINT);

            ps.setString(4, truncate(details, 2000));

            if (ipAddress != null) ps.setString(5, truncate(ipAddress, 45));
            else                   ps.setNull(5, Types.VARCHAR);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong("event_id") : -1L;
            }

        } catch (SQLException e) {
            // Audit failures must not interrupt banking operations.
            log.error("Audit log write failed [type={}]: {}", eventType, e.getMessage(), e);
            return -1L;
        }
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Returns the most recent {@code limit} security events, newest first.
     *
     * @param limit maximum number of rows to return
     * @return list of {@link SecurityEvent} objects
     * @throws SQLException on database errors
     */
    public List<SecurityEvent> getRecentEvents(int limit) throws SQLException {
        String sql = """
            SELECT event_id, event_type, username, account_number,
                   details, ip_address, occurred_at
              FROM security_audit_log
             ORDER BY occurred_at DESC
             LIMIT ?
            """;

        List<SecurityEvent> events = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(mapRow(rs));
                }
            }
        }
        return events;
    }

    /**
     * Returns all security events related to a specific account number.
     *
     * @param accountNumber the target account
     * @param limit         maximum number of rows
     * @return list of {@link SecurityEvent} objects
     * @throws SQLException on database errors
     */
    public List<SecurityEvent> getEventsByAccount(long accountNumber,
                                                   int limit) throws SQLException {
        String sql = """
            SELECT event_id, event_type, username, account_number,
                   details, ip_address, occurred_at
              FROM security_audit_log
             WHERE account_number = ?
             ORDER BY occurred_at DESC
             LIMIT ?
            """;

        List<SecurityEvent> events = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, accountNumber);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(mapRow(rs));
                }
            }
        }
        return events;
    }

    /**
     * Returns all security events for a specific event type (e.g., all
     * {@code LOGIN_FAILURE} events within the last N rows).
     *
     * @param eventType filter type string
     * @param limit     maximum number of rows
     * @return list of {@link SecurityEvent} objects
     * @throws SQLException on database errors
     */
    public List<SecurityEvent> getEventsByType(String eventType,
                                               int limit) throws SQLException {
        String sql = """
            SELECT event_id, event_type, username, account_number,
                   details, ip_address, occurred_at
              FROM security_audit_log
             WHERE event_type = ?
             ORDER BY occurred_at DESC
             LIMIT ?
            """;

        List<SecurityEvent> events = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, eventType);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(mapRow(rs));
                }
            }
        }
        return events;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Maps a {@link ResultSet} row to a {@link SecurityEvent}. */
    private SecurityEvent mapRow(ResultSet rs) throws SQLException {
        long   id        = rs.getLong("event_id");
        String type      = rs.getString("event_type");
        String user      = rs.getString("username");
        long   accNum    = rs.getLong("account_number");
        Long   accNumBox = rs.wasNull() ? null : accNum;
        String details   = rs.getString("details");
        String ip        = rs.getString("ip_address");
        Timestamp ts     = rs.getTimestamp("occurred_at");
        LocalDateTime at = ts != null ? ts.toLocalDateTime() : LocalDateTime.now();
        return new SecurityEvent(id, type, user, accNumBox, details, ip, at);
    }

    /** Truncates a string to {@code max} characters to fit column widths. */
    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
