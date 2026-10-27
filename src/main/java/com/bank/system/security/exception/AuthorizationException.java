package com.bank.system.security.exception;

import com.bank.system.security.Permission;

/**
 * AuthorizationException – thrown when an authenticated user attempts an
 * operation for which they do not hold the required {@link Permission}.
 *
 * <p>Intentionally unchecked so it can propagate through the service layer
 * without forcing every caller to add a {@code throws} clause.
 */
public class AuthorizationException extends RuntimeException {

    /** The permission that was required but not held. */
    private final Permission requiredPermission;

    public AuthorizationException(String message, Permission requiredPermission) {
        super(message);
        this.requiredPermission = requiredPermission;
    }

    public AuthorizationException(String message) {
        super(message);
        this.requiredPermission = null;
    }

    /**
     * Returns the permission that triggered this exception, or {@code null}
     * if the check was not permission-specific.
     */
    public Permission getRequiredPermission() {
        return requiredPermission;
    }
}
