package com.bank.system.exception;

/**
 * Thrown when a requested bank account does not exist in the database.
 */
public class AccountNotFoundException extends BankingException {

    private static final long serialVersionUID = 1L;

    private final long accountNumber;

    public AccountNotFoundException(long accountNumber) {
        super("Account not found: " + accountNumber);
        this.accountNumber = accountNumber;
    }

    public AccountNotFoundException(String message, long accountNumber) {
        super(message);
        this.accountNumber = accountNumber;
    }

    /** @return the account number that was not found */
    public long getAccountNumber() {
        return accountNumber;
    }
}
