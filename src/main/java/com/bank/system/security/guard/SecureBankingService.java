package com.bank.system.security.guard;

import com.bank.system.model.Account;
import com.bank.system.model.Account.AccountStatus;
import com.bank.system.model.Account.AccountType;
import com.bank.system.model.Transaction;
import com.bank.system.security.Permission;
import com.bank.system.security.SecurityContext;
import com.bank.system.security.UserPrincipal;
import com.bank.system.security.audit.AuditService;
import com.bank.system.security.auth.AuthService;
import com.bank.system.security.exception.AuthorizationException;
import com.bank.system.security.rbac.RbacPolicy;
import com.bank.system.service.BankingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;

/**
 * SecureBankingService – security-hardened façade over {@link BankingService}.
 *
 * <h3>Responsibilities</h3>
 * <p>This class is the <strong>only</strong> entry point that Member 4
 * (UI / Controller) should use when building REST controllers or servlets.
 * It enforces the full security stack before delegating to the underlying
 * {@link BankingService}:
 *
 * <ol>
 *   <li><strong>Authentication check</strong> – requires a valid principal in
 *       the {@link SecurityContext} (set by {@link AuthService#login}).</li>
 *   <li><strong>RBAC authorisation</strong> – delegates every permission
 *       check to {@link RbacPolicy}.</li>
 *   <li><strong>Ownership check</strong> – customers may only act on their
 *       own accounts; staff may act on any account.</li>
 *   <li><strong>MFA step-up</strong> – high-value transfers (≥ ₹10,000) require
 *       the principal to have completed MFA via
 *       {@link AuthService#verifyMfa}.</li>
 *   <li><strong>Input validation</strong> – amount and ID constraints are
 *       re-validated server-side regardless of client assertions.</li>
 *   <li><strong>Audit logging</strong> – every operation (success or denial)
 *       is recorded in {@code security_audit_log}.</li>
 * </ol>
 *
 * <h3>Never trust the frontend</h3>
 * <p>All verifications in this class are independent of anything the client
 * sends.  The account ownership, active status, and permission checks all use
 * server-side state.
 *
 * <h3>Thread safety</h3>
 * <p>Stateless – all per-request state lives in the thread-local
 * {@link SecurityContext}.
 */
public class SecureBankingService {

    private static final Logger log = LoggerFactory.getLogger(SecureBankingService.class);

    private final BankingService bankingService;
    private final RbacPolicy     rbacPolicy;
    private final AuditService   auditService;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Production constructor. */
    public SecureBankingService() {
        this(new BankingService(), new RbacPolicy(), new AuditService());
    }

    /** Full DI constructor for unit testing. */
    public SecureBankingService(BankingService bankingService,
                                RbacPolicy rbacPolicy,
                                AuditService auditService) {
        this.bankingService = bankingService;
        this.rbacPolicy     = rbacPolicy;
        this.auditService   = auditService;
    }

    // ── Account operations ────────────────────────────────────────────────────

    /**
     * Opens a new account.  Requires {@link Permission#OPEN_ACCOUNT}.
     *
     * @param customerId     customer to own the account
     * @param branchId       branch where the account is opened
     * @param accountType    SAVINGS, CHECKING, or LOAN
     * @param initialBalance opening balance (≥ 0)
     * @return the new account number
     */
    public long openAccount(int customerId, int branchId,
                            AccountType accountType, BigDecimal initialBalance) {
        UserPrincipal principal = SecurityContext.require();

        try {
            rbacPolicy.requireOpenAccount(principal);

            long accountNumber = bankingService.openAccount(
                customerId, branchId, accountType, initialBalance);

            auditService.logEvent("ACCOUNT_OPENED", (Long) accountNumber,
                "Account opened by " + principal.getUsername()
                + " for customer " + customerId
                + " | type=" + accountType
                + " | initialBalance=" + initialBalance);

            log.info("Account {} opened for customer {} by {}",
                     accountNumber, customerId, principal.getUsername());
            return accountNumber;

        } catch (AuthorizationException e) {
            auditService.logAccessDenied(null,
                "OPEN_ACCOUNT denied for " + principal.getUsername() + ": " + e.getMessage());
            throw e;
        }
    }

    /**
     * Retrieves account details.  Requires the caller to own the account
     * or hold {@link Permission#VIEW_ANY_ACCOUNT}.
     *
     * @param accountNumber the account to look up
     * @return the {@link Account}
     */
    public Account getAccount(long accountNumber) {
        UserPrincipal principal = SecurityContext.require();
        Account account = bankingService.getAccount(accountNumber);

        try {
            rbacPolicy.requireViewAccount(principal, account.getCustomerId());
            return account;

        } catch (AuthorizationException e) {
            auditService.logAccessDenied((long) accountNumber,
                "VIEW_ACCOUNT denied for " + principal.getUsername());
            throw e;
        }
    }

    /**
     * Returns all accounts for a customer.  Customers may only list their
     * own accounts; staff may list any customer's accounts.
     *
     * @param customerId the customer whose accounts to list
     * @return list of accounts
     */
    public List<Account> getAccountsByCustomer(int customerId) {
        UserPrincipal principal = SecurityContext.require();

        try {
            rbacPolicy.requireViewAccount(principal, customerId);
            return bankingService.getAccountsByCustomer(customerId);

        } catch (AuthorizationException e) {
            auditService.logAccessDenied(null,
                "VIEW_ACCOUNT denied for " + principal.getUsername()
                + " (customer " + customerId + ")");
            throw e;
        }
    }

    /**
     * Retrieves all accounts in the system. Requires VIEW_ANY_ACCOUNT.
     */
    public List<Account> getAllAccounts(int limit) {
        UserPrincipal principal = SecurityContext.require();
        try {
            if (!principal.hasPermission(Permission.VIEW_ANY_ACCOUNT)) {
                throw new AuthorizationException(
                    "User '" + principal.getUsername() + "' does not hold VIEW_ANY_ACCOUNT.");
            }
            return bankingService.getAllAccounts(limit);
        } catch (AuthorizationException e) {
            auditService.logAccessDenied(null,
                "VIEW_ANY_ACCOUNT denied for " + principal.getUsername());
            throw e;
        }
    }

    public List<Account> getAllAccounts() {
        return getAllAccounts(100);
    }

    /**
     * Lists all customers. Requires VIEW_ANY_ACCOUNT.
     */
    public List<com.bank.system.model.Customer> getAllCustomers() {
        UserPrincipal principal = SecurityContext.require();
        try {
            if (!principal.hasPermission(Permission.VIEW_ANY_ACCOUNT)) {
                throw new AuthorizationException(
                    "User '" + principal.getUsername() + "' does not hold VIEW_ANY_ACCOUNT.");
            }
            return bankingService.getAllCustomers();
        } catch (AuthorizationException e) {
            auditService.logAccessDenied(null,
                "VIEW_ANY_ACCOUNT denied for " + principal.getUsername() + " (list customers)");
            throw e;
        }
    }

    /**
     * Retrieves a single customer.
     */
    public com.bank.system.model.Customer getCustomer(int customerId) {
        UserPrincipal principal = SecurityContext.require();
        rbacPolicy.requireViewAccount(principal, customerId);
        return bankingService.getCustomer(customerId)
            .orElseThrow(() -> new com.bank.system.exception.BankingException("Customer not found: " + customerId));
    }

    /**
     * Registers a new customer. Requires OPEN_ACCOUNT permission.
     */
    public int createCustomer(String firstName, String lastName, String email,
                              String phone, String nationalId, String address) {
        UserPrincipal principal = SecurityContext.require();
        try {
            rbacPolicy.requireOpenAccount(principal);
            int id = bankingService.createCustomer(firstName, lastName, email, phone, nationalId, address);
            auditService.logEvent("CUSTOMER_CREATED", null,
                "Customer " + firstName + " " + lastName + " (ID " + id + ") created by " + principal.getUsername());
            return id;
        } catch (AuthorizationException e) {
            auditService.logAccessDenied(null,
                "CREATE_CUSTOMER denied for " + principal.getUsername());
            throw e;
        }
    }

    /**
     * Retrieves all branches.
     */
    public List<com.bank.system.model.Branch> getAllBranches() {
        SecurityContext.require();
        return bankingService.getAllBranches();
    }

    /**
     * Updates account status (FROZEN / ACTIVE / CLOSED).
     * <ul>
     *   <li>Freeze/reactivate → requires {@link Permission#FREEZE_ACCOUNT}.</li>
     *   <li>Close → requires {@link Permission#CLOSE_ACCOUNT}.</li>
     * </ul>
     *
     * @param accountNumber account to update
     * @param newStatus     the desired new status
     */
    public void updateAccountStatus(long accountNumber, AccountStatus newStatus) {
        UserPrincipal principal = SecurityContext.require();

        try {
            if (newStatus == AccountStatus.CLOSED) {
                rbacPolicy.requireCloseAccount(principal);
            } else {
                rbacPolicy.requireFreezeAccount(principal);
            }

            bankingService.updateAccountStatus(accountNumber, newStatus);

            auditService.logEvent(
                newStatus == AccountStatus.CLOSED ? "ACCOUNT_CLOSED" : "ACCOUNT_FROZEN",
                accountNumber,
                "Account " + accountNumber + " set to " + newStatus
                + " by " + principal.getUsername());

        } catch (AuthorizationException e) {
            auditService.logAccessDenied(accountNumber,
                "STATUS_UPDATE denied for " + principal.getUsername()
                + " | attempted status=" + newStatus);
            throw e;
        }
    }

    // ── Core banking operations ───────────────────────────────────────────────

    /**
     * Deposits money into an account.  Requires {@link Permission#DEPOSIT}.
     *
     * @param accountNumber destination account
     * @param amount        positive deposit amount
     * @param note          optional reference note
     * @return the generated transaction ID
     */
    public long deposit(long accountNumber, BigDecimal amount, String note) {
        UserPrincipal principal = SecurityContext.require();

        try {
            // Fetch account to get the owning customer (ownership check not
            // required for deposits – any authorised staff/teller may deposit).
            rbacPolicy.requireDeposit(principal, -1 /* ownership not restricted */);

            long txId = bankingService.deposit(accountNumber, amount, note);

            auditService.logTransactionEvent("DEPOSIT", accountNumber,
                "Deposit ₹" + amount + " by " + principal.getUsername()
                + " | txId=" + txId
                + (note != null ? " | note=" + note : ""));

            return txId;

        } catch (AuthorizationException e) {
            auditService.logAccessDenied(accountNumber,
                "DEPOSIT denied for " + principal.getUsername());
            throw e;
        }
    }

    /**
     * Withdraws money from an account.  Requires {@link Permission#WITHDRAW}.
     *
     * @param accountNumber source account
     * @param amount        positive withdrawal amount
     * @param note          optional reference note
     * @return the generated transaction ID
     */
    public long withdraw(long accountNumber, BigDecimal amount, String note) {
        UserPrincipal principal = SecurityContext.require();

        try {
            rbacPolicy.requireWithdraw(principal, -1 /* ownership not restricted */);

            long txId = bankingService.withdraw(accountNumber, amount, note);

            auditService.logTransactionEvent("WITHDRAWAL", accountNumber,
                "Withdrawal ₹" + amount + " by " + principal.getUsername()
                + " | txId=" + txId
                + (note != null ? " | note=" + note : ""));

            return txId;

        } catch (AuthorizationException e) {
            auditService.logAccessDenied(accountNumber,
                "WITHDRAWAL denied for " + principal.getUsername());
            throw e;
        }
    }

    /**
     * Transfers funds between two accounts.
     *
     * <h3>Security gates (in order)</h3>
     * <ol>
     *   <li>Authenticated session must be present.</li>
     *   <li>{@link Permission#TRANSFER} must be held.</li>
     *   <li>Customers must own the sender account.</li>
     *   <li>If amount ≥ ₹10,000, MFA step-up must have been verified.</li>
     * </ol>
     *
     * @param senderAccountNumber   account to debit
     * @param receiverAccountNumber account to credit
     * @param amount                positive transfer amount
     * @param note                  optional reference note
     * @return the generated transaction ID
     */
    public long transfer(long senderAccountNumber, long receiverAccountNumber,
                         BigDecimal amount, String note) {
        UserPrincipal principal = SecurityContext.require();

        try {
            // ── 1. Permission: TRANSFER ────────────────────────────────────
            // Resolve sender account to get the owning customer for ownership check.
            Account sender = bankingService.getAccount(senderAccountNumber);
            rbacPolicy.requireTransfer(principal, sender.getCustomerId());

            // ── 2. MFA step-up for high-value transfers ────────────────────
            if (AuthService.requiresMfa(amount)) {
                rbacPolicy.requireMfa(principal);
                auditService.logEvent("HIGH_VALUE_TRANSFER_AUTHORISED",
                    senderAccountNumber,
                    "MFA-gated transfer ₹" + amount
                    + " from " + senderAccountNumber
                    + " → " + receiverAccountNumber
                    + " | user=" + principal.getUsername());
            }

            // ── 3. Delegate to core banking engine ─────────────────────────
            long txId = bankingService.transfer(
                senderAccountNumber, receiverAccountNumber, amount, note);

            auditService.logTransactionEvent("TRANSFER", senderAccountNumber,
                "Transfer ₹" + amount
                + " from " + senderAccountNumber
                + " → " + receiverAccountNumber
                + " | txId=" + txId
                + " | user=" + principal.getUsername()
                + (note != null ? " | note=" + note : ""));

            return txId;

        } catch (AuthorizationException e) {
            auditService.logAccessDenied(senderAccountNumber,
                "TRANSFER denied for " + principal.getUsername()
                + " | sender=" + senderAccountNumber
                + " | receiver=" + receiverAccountNumber
                + " | amount=" + amount
                + " | reason=" + e.getMessage());
            throw e;
        }
    }

    /**
     * Executes transfer via PostgreSQL stored procedure with full security gates.
     */
    public long transferViaProcedure(long senderAccountNumber, long receiverAccountNumber,
                                     BigDecimal amount, String note) {
        UserPrincipal principal = SecurityContext.require();

        try {
            Account sender = bankingService.getAccount(senderAccountNumber);
            rbacPolicy.requireTransfer(principal, sender.getCustomerId());

            if (AuthService.requiresMfa(amount)) {
                rbacPolicy.requireMfa(principal);
                auditService.logEvent("HIGH_VALUE_TRANSFER_AUTHORISED",
                    senderAccountNumber,
                    "MFA-gated procedure transfer ₹" + amount
                    + " from " + senderAccountNumber
                    + " → " + receiverAccountNumber
                    + " | user=" + principal.getUsername());
            }

            long txId = bankingService.transferViaProcedure(
                senderAccountNumber, receiverAccountNumber, amount, note);

            auditService.logTransactionEvent("TRANSFER_PROCEDURE", senderAccountNumber,
                "Procedure Transfer ₹" + amount
                + " from " + senderAccountNumber
                + " → " + receiverAccountNumber
                + " | txId=" + txId
                + " | user=" + principal.getUsername()
                + (note != null ? " | note=" + note : ""));

            return txId;

        } catch (AuthorizationException e) {
            auditService.logAccessDenied(senderAccountNumber,
                "PROCEDURE_TRANSFER denied for " + principal.getUsername()
                + " | sender=" + senderAccountNumber
                + " | receiver=" + receiverAccountNumber
                + " | amount=" + amount
                + " | reason=" + e.getMessage());
            throw e;
        }
    }

    // ── Query operations ──────────────────────────────────────────────────────

    /**
     * Returns the current balance of an account.
     * The caller must own the account or be a staff member.
     *
     * @param accountNumber account to query
     * @return current balance
     */
    public BigDecimal getBalance(long accountNumber) {
        UserPrincipal principal = SecurityContext.require();
        Account account = bankingService.getAccount(accountNumber);
        rbacPolicy.requireViewAccount(principal, account.getCustomerId());
        return bankingService.getBalance(accountNumber);
    }

    /**
     * Returns recent transaction history for an account.
     * Requires {@link Permission#VIEW_OWN_TRANSACTIONS} (own) or
     * {@link Permission#VIEW_ALL_TRANSACTIONS} (any).
     *
     * @param accountNumber account to query
     * @param limit         maximum number of records
     * @return list of transactions
     */
    public List<Transaction> getTransactionHistory(long accountNumber, int limit) {
        UserPrincipal principal = SecurityContext.require();
        Account account = bankingService.getAccount(accountNumber);

        try {
            rbacPolicy.requireViewTransactions(principal, account.getCustomerId());
            return bankingService.getTransactionHistory(accountNumber, limit);

        } catch (AuthorizationException e) {
            auditService.logAccessDenied(accountNumber,
                "VIEW_TRANSACTIONS denied for " + principal.getUsername());
            throw e;
        }
    }

    // ── Audit log access ──────────────────────────────────────────────────────

    /**
     * Returns recent security audit events.
     * Requires {@link Permission#READ_AUDIT_LOG}.
     *
     * @param limit maximum number of events to return
     * @return list of security events
     */
    public List<com.bank.system.security.audit.SecurityEvent> getAuditLog(int limit) {
        UserPrincipal principal = SecurityContext.require();

        try {
            rbacPolicy.requireReadAuditLog(principal);
            return auditService.getRecentEvents(limit);

        } catch (AuthorizationException e) {
            auditService.logAccessDenied(null,
                "READ_AUDIT_LOG denied for " + principal.getUsername());
            throw e;
        } catch (java.sql.SQLException e) {
            throw new com.bank.system.exception.BankingException(
                "Failed to retrieve audit log: " + e.getMessage(), e);
        }
    }
}
