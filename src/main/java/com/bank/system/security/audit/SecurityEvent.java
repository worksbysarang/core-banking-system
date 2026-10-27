package com.bank.system.security.audit;

import java.time.LocalDateTime;

/**
 * SecurityEvent – immutable value object representing a single audit log entry.
 *
 * <p>Instances are created by {@link AuditService} and persisted by
 * {@link AuditDAO} to the {@code security_audit_log} table.
 *
 * <h3>Fields</h3>
 * <ul>
 *   <li>{@code eventId}      – surrogate key assigned by the database.</li>
 *   <li>{@code eventType}    – short upper-case label, e.g. {@code "LOGIN_SUCCESS"}.</li>
 *   <li>{@code username}     – acting user's login name, or {@code "SYSTEM"}.</li>
 *   <li>{@code accountNumber}– target account (nullable).</li>
 *   <li>{@code details}      – human-readable description of the event.</li>
 *   <li>{@code ipAddress}    – client IP where available (nullable).</li>
 *   <li>{@code occurredAt}   – UTC timestamp.</li>
 * </ul>
 */
public class SecurityEvent {

    private final long          eventId;
    private final String        eventType;
    private final String        username;
    private final Long          accountNumber;  // nullable
    private final String        details;
    private final String        ipAddress;      // nullable
    private final LocalDateTime occurredAt;

    // ── Constructor ───────────────────────────────────────────────────────────

    public SecurityEvent(long eventId, String eventType, String username,
                         Long accountNumber, String details,
                         String ipAddress, LocalDateTime occurredAt) {
        this.eventId       = eventId;
        this.eventType     = eventType;
        this.username      = username;
        this.accountNumber = accountNumber;
        this.details       = details;
        this.ipAddress     = ipAddress;
        this.occurredAt    = occurredAt;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public long          getEventId()       { return eventId; }
    public String        getEventType()     { return eventType; }
    public String        getUsername()      { return username; }
    public Long          getAccountNumber() { return accountNumber; }
    public String        getDetails()       { return details; }
    public String        getIpAddress()     { return ipAddress; }
    public LocalDateTime getOccurredAt()    { return occurredAt; }

    @Override
    public String toString() {
        return "SecurityEvent{id=" + eventId +
               ", type='" + eventType + '\'' +
               ", user='" + username + '\'' +
               ", account=" + accountNumber +
               ", at=" + occurredAt + '}';
    }
}
