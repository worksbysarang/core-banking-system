package com.bank.system.security;

/**
 * Role – RBAC roles for the Academic Banking Ledger.
 *
 * <p>Each role is assigned a human-readable label and a brief description
 * that documents its intended scope within the system.
 *
 * <h3>Hierarchy (least → most privileged)</h3>
 * <pre>
 *   CUSTOMER → TELLER → BRANCH_MANAGER → AUDITOR → ADMIN
 * </pre>
 *
 * <p>AUDITOR is intentionally read-only at the same privilege level as
 * BRANCH_MANAGER so that the audit role never gains write permissions.
 */
public enum Role {

    /**
     * Standard account holder.  Can view their own accounts and
     * transaction history, and initiate transfers from their own accounts.
     */
    CUSTOMER("Customer", "Account holder with self-service access"),

    /**
     * Branch teller. Can perform deposits, withdrawals, and transfers on
     * behalf of customers at their branch.
     */
    TELLER("Teller", "Branch staff authorised to process transactions"),

    /**
     * Branch manager. Inherits teller capabilities plus the ability to
     * open/freeze/close accounts and view branch-level reports.
     */
    BRANCH_MANAGER("Branch Manager", "Branch head with account-management authority"),

    /**
     * Read-only compliance / internal-audit role.  Can view all transactions
     * and audit logs but cannot initiate any write operations.
     */
    AUDITOR("Auditor", "Read-only compliance and audit access"),

    /**
     * System administrator. Full access to all operations including user
     * management and system configuration.  Requires MFA at login.
     */
    ADMIN("Administrator", "Full system access – privileged role");

    // ── Fields ────────────────────────────────────────────────────────────────

    private final String label;
    private final String description;

    // ── Constructor ───────────────────────────────────────────────────────────

    Role(String label, String description) {
        this.label       = label;
        this.description = description;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String getLabel()       { return label; }
    public String getDescription() { return description; }

    // ── Hierarchy helpers ─────────────────────────────────────────────────────

    /**
     * Returns {@code true} if this role has at least the same authority as
     * {@code minimum}.  Used for quick privilege checks.
     *
     * <p>Ordering is defined by the enum ordinal (declaration order above).
     *
     * @param minimum the minimum required role
     * @return {@code true} if this role's ordinal ≥ minimum's ordinal
     */
    public boolean isAtLeast(Role minimum) {
        return this.ordinal() >= minimum.ordinal();
    }

    @Override
    public String toString() { return label; }
}
