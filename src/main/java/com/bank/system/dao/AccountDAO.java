package com.bank.system.dao;

import com.bank.system.config.DatabaseConfig;
import com.bank.system.exception.AccountNotFoundException;
import com.bank.system.exception.BankingException;
import com.bank.system.model.Account;
import com.bank.system.model.Account.AccountStatus;
import com.bank.system.model.Account.AccountType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data-Access Object for the {@code accounts} table.
 *
 * <p>All SQL uses {@link PreparedStatement} to prevent SQL injection.
 * Every JDBC resource is opened inside a try-with-resources block so
 * connections are always returned to the HikariCP pool.
 */
public class AccountDAO {

    private static final Logger log = LoggerFactory.getLogger(AccountDAO.class);

    // ── SQL constants ─────────────────────────────────────────────────────────

    private static final String SQL_FIND_BY_ID =
        "SELECT account_number, customer_id, branch_id, account_type, " +
        "       balance, status, created_at " +
        "  FROM accounts WHERE account_number = ?";

    private static final String SQL_FIND_BY_CUSTOMER =
        "SELECT account_number, customer_id, branch_id, account_type, " +
        "       balance, status, created_at " +
        "  FROM accounts WHERE customer_id = ?";

    private static final String SQL_INSERT =
        "INSERT INTO accounts (customer_id, branch_id, account_type, balance, status) " +
        "VALUES (?, ?, ?, ?, ?) RETURNING account_number";

    private static final String SQL_UPDATE_BALANCE =
        "UPDATE accounts SET balance = ? WHERE account_number = ?";

    private static final String SQL_UPDATE_STATUS =
        "UPDATE accounts SET status = ? WHERE account_number = ?";

    private static final String SQL_GET_BALANCE =
        "SELECT balance FROM accounts WHERE account_number = ?";

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Creates a new account and returns the generated account number.
     *
     * @param customerId  owner's customer ID
     * @param branchId    branch where the account is opened
     * @param accountType SAVINGS, CHECKING, or LOAN
     * @param initialBalance opening balance (must be ≥ 0)
     * @return the newly generated {@code account_number}
     * @throws BankingException on DB error
     */
    public long createAccount(int customerId, int branchId,
                              AccountType accountType, BigDecimal initialBalance) {
        log.info("Creating {} account for customer {} at branch {}",
                 accountType, customerId, branchId);

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT)) {

            ps.setInt(1, customerId);
            ps.setInt(2, branchId);
            ps.setString(3, accountType.name());
            ps.setBigDecimal(4, initialBalance);
            ps.setString(5, AccountStatus.ACTIVE.name());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long accountNumber = rs.getLong(1);
                    log.info("Account {} created successfully.", accountNumber);
                    return accountNumber;
                }
                throw new BankingException("Account creation failed – no ID returned.");
            }
        } catch (SQLException e) {
            throw new BankingException("Failed to create account: " + e.getMessage(), e);
        }
    }

    /**
     * Finds an account by its primary key.
     *
     * @param accountNumber the account to look up
     * @return an {@link Optional} containing the account, or empty if not found
     * @throws BankingException on DB error
     */
    public Optional<Account> findById(long accountNumber) {
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_ID)) {

            ps.setLong(1, accountNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new BankingException(
                "Error fetching account " + accountNumber + ": " + e.getMessage(), e);
        }
    }

    /**
     * Returns all accounts belonging to a customer.
     *
     * @param customerId the customer whose accounts to retrieve
     * @return a list of accounts (may be empty)
     */
    public List<Account> findByCustomerId(int customerId) {
        List<Account> results = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_CUSTOMER)) {

            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new BankingException(
                "Error fetching accounts for customer " + customerId + ": " + e.getMessage(), e);
        }
        return results;
    }

    /**
     * Returns the current balance of an account.
     *
     * @param accountNumber account to query
     * @return current balance
     * @throws AccountNotFoundException if the account does not exist
     */
    public BigDecimal getBalance(long accountNumber) {
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_GET_BALANCE)) {

            ps.setLong(1, accountNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("balance");
                }
                throw new AccountNotFoundException(accountNumber);
            }
        } catch (SQLException e) {
            throw new BankingException(
                "Error fetching balance for account " + accountNumber + ": " + e.getMessage(), e);
        }
    }

    /**
     * Updates the balance of an account within an existing connection/transaction.
     *
     * <p>This overload is used by {@link com.bank.system.service.BankingService}
     * when it manages the JDBC transaction itself (auto-commit disabled).
     *
     * @param conn          active connection (auto-commit must be {@code false})
     * @param accountNumber account to update
     * @param newBalance    new balance to set
     * @throws SQLException on DB error (caller handles rollback)
     */
    public void updateBalance(Connection conn, long accountNumber, BigDecimal newBalance)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_BALANCE)) {
            ps.setBigDecimal(1, newBalance);
            ps.setLong(2, accountNumber);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new AccountNotFoundException(accountNumber);
            }
        }
    }

    /**
     * Updates the status (ACTIVE / FROZEN / CLOSED) of an account.
     *
     * @param accountNumber account to update
     * @param newStatus     the new status
     * @throws AccountNotFoundException if the account does not exist
     */
    public void updateStatus(long accountNumber, AccountStatus newStatus) {
        log.info("Updating status of account {} to {}", accountNumber, newStatus);

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_STATUS)) {

            ps.setString(1, newStatus.name());
            ps.setLong(2, accountNumber);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new AccountNotFoundException(accountNumber);
            }
            log.info("Account {} status updated to {}.", accountNumber, newStatus);
        } catch (SQLException e) {
            throw new BankingException(
                "Error updating status for account " + accountNumber + ": " + e.getMessage(), e);
        }
    }

    // ── Row mapper ────────────────────────────────────────────────────────────

    /**
     * Maps the current row of a {@link ResultSet} to an {@link Account} object.
     */
    private Account mapRow(ResultSet rs) throws SQLException {
        Account acc = new Account();
        acc.setAccountNumber(rs.getLong("account_number"));
        acc.setCustomerId(rs.getInt("customer_id"));
        acc.setBranchId(rs.getInt("branch_id"));
        acc.setAccountType(AccountType.valueOf(rs.getString("account_type")));
        acc.setBalance(rs.getBigDecimal("balance"));
        acc.setStatus(AccountStatus.valueOf(rs.getString("status")));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) acc.setCreatedAt(ts.toLocalDateTime());
        return acc;
    }
}
