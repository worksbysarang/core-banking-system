package com.bank.system.service;

import com.bank.system.config.DatabaseConfig;
import com.bank.system.dao.AccountDAO;
import com.bank.system.dao.BranchDAO;
import com.bank.system.dao.CustomerDAO;
import com.bank.system.dao.TransactionDAO;
import com.bank.system.exception.AccountNotFoundException;
import com.bank.system.exception.BankingException;
import com.bank.system.exception.InsufficientBalanceException;
import com.bank.system.model.Account;
import com.bank.system.model.Account.AccountStatus;
import com.bank.system.model.Account.AccountType;
import com.bank.system.model.Branch;
import com.bank.system.model.Customer;
import com.bank.system.model.Transaction;
import com.bank.system.model.Transaction.TransactionStatus;
import com.bank.system.model.Transaction.TransactionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * BankingService – the core business-logic layer.
 *
 * <p>This class is the primary entry point for Member 4 (UI / Controller) to
 * perform banking operations.  It enforces all business rules, manages JDBC
 * transaction boundaries, and delegates persistence to the DAO layer.
 *
 * <h3>Transaction safety</h3>
 * <p>For operations that touch multiple rows (deposit, withdrawal, transfer),
 * this service:
 * <ol>
 *   <li>Borrows a {@link Connection} from the injected {@link DataSource}.</li>
 *   <li>Disables auto-commit.</li>
 *   <li>Performs all updates inside a try block.</li>
 *   <li>Commits on success or rolls back and rethrows on any failure.</li>
 * </ol>
 *
 * <h3>Thread safety</h3>
 * <p>The service is stateless and safe to share across threads.
 */
public class BankingService {

    private static final Logger log = LoggerFactory.getLogger(BankingService.class);

    private final AccountDAO         accountDAO;
    private final TransactionDAO     transactionDAO;
    private final CustomerDAO        customerDAO;
    private final BranchDAO          branchDAO;

    /**
     * Functional interface that supplies a JDBC {@link Connection}.
     * Declared here so that method references like
     * {@code DatabaseConfig.getInstance()::getConnection} compile correctly
     * (they throw checked {@link SQLException}).
     */
    @FunctionalInterface
    public interface ConnectionSupplier {
        Connection get() throws SQLException;
    }

    /** Supplies connections for multi-step (multi-row) transactions. */
    private final ConnectionSupplier connectionSupplier;

    // ── Constructors ──────────────────────────────────────────────────────────

    /**
     * Production constructor – uses the HikariCP pool managed by
     * {@link DatabaseConfig}.
     */
    public BankingService() {
        this(new AccountDAO(), new TransactionDAO(),
             DatabaseConfig.getInstance()::getConnection,
             new CustomerDAO(), new BranchDAO());
    }

    /**
     * Backward-compatible DI constructor – pass mock DAOs and connection supplier.
     */
    public BankingService(AccountDAO accountDAO, TransactionDAO transactionDAO,
                          ConnectionSupplier connectionSupplier) {
        this(accountDAO, transactionDAO, connectionSupplier,
             new CustomerDAO(), new BranchDAO());
    }

    /**
     * Full DI constructor for unit testing with all DAOs.
     */
    public BankingService(AccountDAO accountDAO, TransactionDAO transactionDAO,
                          ConnectionSupplier connectionSupplier,
                          CustomerDAO customerDAO, BranchDAO branchDAO) {
        this.accountDAO         = accountDAO;
        this.transactionDAO     = transactionDAO;
        this.connectionSupplier = connectionSupplier;
        this.customerDAO        = customerDAO;
        this.branchDAO          = branchDAO;
    }

    /** Convenience constructor for backward compatibility. */
    BankingService(AccountDAO accountDAO, TransactionDAO transactionDAO) {
        this(accountDAO, transactionDAO,
             DatabaseConfig.getInstance()::getConnection);
    }

    // ── Account management ────────────────────────────────────────────────────

    /**
     * Opens a new bank account.
     *
     * @param customerId     owner's customer ID
     * @param branchId       branch where the account is opened
     * @param accountType    SAVINGS, CHECKING, or LOAN
     * @param initialBalance opening deposit (must be ≥ 0)
     * @return the new account number
     * @throws BankingException if the initial balance is negative
     */
    public long openAccount(int customerId, int branchId,
                            AccountType accountType, BigDecimal initialBalance) {
        if (initialBalance == null || initialBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new BankingException("Initial balance must be zero or positive.");
        }
        return accountDAO.createAccount(customerId, branchId, accountType, initialBalance);
    }

    /**
     * Returns an account or throws {@link AccountNotFoundException}.
     *
     * @param accountNumber account to look up
     * @return the {@link Account}
     */
    public Account getAccount(long accountNumber) {
        return accountDAO.findById(accountNumber)
            .orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }

    /**
     * Returns all accounts for a customer.
     */
    public List<Account> getAccountsByCustomer(int customerId) {
        return accountDAO.findByCustomerId(customerId);
    }

    /**
     * Returns all accounts up to the specified limit.
     */
    public List<Account> getAllAccounts(int limit) {
        return accountDAO.findAll(limit);
    }

    public List<Account> getAllAccounts() {
        return accountDAO.findAll();
    }

    /**
     * Returns all customers.
     */
    public List<Customer> getAllCustomers() {
        return customerDAO.findAll();
    }

    /**
     * Returns a customer by ID.
     */
    public Optional<Customer> getCustomer(int customerId) {
        return customerDAO.findById(customerId);
    }

    /**
     * Creates a new customer record.
     */
    public int createCustomer(String firstName, String lastName, String email,
                              String phone, String nationalId, String address) {
        if (firstName == null || firstName.isBlank() || lastName == null || lastName.isBlank()) {
            throw new BankingException("First name and last name are required.");
        }
        if (email == null || email.isBlank()) {
            throw new BankingException("Email is required.");
        }
        if (phone == null || phone.isBlank()) {
            throw new BankingException("Phone number is required.");
        }
        if (nationalId == null || nationalId.isBlank()) {
            throw new BankingException("National ID is required.");
        }
        return customerDAO.createCustomer(firstName, lastName, email, phone, nationalId, address);
    }

    /**
     * Returns all branches.
     */
    public List<Branch> getAllBranches() {
        return branchDAO.findAll();
    }

    /**
     * Returns a branch by ID.
     */
    public Optional<Branch> getBranch(int branchId) {
        return branchDAO.findById(branchId);
    }

    /**
     * Freezes or closes an account.  Active accounts can be frozen or closed;
     * frozen accounts can be reactivated or closed; closed accounts are final.
     *
     * @param accountNumber account to update
     * @param newStatus     the desired new status
     */
    public void updateAccountStatus(long accountNumber, AccountStatus newStatus) {
        Account acc = getAccount(accountNumber);
        if (acc.getStatus() == AccountStatus.CLOSED) {
            throw new BankingException("Cannot change the status of a CLOSED account.");
        }
        accountDAO.updateStatus(accountNumber, newStatus);
        log.info("Account {} status updated → {}", accountNumber, newStatus);
    }

    // ── Core banking operations ───────────────────────────────────────────────

    /**
     * Deposits a positive amount into an account.
     *
     * @param accountNumber destination account
     * @param amount        amount to deposit (must be &gt; 0)
     * @param note          optional reference note
     * @return the generated transaction ID
     */
    public long deposit(long accountNumber, BigDecimal amount, String note) {
        validatePositiveAmount(amount);

        try (Connection conn = connectionSupplier.get()) {
            conn.setAutoCommit(false);
            try {
                Account acc = requireActive(conn, accountNumber);

                BigDecimal newBalance = acc.getBalance().add(amount);
                accountDAO.updateBalance(conn, accountNumber, newBalance);

                long txId = transactionDAO.insertTransaction(
                    conn, null, accountNumber, amount,
                    TransactionType.DEPOSIT, TransactionStatus.SUCCESS, note);

                conn.commit();
                log.info("Deposit ₹{} → account {}, txId={}", amount, accountNumber, txId);
                return txId;

            } catch (Exception e) {
                safeRollback(conn);
                throw toServiceException("Deposit failed", e);
            }
        } catch (SQLException e) {
            throw new BankingException("Deposit failed (connection error): " + e.getMessage(), e);
        }
    }

    /**
     * Withdraws a positive amount from an account.
     *
     * @param accountNumber source account
     * @param amount        amount to withdraw (must be &gt; 0)
     * @param note          optional reference note
     * @return the generated transaction ID
     * @throws InsufficientBalanceException if the account has insufficient funds
     */
    public long withdraw(long accountNumber, BigDecimal amount, String note) {
        validatePositiveAmount(amount);

        try (Connection conn = connectionSupplier.get()) {
            conn.setAutoCommit(false);
            try {
                Account acc = requireActive(conn, accountNumber);

                if (acc.getBalance().compareTo(amount) < 0) {
                    throw new InsufficientBalanceException(
                        accountNumber, acc.getBalance(), amount);
                }

                BigDecimal newBalance = acc.getBalance().subtract(amount);
                accountDAO.updateBalance(conn, accountNumber, newBalance);

                long txId = transactionDAO.insertTransaction(
                    conn, accountNumber, null, amount,
                    TransactionType.WITHDRAWAL, TransactionStatus.SUCCESS, note);

                conn.commit();
                log.info("Withdrawal ₹{} ← account {}, txId={}", amount, accountNumber, txId);
                return txId;

            } catch (Exception e) {
                safeRollback(conn);
                throw toServiceException("Withdrawal failed", e);
            }
        } catch (SQLException e) {
            throw new BankingException("Withdrawal failed (connection error): " + e.getMessage(), e);
        }
    }

    /**
     * Transfers funds atomically between two accounts.
     *
     * <p>Accounts are locked in ascending {@code account_number} order to
     * prevent deadlocks when concurrent transfers occur between the same pair
     * of accounts in opposite directions.
     *
     * @param senderAccountNumber   account to debit
     * @param receiverAccountNumber account to credit
     * @param amount                positive transfer amount
     * @param note                  optional reference note
     * @return the generated transaction ID
     * @throws InsufficientBalanceException if the sender has insufficient funds
     * @throws BankingException             if accounts are the same or inactive
     */
    public long transfer(long senderAccountNumber, long receiverAccountNumber,
                         BigDecimal amount, String note) {

        validatePositiveAmount(amount);

        if (senderAccountNumber == receiverAccountNumber) {
            throw new BankingException(
                "Sender and receiver accounts must be different.");
        }

        try (Connection conn = connectionSupplier.get()) {
            conn.setAutoCommit(false);
            try {
                // Lock accounts in ascending order (deadlock prevention)
                long first  = Math.min(senderAccountNumber, receiverAccountNumber);
                long second = Math.max(senderAccountNumber, receiverAccountNumber);

                Account firstAcc  = requireActive(conn, first);
                Account secondAcc = requireActive(conn, second);

                Account sender   = (first == senderAccountNumber)   ? firstAcc  : secondAcc;
                Account receiver = (first == receiverAccountNumber)  ? firstAcc  : secondAcc;

                if (sender.getBalance().compareTo(amount) < 0) {
                    throw new InsufficientBalanceException(
                        senderAccountNumber, sender.getBalance(), amount);
                }

                accountDAO.updateBalance(conn, senderAccountNumber,
                    sender.getBalance().subtract(amount));
                accountDAO.updateBalance(conn, receiverAccountNumber,
                    receiver.getBalance().add(amount));

                long txId = transactionDAO.insertTransaction(
                    conn, senderAccountNumber, receiverAccountNumber, amount,
                    TransactionType.TRANSFER, TransactionStatus.SUCCESS, note);

                conn.commit();
                log.info("Transfer ₹{} : {} → {}, txId={}",
                         amount, senderAccountNumber, receiverAccountNumber, txId);
                return txId;

            } catch (Exception e) {
                safeRollback(conn);
                throw toServiceException("Transfer failed", e);
            }
        } catch (SQLException e) {
            throw new BankingException("Transfer failed (connection error): " + e.getMessage(), e);
        }
    }

    /**
     * Transfers funds atomically using the PostgreSQL stored procedure
     * {@code process_fund_transfer}.
     *
     * @param senderAccountNumber   account to debit
     * @param receiverAccountNumber account to credit
     * @param amount                positive transfer amount
     * @param note                  optional reference note
     * @return the generated transaction ID
     */
    public long transferViaProcedure(long senderAccountNumber, long receiverAccountNumber,
                                     BigDecimal amount, String note) {
        validatePositiveAmount(amount);
        if (senderAccountNumber == receiverAccountNumber) {
            throw new BankingException("Sender and receiver accounts must be different.");
        }

        try (Connection conn = connectionSupplier.get()) {
            conn.setAutoCommit(false);
            try {
                transactionDAO.executeTransferProcedure(
                    conn, senderAccountNumber, receiverAccountNumber, amount, note);
                conn.commit();
                log.info("Procedure Transfer ₹{} : {} → {} completed.",
                         amount, senderAccountNumber, receiverAccountNumber);

                List<Transaction> hist = transactionDAO.getHistory(senderAccountNumber, 1);
                return hist.isEmpty() ? 0L : hist.get(0).getTransactionId();
            } catch (Exception e) {
                safeRollback(conn);
                throw toServiceException("Procedure transfer failed", e);
            }
        } catch (SQLException e) {
            throw new BankingException("Procedure transfer failed (connection error): " + e.getMessage(), e);
        }
    }

    // ── Query operations ──────────────────────────────────────────────────────

    /**
     * Returns the current balance of an account.
     *
     * @param accountNumber account to query
     * @return current balance
     */
    public BigDecimal getBalance(long accountNumber) {
        return accountDAO.getBalance(accountNumber);
    }

    /**
     * Returns the most recent transactions for an account, newest first.
     *
     * @param accountNumber account to query
     * @param limit         maximum number of records (e.g. 50)
     * @return list of transactions
     */
    public List<Transaction> getTransactionHistory(long accountNumber, int limit) {
        if (limit <= 0) throw new BankingException("Limit must be positive.");
        return transactionDAO.getHistory(accountNumber, limit);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Fetches an account by ID within the given connection (so we share the
     * transaction) and verifies it is ACTIVE.
     */
    private Account requireActive(Connection conn, long accountNumber) throws SQLException {
        Account acc = accountDAO.findById(conn, accountNumber)
            .orElseThrow(() -> new AccountNotFoundException(accountNumber));

        if (acc.getStatus() != AccountStatus.ACTIVE) {
            throw new BankingException(
                "Account " + accountNumber + " is not ACTIVE (status=" + acc.getStatus() + ").");
        }
        return acc;
    }

    /** Validates that an amount is strictly positive. */
    private void validatePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BankingException("Amount must be greater than zero.");
        }
    }

    /** Rolls back without swallowing the original exception. */
    private void safeRollback(Connection conn) {
        try {
            conn.rollback();
            log.warn("Transaction rolled back.");
        } catch (SQLException ex) {
            log.error("Rollback failed: {}", ex.getMessage(), ex);
        }
    }

    /**
     * Re-wraps or re-throws the exception from the inner try-block.
     * BankingExceptions (including sub-types) are rethrown as-is;
     * everything else is wrapped in a BankingException.
     */
    private BankingException toServiceException(String prefix, Exception e) {
        if (e instanceof BankingException be) {
            return be;              // preserve original typed exception
        }
        return new BankingException(prefix + ": " + e.getMessage(), e);
    }
}
