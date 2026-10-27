package com.bank.system.security;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * UserPrincipal – the authenticated identity of a logged-in user.
 *
 * <p>An instance of this class is created by
 * {@link com.bank.system.security.auth.AuthService} on successful login and
 * stored in the thread-local {@link SecurityContext}.  It is immutable after
 * construction.
 *
 * <h3>Fields</h3>
 * <ul>
 *   <li>{@code userId}     – surrogate key from the {@code bank_users} table.</li>
 *   <li>{@code username}   – unique login name.</li>
 *   <li>{@code role}       – assigned {@link Role} (single-role model).</li>
 *   <li>{@code customerId} – links the principal to a {@code customers} row
 *                            (null for tellers, managers, admins).</li>
 *   <li>{@code permissions}– resolved at login from
 *                            {@link Permission#forRole(Role)}.</li>
 *   <li>{@code loginTime}  – UTC timestamp of successful authentication.</li>
 *   <li>{@code mfaVerified}– {@code true} once step-up MFA is satisfied.</li>
 * </ul>
 */
public class UserPrincipal {

    private final int            userId;
    private final String         username;
    private final Role           role;
    private final Integer        customerId;      // nullable for staff accounts
    private final Set<Permission> permissions;
    private final LocalDateTime  loginTime;
    private       boolean        mfaVerified;     // mutable: set after MFA step

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * Creates a fully resolved principal.  The permission set is derived
     * automatically from the supplied role.
     *
     * @param userId     database user ID
     * @param username   login name
     * @param role       assigned role
     * @param customerId linked customer row, or {@code null} for staff
     * @param loginTime  timestamp of successful authentication
     */
    public UserPrincipal(int userId, String username, Role role,
                         Integer customerId, LocalDateTime loginTime) {
        this.userId      = userId;
        this.username    = Objects.requireNonNull(username, "username");
        this.role        = Objects.requireNonNull(role, "role");
        this.customerId  = customerId;
        this.loginTime   = Objects.requireNonNull(loginTime, "loginTime");
        this.permissions = Collections.unmodifiableSet(
                               EnumSet.copyOf(Permission.forRole(role)));
        this.mfaVerified = false;
    }

    // ── Permission helpers ────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the principal holds the given permission.
     *
     * @param permission the permission to check
     * @return {@code true} if granted
     */
    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }

    /**
     * Returns {@code true} if the principal holds ALL of the supplied
     * permissions.
     *
     * @param required the permissions that must all be present
     * @return {@code true} if all are granted
     */
    public boolean hasAllPermissions(Permission... required) {
        for (Permission p : required) {
            if (!permissions.contains(p)) return false;
        }
        return true;
    }

    // ── Ownership check ───────────────────────────────────────────────────────

    /**
     * Returns {@code true} if this principal owns the account identified by
     * {@code accountCustomerId}, or if the principal is staff (non-customer).
     *
     * <p>Used in the secure service to enforce that customers can only
     * access their own accounts.
     *
     * @param accountCustomerId the {@code customer_id} of the target account
     * @return {@code true} if access is permitted from an ownership perspective
     */
    public boolean ownsOrIsStaff(int accountCustomerId) {
        if (role == Role.CUSTOMER) {
            return customerId != null && customerId.equals(accountCustomerId);
        }
        return true;   // staff roles are not ownership-restricted
    }

    // ── MFA ───────────────────────────────────────────────────────────────────

    /**
     * Marks this principal as having completed MFA step-up verification.
     * Called by {@link com.bank.system.security.auth.AuthService} after the
     * second factor is validated.
     */
    public void markMfaVerified() { this.mfaVerified = true; }

    /**
     * Returns {@code true} if step-up MFA has been verified for this session.
     * Required before high-value transactions are authorised.
     */
    public boolean isMfaVerified() { return mfaVerified; }

    // ── Getters ───────────────────────────────────────────────────────────────

    public int             getUserId()     { return userId; }
    public String          getUsername()   { return username; }
    public Role            getRole()       { return role; }
    public Integer         getCustomerId() { return customerId; }
    public Set<Permission> getPermissions(){ return permissions; }
    public LocalDateTime   getLoginTime()  { return loginTime; }

    @Override
    public String toString() {
        return "UserPrincipal{userId=" + userId +
               ", username='" + username + '\'' +
               ", role=" + role +
               ", mfaVerified=" + mfaVerified + '}';
    }
}
