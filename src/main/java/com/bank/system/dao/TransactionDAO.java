package com.bank.system.dao;

import com.bank.system.config.DatabaseConfig;
import com.bank.system.exception.BankingException;
import com.bank.system.model.Transaction;
import com.bank.system.model.Transaction.TransactionStatus;
import com.bank.system.model.Transaction.TransactionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data-Access Object for the {@code transactions} table.
 *
 * <p>Provides methods to record individual transaction rows and to query
 * transaction history.  Fund-transfer atomicity is managed at the
 * {@link com.bank.system.service.BankingService} level (not here), so that
 * balance updates and the transaction insert share a single JDBC transaction.
 */
public class TransactionDAO {

    private static final Logger log = LoggerFactory.getLogger(TransactionDAO.class);

    // ── SQL constants ─────────────────────────────────────────────────────────

    private static final String SQL_INSERT =
        "INSERT INTO transactions " +
        "  (sender_account, receiver_account, amount, transaction_type, status, reference_note) " +
        "VALUES (?, ?, ?, ?, ?, ?) RETURNING transaction_id";

    private static final String SQL_FIND_BY_ID =
        "SELECT transaction_id, sender_account, receiver_account, amount, " +
        "       transaction_type, status, reference_note, created_at " +
        "  FROM transactions WHERE transaction_id = ?";

    private static final String SQL_HISTORY_BY_ACCOUNT =
        "SELECT transaction_id, sender_account, receiver_account, amount, " +
        "       transaction_type, status, reference_note, created_at " +
        "  FROM transactions " +
        " WHERE sender_account = ? OR receiver_account = ? " +
        " ORDER BY created_at DESC " +
        " LIMIT ?";

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Inserts a transaction record within an <em>existing</em> connection /
     * transaction managed by the caller.
     *
     * <p>Used by {@link com.bank.system.service.BankingService} when it
     * controls the JDBC transaction boundary (auto-commit disabled).
     *
     * @param conn            active connection with auto-commit disabled
     * @param senderAccount   nullable (null for DEPOSIT)
     * @param receiverAccount nullable (null for WITHDRAWAL)
     * @param amount          positive transaction amount
     * @param type            DEPOSIT, WITHDRAWAL, TRANSFER, or INTEREST
     * @param status          SUCCESS, PENDING, or FAILED
     * @param note            free-text reference note (may be null)
     * @return the generated {@code transaction_id}
     * @throws SQLException on DB error (caller handles rollback)
     */
    public long insertTransaction(Connection conn,
                                  Long senderAccount,
                                  Long receiverAccount,
                                  BigDecimal amount,
                                  TransactionType type,
                                  TransactionStatus status,
                                  String note) throws SQLException {

        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT)) {

            // sender_account – nullable
            if (senderAccount != null) ps.setLong(1, senderAccount);
            else                       ps.setNull(1, Types.BIGINT);

            // receiver_account – nullable
            if (receiverAccount != null) ps.setLong(2, receiverAccount);
            else                         ps.setNull(2, Types.BIGINT);

            ps.setBigDecimal(3, amount);
            ps.setString(4, type.name());
            ps.setString(5, status.name());

            if (note != null) ps.setString(6, note);
            else              ps.setNull(6, Types.VARCHAR);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long txId = rs.getLong(1);
                    log.debug("Transaction {} recorded (type={}, amount={}).", txId, type, amount);
                    return txId;
                }
                throw new BankingException("Transaction insert failed – no ID returned.");
            }
        }
    }

    /**
     * Looks up a transaction by its primary key.
     *
     * @param transactionId the transaction to retrieve
     * @return an {@link Optional} containing the transaction, or empty
     */
    public Optional<Transaction> findById(long transactionId) {
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_ID)) {

            ps.setLong(1, transactionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new BankingException(
                "Error fetching transaction " + transactionId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Returns the most recent transactions involving an account (as sender or
     * receiver), ordered newest-first.
     *
     * @param accountNumber the account whose history to retrieve
     * @param limit         maximum number of rows to return
     * @return list of transactions (may be empty)
     */
    public List<Transaction> getHistory(long accountNumber, int limit) {
        List<Transaction> results = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_HISTORY_BY_ACCOUNT)) {

            ps.setLong(1, accountNumber);
            ps.setLong(2, accountNumber);
            ps.setInt(3, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new BankingException(
                "Error fetching transaction history for account " + accountNumber +
                ": " + e.getMessage(), e);
        }
        return results;
    }

    /**
     * Executes the PostgreSQL stored procedure {@code process_fund_transfer}
     * via {@link CallableStatement}.
     *
     * @param conn            active connection
     * @param senderAccount   sender account
     * @param receiverAccount receiver account
     * @param amount          positive transfer amount
     * @param note            reference note
     * @throws SQLException on procedure error
     */
    public void executeTransferProcedure(Connection conn,
                                         long senderAccount,
                                         long receiverAccount,
                                         BigDecimal amount,
                                         String note) throws SQLException {
        try (CallableStatement cs = conn.prepareCall("CALL process_fund_transfer(?, ?, ?, ?)")) {
            cs.setLong(1, senderAccount);
            cs.setLong(2, receiverAccount);
            cs.setBigDecimal(3, amount);
            if (note != null) cs.setString(4, note);
            else              cs.setNull(4, Types.VARCHAR);
            cs.execute();
        }
    }

    // ── Row mapper ────────────────────────────────────────────────────────────

    /**
     * Maps the current row of a {@link ResultSet} to a {@link Transaction}.
     */
    private Transaction mapRow(ResultSet rs) throws SQLException {
        Transaction tx = new Transaction();
        tx.setTransactionId(rs.getLong("transaction_id"));

        // Handle nullable sender/receiver
        long sender = rs.getLong("sender_account");
        tx.setSenderAccount(rs.wasNull() ? null : sender);

        long receiver = rs.getLong("receiver_account");
        tx.setReceiverAccount(rs.wasNull() ? null : receiver);

        tx.setAmount(rs.getBigDecimal("amount"));
        tx.setTransactionType(TransactionType.valueOf(rs.getString("transaction_type")));
        tx.setStatus(TransactionStatus.valueOf(rs.getString("status")));
        tx.setReferenceNote(rs.getString("reference_note"));

        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) tx.setCreatedAt(ts.toLocalDateTime());

        return tx;
    }
}
