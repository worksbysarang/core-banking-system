package com.bank.system.security.exception;

/**
 * AuthenticationException – thrown when a user fails to authenticate or
 * when an operation is attempted without a valid session.
 *
 * <p>This is an unchecked exception so callers are not forced to declare it,
 * while still carrying full stack-trace information.
 */
public class AuthenticationException extends RuntimeException {

    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
