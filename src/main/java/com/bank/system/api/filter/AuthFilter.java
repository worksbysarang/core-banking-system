package com.bank.system.api.filter;

import com.bank.system.api.dto.ApiResponse;
import com.bank.system.api.session.SessionStore;
import com.bank.system.api.util.HttpUtil;
import com.bank.system.security.SecurityContext;
import com.bank.system.security.UserPrincipal;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

/**
 * AuthFilter – wraps any {@link HttpHandler} to enforce authentication.
 *
 * <h3>How it works</h3>
 * <ol>
 *   <li>OPTIONS requests are passed through immediately (CORS pre-flight).</li>
 *   <li>The {@code Authorization: Bearer <token>} header is extracted.</li>
 *   <li>The token is resolved to a {@link UserPrincipal} via
 *       {@link SessionStore}.</li>
 *   <li>If found, the principal is bound to {@link SecurityContext} and the
 *       inner handler is invoked.</li>
 *   <li>If missing/expired, a {@code 401 Unauthorized} JSON response is
 *       returned immediately and the inner handler is never called.</li>
 *   <li>{@link SecurityContext#clear()} is called in a {@code finally} block
 *       so the thread-local is always cleaned up, regardless of exceptions.</li>
 * </ol>
 *
 * <h3>Usage</h3>
 * <pre>
 *   server.createContext("/api/accounts", new AuthFilter(new AccountController(...)));
 * </pre>
 */
public class AuthFilter implements HttpHandler {

    private final HttpHandler inner;

    /**
     * Wraps the given handler with authentication enforcement.
     *
     * @param inner the protected handler to delegate to on success
     */
    public AuthFilter(HttpHandler inner) {
        this.inner = inner;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // ── CORS pre-flight – pass through without auth check ─────────────────
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpUtil.handleOptions(exchange);
            return;
        }

        // ── Extract Bearer token ──────────────────────────────────────────────
        String token = HttpUtil.bearerToken(exchange);
        UserPrincipal principal = SessionStore.getInstance().get(token);

        if (principal == null) {
            HttpUtil.sendJson(exchange, 401,
                ApiResponse.error("Unauthenticated – please log in first. "
                    + "Include 'Authorization: Bearer <token>' header."));
            return;
        }

        // ── Bind principal to this thread ─────────────────────────────────────
        SecurityContext.set(principal);
        try {
            inner.handle(exchange);
        } finally {
            SecurityContext.clear();   // critical: always clean up thread-local
        }
    }
}
