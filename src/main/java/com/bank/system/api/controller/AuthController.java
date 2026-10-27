package com.bank.system.api.controller;

import com.bank.system.api.dto.ApiResponse;
import com.bank.system.api.dto.LoginRequest;
import com.bank.system.api.dto.MfaRequest;
import com.bank.system.api.session.SessionStore;
import com.bank.system.api.util.HttpUtil;
import com.bank.system.security.SecurityContext;
import com.bank.system.security.UserPrincipal;
import com.bank.system.security.auth.AuthService;
import com.bank.system.security.exception.AuthenticationException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AuthController – handles authentication endpoints.
 *
 * <h3>Endpoints</h3>
 * <pre>
 *   POST /api/auth/login    – authenticate and receive a Bearer token
 *   POST /api/auth/logout   – invalidate the current session
 *   POST /api/auth/mfa      – complete MFA step-up for the current session
 *   GET  /api/auth/me       – return the current principal's details
 * </pre>
 *
 * <p><strong>Login</strong> does not require the {@link com.bank.system.api.filter.AuthFilter}
 * because no token exists yet.  All other endpoints go through it.
 */
public class AuthController implements HttpHandler {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService    authService;
    private final SessionStore   sessionStore;

    // ── Constructor ───────────────────────────────────────────────────────────

    public AuthController(AuthService authService) {
        this.authService  = authService;
        this.sessionStore = SessionStore.getInstance();
    }

    // ── HttpHandler dispatch ──────────────────────────────────────────────────

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase();
        String path   = exchange.getRequestURI().getPath();

        try {
            if ("OPTIONS".equals(method)) { HttpUtil.handleOptions(exchange); return; }

            switch (path) {
                case "/api/auth/login"  -> handleLogin(exchange, method);
                case "/api/auth/logout" -> handleLogout(exchange, method);
                case "/api/auth/mfa"    -> handleMfa(exchange, method);
                case "/api/auth/me"     -> handleMe(exchange, method);
                default -> HttpUtil.sendJson(exchange, 404,
                               ApiResponse.error("Auth endpoint not found: " + path));
            }
        } catch (AuthenticationException e) {
            HttpUtil.sendJson(exchange, 401, ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("AuthController error: {}", e.getMessage(), e);
            HttpUtil.sendJson(exchange, 500, ApiResponse.error("Internal server error."));
        }
    }

    // ── POST /api/auth/login ──────────────────────────────────────────────────

    /**
     * Authenticates the user and returns a Bearer token.
     *
     * <p>Request body:
     * <pre>{ "username": "alice", "password": "MySecret@1" }</pre>
     *
     * <p>Response (200):
     * <pre>{ "success": true, "data": { "token": "...", "role": "CUSTOMER", "username": "alice" } }</pre>
     */
    private void handleLogin(HttpExchange exchange, String method) throws IOException {
        if (!"POST".equals(method)) {
            HttpUtil.sendJson(exchange, 405, ApiResponse.error("Method not allowed – use POST."));
            return;
        }

        LoginRequest req = HttpUtil.readJson(exchange, LoginRequest.class);
        if (req == null || req.username == null || req.password == null) {
            HttpUtil.sendJson(exchange, 400,
                ApiResponse.error("Request body must include 'username' and 'password'."));
            return;
        }

        // AuthService.login() sets the SecurityContext but we also need a token
        // for subsequent stateless HTTP requests – so we create a session entry.
        UserPrincipal principal = authService.login(req.username, req.password);
        String token = sessionStore.create(principal);

        // Clear the thread-local set by AuthService (AuthFilter will re-bind on
        // subsequent requests; for the login response itself we don't need it)
        SecurityContext.clear();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("token", token);
        payload.put("username", principal.getUsername());
        payload.put("role", principal.getRole().name());
        payload.put("roleLabel", principal.getRole().getLabel());
        payload.put("customerId", principal.getCustomerId());
        payload.put("permissions", principal.getPermissions().stream()
            .map(Enum::name).toList());
        payload.put("mfaVerified", principal.isMfaVerified());
        HttpUtil.sendJson(exchange, 200, ApiResponse.ok(payload));
        log.info("Login successful for '{}'", req.username);
    }

    // ── POST /api/auth/logout ─────────────────────────────────────────────────

    /**
     * Invalidates the current Bearer token.
     * Requires {@code Authorization: Bearer <token>} header.
     */
    private void handleLogout(HttpExchange exchange, String method) throws IOException {
        if (!"POST".equals(method)) {
            HttpUtil.sendJson(exchange, 405, ApiResponse.error("Method not allowed."));
            return;
        }
        String token = HttpUtil.bearerToken(exchange);
        sessionStore.invalidate(token);
        authService.logout();   // logs the audit event
        HttpUtil.sendJson(exchange, 200, ApiResponse.ok(Map.of("message", "Logged out.")));
    }

    // ── POST /api/auth/mfa ────────────────────────────────────────────────────

    /**
     * Verifies the MFA code for the currently authenticated session.
     *
     * <p>Request body: {@code { "code": "123456" }}
     *
     * <p>After success, the principal's MFA flag is set to {@code true} for
     * the remainder of the session – high-value transfers will now be allowed.
     */
    private void handleMfa(HttpExchange exchange, String method) throws IOException {
        if (!"POST".equals(method)) {
            HttpUtil.sendJson(exchange, 405, ApiResponse.error("Method not allowed."));
            return;
        }
        MfaRequest req = HttpUtil.readJson(exchange, MfaRequest.class);
        if (req == null || req.code == null) {
            HttpUtil.sendJson(exchange, 400, ApiResponse.error("'code' field is required."));
            return;
        }
        authService.verifyMfa(req.code);
        HttpUtil.sendJson(exchange, 200,
            ApiResponse.ok(Map.of("message", "MFA verified. High-value operations unlocked.")));
    }

    // ── GET /api/auth/me ──────────────────────────────────────────────────────

    /** Returns the current session's principal information. */
    private void handleMe(HttpExchange exchange, String method) throws IOException {
        if (!"GET".equals(method)) {
            HttpUtil.sendJson(exchange, 405, ApiResponse.error("Method not allowed."));
            return;
        }
        UserPrincipal principal = SecurityContext.require();
        Map<String, Object> me = new LinkedHashMap<>();
        me.put("userId", principal.getUserId());
        me.put("username", principal.getUsername());
        me.put("role", principal.getRole().name());
        me.put("roleLabel", principal.getRole().getLabel());
        me.put("customerId", principal.getCustomerId());
        me.put("permissions", principal.getPermissions().stream()
            .map(Enum::name).toList());
        me.put("mfaVerified", principal.isMfaVerified());
        me.put("loginTime", principal.getLoginTime().toString());
        HttpUtil.sendJson(exchange, 200, ApiResponse.ok(me));
    }
}
