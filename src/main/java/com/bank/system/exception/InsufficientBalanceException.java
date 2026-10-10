package com.bank.system.exception;

import java.math.BigDecimal;

/**
 * Thrown when a debit operation would take an account below zero balance.
 */
public class InsufficientBalanceException extends BankingException {

    private static final long serialVersionUID = 1L;

    private final long accountNumber;
    private final BigDecimal currentBalance;
    private final BigDecimal requestedAmount;

    public InsufficientBalanceException(long accountNumber,
                                        BigDecimal currentBalance,
                                        BigDecimal requestedAmount) {
        super(String.format(
            "Insufficient balance in account %d. Available: %.2f, Requested: %.2f",
            accountNumber, currentBalance, requestedAmount));
        this.accountNumber    = accountNumber;
        this.currentBalance   = currentBalance;
        this.requestedAmount  = requestedAmount;
    }

    public long getAccountNumber()        { return accountNumber; }
    public BigDecimal getCurrentBalance() { return currentBalance; }
    public BigDecimal getRequestedAmount(){ return requestedAmount; }
}
