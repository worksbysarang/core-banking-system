package com.bank.system.security.rbac;

import com.bank.system.security.Permission;
import com.bank.system.security.UserPrincipal;
import com.bank.system.security.exception.AuthorizationException;

/**
 * RbacPolicy – enforces Role-Based Access Control (RBAC) rules.
 *
 * <h3>Design</h3>
 * <p>Each method in this class represents one authorization decision point.
 * It reads the {@link UserPrincipal} supplied by the caller (retrieved from
 * {@link com.bank.system.security.SecurityContext}) and either returns
 * quietly (access granted) or throws {@link AuthorizationException}.
 *
 * <p>Centralising all checks here means the
 * {@link com.bank.system.security.guard.SecureBankingService} never contains
 * raw {@code if/else} permission logic, keeping both classes focused and
 * independently testable.
 *
 * <h3>Thread safety</h3>
 * <p>Stateless – all methods are thread-safe.
 */
public class RbacPolicy {

    // ── Generic permission check ──────────────────────────────────────────────

    /**
     * Asserts that {@code principal} holds {@code permission}.
     *
     * @param principal  the authenticated user
     * @param permission the required permission
     * @throws AuthorizationException if the check fails
     */
    public void require(UserPrincipal principal, Permission permission) {
        if (!principal.hasPermission(permission)) {
            throw new AuthorizationException(
                "Access denied: role [" + principal.getRole() + "] does not hold "
                + permission + " permission.",
                permission);
        }
    }

    // ── Account-specific checks ───────────────────────────────────────────────

    /**
     * Asserts that {@code principal} may read the account owned by
     * {@code accountCustomerId}.
     *
     * <ul>
     *   <li>Customers must own the account ({@link UserPrincipal#ownsOrIsStaff}).</li>
     *   <li>Staff with {@link Permission#VIEW_ANY_ACCOUNT} are always allowed.</li>
     * </ul>
     *
     * @param principal        the authenticated user
     * @param accountCustomerId the {@code customer_id} stored on the target account
     */
    public void requireViewAccount(UserPrincipal principal, int accountCustomerId) {
        // Staff: need VIEW_ANY_ACCOUNT
        if (!principal.ownsOrIsStaff(accountCustomerId)) {
            throw new AuthorizationException(
                "Access denied: you may only view your own accounts.",
                Permission.VIEW_OWN_ACCOUNT);
        }
        // Own-account customers only need VIEW_OWN_ACCOUNT
        if (!principal.hasPermission(Permission.VIEW_OWN_ACCOUNT)
                && !principal.hasPermission(Permission.VIEW_ANY_ACCOUNT)) {
            throw new AuthorizationException(
                "Access denied: missing view-account permission.",
                Permission.VIEW_OWN_ACCOUNT);
        }
    }

    /**
     * Asserts that {@code principal} may initiate a deposit into the account
     * owned by {@code accountCustomerId}.
     */
    public void requireDeposit(UserPrincipal principal, int accountCustomerId) {
        require(principal, Permission.DEPOSIT);
    }

    /**
     * Asserts that {@code principal} may initiate a withdrawal from the account
     * owned by {@code accountCustomerId}.
     */
    public void requireWithdraw(UserPrincipal principal, int accountCustomerId) {
        require(principal, Permission.WITHDRAW);
    }

    /**
     * Asserts that {@code principal} may initiate a transfer from the sender
     * account (owned by {@code senderCustomerId}).
     *
     * <p>Customers may only transfer from their own accounts.
     */
    public void requireTransfer(UserPrincipal principal, int senderCustomerId) {
        require(principal, Permission.TRANSFER);
        if (!principal.ownsOrIsStaff(senderCustomerId)) {
            throw new AuthorizationException(
                "Access denied: customers may only transfer from their own accounts.",
                Permission.TRANSFER);
        }
    }

    /**
     * Asserts that {@code principal} may open a new account.
     */
    public void requireOpenAccount(UserPrincipal principal) {
        require(principal, Permission.OPEN_ACCOUNT);
    }

    /**
     * Asserts that {@code principal} may freeze or reactivate an account.
     */
    public void requireFreezeAccount(UserPrincipal principal) {
        require(principal, Permission.FREEZE_ACCOUNT);
    }

    /**
     * Asserts that {@code principal} may permanently close an account.
     */
    public void requireCloseAccount(UserPrincipal principal) {
        require(principal, Permission.CLOSE_ACCOUNT);
    }

    /**
     * Asserts that {@code principal} may view transaction history for the
     * account owned by {@code accountCustomerId}.
     */
    public void requireViewTransactions(UserPrincipal principal, int accountCustomerId) {
        boolean ownsAccount = principal.ownsOrIsStaff(accountCustomerId);
        boolean hasOwnPerm  = principal.hasPermission(Permission.VIEW_OWN_TRANSACTIONS);
        boolean hasAllPerm  = principal.hasPermission(Permission.VIEW_ALL_TRANSACTIONS);

        if (ownsAccount && hasOwnPerm) return;   // customer viewing own history
        if (hasAllPerm) return;                  // staff viewing any history

        throw new AuthorizationException(
            "Access denied: you do not have permission to view transaction history.",
            Permission.VIEW_OWN_TRANSACTIONS);
    }

    /**
     * Asserts that {@code principal} may read the audit log.
     */
    public void requireReadAuditLog(UserPrincipal principal) {
        require(principal, Permission.READ_AUDIT_LOG);
    }

    /**
     * Asserts that {@code principal} may manage system users.
     */
    public void requireManageUsers(UserPrincipal principal) {
        require(principal, Permission.MANAGE_USERS);
    }

    /**
     * Asserts that MFA step-up has been completed for a high-value operation.
     *
     * @param principal the authenticated user
     * @throws com.bank.system.security.exception.AuthenticationException
     *         if MFA has not been verified
     */
    public void requireMfa(UserPrincipal principal) {
        if (!principal.isMfaVerified()) {
            throw new com.bank.system.security.exception.AuthenticationException(
                "High-value operation requires MFA step-up verification. "
                + "Please complete MFA before proceeding.");
        }
    }
}
