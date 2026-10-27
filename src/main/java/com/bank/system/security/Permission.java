package com.bank.system.security;

import java.util.EnumSet;
import java.util.Set;

/**
 * Permission – fine-grained action permissions used by the RBAC policy.
 *
 * <p>Each {@code Permission} constant corresponds to a specific banking
 * operation.  The static factory methods on this class define which
 * permissions are granted to each {@link Role}.
 *
 * <h3>Design note</h3>
 * <p>Permissions are intentionally decoupled from roles so that the policy
 * ({@link com.bank.system.security.rbac.RbacPolicy}) can be updated without
 * changing the enum.  New permissions only need to be added here and wired
 * into the policy.
 */
public enum Permission {

    // ── Account permissions ───────────────────────────────────────────────────

    /** View own account balance and details. */
    VIEW_OWN_ACCOUNT,

    /** View any account in the system (tellers and above). */
    VIEW_ANY_ACCOUNT,

    /** Open a new bank account. */
    OPEN_ACCOUNT,

    /** Freeze or reactivate an existing account. */
    FREEZE_ACCOUNT,

    /** Permanently close an account. */
    CLOSE_ACCOUNT,

    // ── Transaction permissions ───────────────────────────────────────────────

    /** Initiate a deposit into an account. */
    DEPOSIT,

    /** Initiate a withdrawal from an account. */
    WITHDRAW,

    /** Initiate a fund transfer between accounts. */
    TRANSFER,

    /** View transaction history of own accounts. */
    VIEW_OWN_TRANSACTIONS,

    /** View transaction history of any account. */
    VIEW_ALL_TRANSACTIONS,

    // ── Audit permissions ─────────────────────────────────────────────────────

    /** Read audit log entries. */
    READ_AUDIT_LOG,

    /** Write (insert) audit log entries (service-internal use). */
    WRITE_AUDIT_LOG,

    // ── Administration permissions ────────────────────────────────────────────

    /** Create or modify system users and role assignments. */
    MANAGE_USERS,

    /** Access system-wide configuration and reports. */
    SYSTEM_ADMIN;

    // ─────────────────────────────────────────────────────────────────────────
    // Static role-to-permission mappings
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the set of permissions granted to the given role.
     *
     * @param role the role to resolve
     * @return an immutable-like {@link EnumSet} of permissions
     */
    public static Set<Permission> forRole(Role role) {
        return switch (role) {
            case CUSTOMER       -> customerPermissions();
            case TELLER         -> tellerPermissions();
            case BRANCH_MANAGER -> branchManagerPermissions();
            case AUDITOR        -> auditorPermissions();
            case ADMIN          -> adminPermissions();
        };
    }

    // ── Per-role sets ─────────────────────────────────────────────────────────

    private static Set<Permission> customerPermissions() {
        return EnumSet.of(
            VIEW_OWN_ACCOUNT,
            VIEW_OWN_TRANSACTIONS,
            TRANSFER                 // self-initiated transfers only
        );
    }

    private static Set<Permission> tellerPermissions() {
        return EnumSet.of(
            VIEW_OWN_ACCOUNT,
            VIEW_ANY_ACCOUNT,
            VIEW_OWN_TRANSACTIONS,
            VIEW_ALL_TRANSACTIONS,
            DEPOSIT,
            WITHDRAW,
            TRANSFER
        );
    }

    private static Set<Permission> branchManagerPermissions() {
        EnumSet<Permission> perms = EnumSet.copyOf(tellerPermissions());
        perms.add(OPEN_ACCOUNT);
        perms.add(FREEZE_ACCOUNT);
        perms.add(CLOSE_ACCOUNT);
        perms.add(READ_AUDIT_LOG);
        return perms;
    }

    private static Set<Permission> auditorPermissions() {
        return EnumSet.of(
            VIEW_ANY_ACCOUNT,
            VIEW_ALL_TRANSACTIONS,
            READ_AUDIT_LOG
        );
    }

    private static Set<Permission> adminPermissions() {
        return EnumSet.allOf(Permission.class);
    }
}
