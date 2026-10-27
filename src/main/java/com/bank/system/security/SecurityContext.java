package com.bank.system.security;

/**
 * SecurityContext – thread-local holder for the authenticated principal.
 *
 * <p>This is the central "who is acting?" mechanism for the security layer.
 * The {@link com.bank.system.security.auth.AuthService} stores the principal
 * here on login; the
 * {@link com.bank.system.security.guard.SecureBankingService} reads it before
 * every privileged operation.
 *
 * <h3>Usage</h3>
 * <pre>
 *   // On login
 *   SecurityContext.set(principal);
 *
 *   // Inside a guarded method
 *   UserPrincipal current = SecurityContext.require();
 *
 *   // On logout / thread cleanup
 *   SecurityContext.clear();
 * </pre>
 *
 * <h3>Thread safety</h3>
 * <p>Uses a {@link ThreadLocal}, so each thread has its own isolated context.
 * Always call {@link #clear()} in a {@code finally} block or servlet filter
 * to prevent context leaking to the next request on a pooled thread.
 */
public final class SecurityContext {

    /** ThreadLocal holding the current principal. Null when not authenticated. */
    private static final ThreadLocal<UserPrincipal> HOLDER = new ThreadLocal<>();

    // Private constructor – utility class, not instantiable.
    private SecurityContext() {}

    // ── Mutators ──────────────────────────────────────────────────────────────

    /**
     * Binds a {@link UserPrincipal} to the current thread.
     *
     * @param principal the authenticated principal; must not be {@code null}
     * @throws IllegalArgumentException if {@code principal} is {@code null}
     */
    public static void set(UserPrincipal principal) {
        if (principal == null) {
            throw new IllegalArgumentException("Cannot set a null principal.");
        }
        HOLDER.set(principal);
    }

    /**
     * Removes the principal from the current thread's context.
     * Must be called at the end of every request / operation lifecycle.
     */
    public static void clear() {
        HOLDER.remove();
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /**
     * Returns the current principal, or {@code null} if no user is
     * authenticated on this thread.
     *
     * @return the current {@link UserPrincipal}, or {@code null}
     */
    public static UserPrincipal get() {
        return HOLDER.get();
    }

    /**
     * Returns the current principal, throwing if none is set.
     *
     * @return the authenticated {@link UserPrincipal} – never {@code null}
     * @throws com.bank.system.security.exception.AuthenticationException
     *         if no principal is bound to this thread
     */
    public static UserPrincipal require() {
        UserPrincipal principal = HOLDER.get();
        if (principal == null) {
            throw new com.bank.system.security.exception.AuthenticationException(
                "No authenticated user found. Please log in first.");
        }
        return principal;
    }

    /**
     * Returns {@code true} if a principal is currently bound to this thread.
     *
     * @return {@code true} if authenticated
     */
    public static boolean isAuthenticated() {
        return HOLDER.get() != null;
    }
}
