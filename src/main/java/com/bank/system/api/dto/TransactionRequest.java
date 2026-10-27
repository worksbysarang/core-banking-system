package com.bank.system.api.dto;

import java.math.BigDecimal;

/**
 * TransactionRequest – JSON body for deposit, withdrawal, and transfer endpoints.
 *
 * <p>Deposit / Withdrawal – {@code POST /api/transactions/deposit|withdraw}:
 * <pre>
 * {
 *   "accountNumber": 1001,
 *   "amount": 500.00,
 *   "note": "Salary"
 * }
 * </pre>
 *
 * <p>Transfer – {@code POST /api/transactions/transfer}:
 * <pre>
 * {
 *   "senderAccountNumber": 1001,
 *   "receiverAccountNumber": 1002,
 *   "amount": 250.00,
 *   "note": "Rent"
 * }
 * </pre>
 */
public class TransactionRequest {
    public long       accountNumber;          // for deposit / withdrawal
    public long       senderAccountNumber;    // for transfer
    public long       receiverAccountNumber;  // for transfer
    public BigDecimal amount;
    public String     note;
    public Boolean    useStoredProcedure; // optional flag to invoke PostgreSQL stored procedure
}
