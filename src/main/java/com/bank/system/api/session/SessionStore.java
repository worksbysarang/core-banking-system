package com.bank.system.api.session;

import com.bank.system.security.UserPrincipal;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SessionStore – in-memory Bearer token → {@link UserPrincipal} registry.
 *
 * <h3>Design</h3>
 * <ul>
 *   <li>On successful login the {@link com.bank.system.api.controller.AuthController}
 *       calls {@link #create(UserPrincipal)} to mint a UUID token and store
 *       the principal.</li>
 *   <li>The {@link com.bank.system.api.filter.AuthFilter} calls
 *       {@link #get(String)} on every request to restore the principal before
 *       the controller runs.</li>
 *   <li>Tokens expire after {@value #SESSION_MINUTES} minutes of creation
 *       (sliding expiry is out of scope for an academic project).</li>
 * </ul>
 *
 * <h3>Production note</h3>
 * <p>For a real deployment replace this with a Redis-backed session store or
 * signed JWT validation.  In-memory state is lost on restart and does not
 * scale horizontally.
 *
 * <h3>Thread safety</h3>
 * <p>Uses a {@link ConcurrentHashMap} – safe for concurrent HTTP handlers.
 */
public class SessionStore {

    /** How long (in minutes) a session token remains valid. */
    public static final int SESSION_MINUTES = 30;

    /** Singleton instance – one store for the whole JVM process. */
    private static final SessionStore INSTANCE = new SessionStore();

    public static SessionStore getInstance() { return INSTANCE; }

    // token  →  entry (principal + creation time)
    private final Map<String, Entry> sessions = new ConcurrentHashMap<>();

    private SessionStore() {}

    // ── API ───────────────────────────────────────────────────────────────────

    /**
     * Creates a new session for the given principal and returns the token.
     *
     * @param principal the authenticated user
     * @return the opaque Bearer token to send to the client
     */
    public String create(UserPrincipal principal) {
        String token = UUID.randomUUID().toString();
        sessions.put(token, new Entry(principal, LocalDateTime.now()));
        return token;
    }

    /**
     * Looks up a session by token.
     *
     * @param token the Bearer token from the {@code Authorization} header
     * @return the {@link UserPrincipal}, or {@code null} if the token is
     *         unknown or has expired
     */
    public UserPrincipal get(String token) {
        if (token == null) return null;
        Entry entry = sessions.get(token);
        if (entry == null) return null;
        if (entry.createdAt.plusMinutes(SESSION_MINUTES).isBefore(LocalDateTime.now())) {
            sessions.remove(token);   // lazy expiry
            return null;
        }
        return entry.principal;
    }

    /**
     * Removes a session (called on logout).
     *
     * @param token the token to invalidate
     */
    public void invalidate(String token) {
        if (token != null) sessions.remove(token);
    }

    /**
     * Returns the number of currently active sessions.
     * Useful for monitoring / admin endpoints.
     */
    public int activeCount() {
        // Prune expired entries before counting
        LocalDateTime now = LocalDateTime.now();
        sessions.entrySet().removeIf(e ->
            e.getValue().createdAt.plusMinutes(SESSION_MINUTES).isBefore(now));
        return sessions.size();
    }

    // ── Inner record ──────────────────────────────────────────────────────────

    private record Entry(UserPrincipal principal, LocalDateTime createdAt) {}
}
