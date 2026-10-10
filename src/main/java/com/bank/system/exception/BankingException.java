package com.bank.system.exception;

/**
 * Root unchecked exception for all domain-level banking errors.
 *
 * <p>Using a checked exception hierarchy forces callers to handle errors
 * explicitly.  All sub-exceptions extend this class so service-layer callers
 * can catch the whole family with a single {@code catch (BankingException e)}.
 */
public class BankingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BankingException(String message) {
        super(message);
    }

    public BankingException(String message, Throwable cause) {
        super(message, cause);
    }
}
