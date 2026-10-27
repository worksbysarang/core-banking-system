package com.bank.system.api.controller;

import com.bank.system.api.dto.ApiResponse;
import com.bank.system.api.util.HttpUtil;
import com.bank.system.security.audit.SecurityEvent;
import com.bank.system.security.exception.AuthorizationException;
import com.bank.system.security.guard.SecureBankingService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * AuditController – exposes the security audit log to authorised staff.
 *
 * <h3>Endpoints</h3>
 * <pre>
 *   GET /api/audit              – recent audit events  (?limit=N)
 *   GET /api/audit?account={id} – events for a specific account
 *   GET /api/audit?type={type}  – events filtered by event type
 * </pre>
 *
 * <h3>Access control</h3>
 * <p>Requires {@link com.bank.system.security.Permission#READ_AUDIT_LOG},
 * which is held by {@code BRANCH_MANAGER}, {@code AUDITOR}, and
 * {@code ADMIN} only.  Any other role receives {@code 403 Forbidden}.
 */
public class AuditController implements HttpHandler {

    private static final Logger log = LoggerFactory.getLogger(AuditController.class);

    private static final int DEFAULT_LIMIT = 50;

    private final SecureBankingService secureService;

    public AuditController(SecureBankingService secureService) {
        this.secureService = secureService;
    }

    // ── Dispatch ──────────────────────────────────────────────────────────────

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase();

        try {
            if ("OPTIONS".equals(method)) { HttpUtil.handleOptions(exchange); return; }
            if (!"GET".equals(method)) {
                HttpUtil.sendJson(exchange, 405,
                    ApiResponse.error("Audit log is read-only – use GET."));
                return;
            }

            int limit = parseLimit(exchange);

            // Filter by account or type if query params are present
            String accountStr = HttpUtil.queryParam(exchange, "account");
            String typeStr    = HttpUtil.queryParam(exchange, "type");

            List<SecurityEvent> events;

            if (accountStr != null) {
                long accountNumber = Long.parseLong(accountStr);
                // Delegate to secureService (which enforces RBAC) then filter
                events = secureService.getAuditLog(limit);           // RBAC checked here
                events = events.stream()
                    .filter(e -> e.getAccountNumber() != null
                                 && e.getAccountNumber() == accountNumber)
                    .toList();
            } else {
                events = secureService.getAuditLog(limit);           // RBAC checked here
                if (typeStr != null) {
                    final String t = typeStr.toUpperCase();
                    events = events.stream()
                        .filter(e -> t.equals(e.getEventType()))
                        .toList();
                }
            }

            HttpUtil.sendJson(exchange, 200, ApiResponse.ok(Map.of(
                "count",  events.size(),
                "events", events
            )));

        } catch (AuthorizationException e) {
            HttpUtil.sendJson(exchange, 403, ApiResponse.error(e.getMessage()));
        } catch (NumberFormatException e) {
            HttpUtil.sendJson(exchange, 400,
                ApiResponse.error("'account' must be a valid account number."));
        } catch (Exception e) {
            log.error("AuditController error: {}", e.getMessage(), e);
            HttpUtil.sendJson(exchange, 500, ApiResponse.error("Internal server error."));
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private int parseLimit(HttpExchange exchange) {
        String s = HttpUtil.queryParam(exchange, "limit");
        if (s == null) return DEFAULT_LIMIT;
        try {
            int v = Integer.parseInt(s);
            return (v > 0 && v <= 500) ? v : DEFAULT_LIMIT;
        } catch (NumberFormatException e) {
            return DEFAULT_LIMIT;
        }
    }
}
