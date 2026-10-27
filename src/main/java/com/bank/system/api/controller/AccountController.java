package com.bank.system.api.controller;

import com.bank.system.api.dto.ApiResponse;
import com.bank.system.api.dto.OpenAccountRequest;
import com.bank.system.api.dto.StatusUpdateRequest;
import com.bank.system.api.util.HttpUtil;
import com.bank.system.model.Account;
import com.bank.system.model.Account.AccountStatus;
import com.bank.system.model.Account.AccountType;
import com.bank.system.security.SecurityContext;
import com.bank.system.security.exception.AuthorizationException;
import com.bank.system.security.guard.SecureBankingService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * AccountController – manages bank account lifecycle.
 *
 * <h3>Endpoints</h3>
 * <pre>
 *   POST  /api/accounts                    – open a new account
 *   GET   /api/accounts/{accountNumber}    – view account details
 *   GET   /api/accounts?customerId={id}    – list accounts for a customer
 *   PATCH /api/accounts/{accountNumber}/status – freeze / close an account
 *   GET   /api/accounts/{accountNumber}/balance – current balance
 * </pre>
 *
 * <p>All endpoints are protected by
 * {@link com.bank.system.api.filter.AuthFilter} – a valid Bearer token is
 * required.  RBAC checks are enforced inside
 * {@link SecureBankingService}.
 */
public class AccountController implements HttpHandler {

    private static final Logger log = LoggerFactory.getLogger(AccountController.class);

    private final SecureBankingService secureService;

    public AccountController(SecureBankingService secureService) {
        this.secureService = secureService;
    }

    // ── Dispatch ──────────────────────────────────────────────────────────────

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase();
        String path   = exchange.getRequestURI().getPath();   // e.g. /api/accounts/1001/status

        try {
            if ("OPTIONS".equals(method)) { HttpUtil.handleOptions(exchange); return; }

            String[] segments = path.split("/");
            // segments: ["", "api", "accounts", <id?>, <sub?>]

            if (segments.length == 3) {
                // /api/accounts
                switch (method) {
                    case "POST" -> handleOpenAccount(exchange);
                    case "GET"  -> handleListAccounts(exchange);
                    default -> HttpUtil.sendJson(exchange, 405,
                                   ApiResponse.error("Method not allowed."));
                }

            } else if (segments.length == 4) {
                // /api/accounts/{id}
                long accountNumber = Long.parseLong(segments[3]);
                if ("GET".equals(method)) handleGetAccount(exchange, accountNumber);
                else HttpUtil.sendJson(exchange, 405, ApiResponse.error("Method not allowed."));

            } else if (segments.length == 5) {
                // /api/accounts/{id}/{sub}
                long   accountNumber = Long.parseLong(segments[3]);
                String sub           = segments[4];
                switch (sub) {
                    case "status"  -> handleUpdateStatus(exchange, method, accountNumber);
                    case "balance" -> handleGetBalance(exchange, method, accountNumber);
                    default -> HttpUtil.sendJson(exchange, 404,
                                   ApiResponse.error("Unknown sub-resource: " + sub));
                }

            } else {
                HttpUtil.sendJson(exchange, 404, ApiResponse.error("Account route not found."));
            }

        } catch (AuthorizationException e) {
            HttpUtil.sendJson(exchange, 403, ApiResponse.error(e.getMessage()));
        } catch (NumberFormatException e) {
            HttpUtil.sendJson(exchange, 400,
                ApiResponse.error("Invalid account number in path."));
        } catch (com.bank.system.exception.BankingException e) {
            HttpUtil.sendJson(exchange, 400, ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("AccountController error: {}", e.getMessage(), e);
            HttpUtil.sendJson(exchange, 500, ApiResponse.error("Internal server error."));
        }
    }

    // ── POST /api/accounts ────────────────────────────────────────────────────

    /**
     * Opens a new bank account.
     * Requires {@link com.bank.system.security.Permission#OPEN_ACCOUNT}.
     *
     * <p>Request body:
     * <pre>
     * {
     *   "customerId": 1,
     *   "branchId": 1,
     *   "accountType": "SAVINGS",
     *   "initialBalance": 1000.00
     * }
     * </pre>
     */
    private void handleOpenAccount(HttpExchange exchange) throws IOException {
        OpenAccountRequest req = HttpUtil.readJson(exchange, OpenAccountRequest.class);
        if (req == null || req.accountType == null) {
            HttpUtil.sendJson(exchange, 400,
                ApiResponse.error("'customerId', 'branchId', 'accountType' are required."));
            return;
        }

        AccountType type;
        try {
            type = AccountType.valueOf(req.accountType.toUpperCase());
        } catch (IllegalArgumentException e) {
            HttpUtil.sendJson(exchange, 400,
                ApiResponse.error("Invalid accountType. Use SAVINGS, CHECKING, or LOAN."));
            return;
        }

        BigDecimal initial = req.initialBalance != null
            ? req.initialBalance : BigDecimal.ZERO;

        long accountNumber = secureService.openAccount(
            req.customerId, req.branchId, type, initial);

        HttpUtil.sendJson(exchange, 201, ApiResponse.ok(Map.of(
            "accountNumber", accountNumber,
            "message", "Account opened successfully."
        )));
    }

    // ── GET /api/accounts?customerId={id} ─────────────────────────────────────

    /**
     * Lists all accounts for a customer.
     * Requires {@link com.bank.system.security.Permission#VIEW_OWN_ACCOUNT} or
     * {@link com.bank.system.security.Permission#VIEW_ANY_ACCOUNT}.
     */
    private void handleListAccounts(HttpExchange exchange) throws IOException {
        String customerIdStr = HttpUtil.queryParam(exchange, "customerId");
        var principal = SecurityContext.require();

        List<Account> accounts;
        if (customerIdStr != null && !customerIdStr.isBlank()) {
            int customerId;
            try {
                customerId = Integer.parseInt(customerIdStr);
            } catch (NumberFormatException e) {
                HttpUtil.sendJson(exchange, 400,
                    ApiResponse.error("'customerId' must be a valid integer."));
                return;
            }
            accounts = secureService.getAccountsByCustomer(customerId);
        } else if (principal.getCustomerId() != null) {
            accounts = secureService.getAccountsByCustomer(principal.getCustomerId());
        } else if (principal.hasPermission(com.bank.system.security.Permission.VIEW_ANY_ACCOUNT)) {
            accounts = secureService.getAllAccounts();
        } else {
            HttpUtil.sendJson(exchange, 400,
                ApiResponse.error("Query parameter 'customerId' is required."));
            return;
        }

        HttpUtil.sendJson(exchange, 200, ApiResponse.ok(accounts));
    }

    // ── GET /api/accounts/{id} ────────────────────────────────────────────────

    /**
     * Returns the details of a single account.
     * Customers may only view their own accounts; staff may view any.
     */
    private void handleGetAccount(HttpExchange exchange, long accountNumber) throws IOException {
        Account account = secureService.getAccount(accountNumber);
        HttpUtil.sendJson(exchange, 200, ApiResponse.ok(account));
    }

    // ── PATCH /api/accounts/{id}/status ──────────────────────────────────────

    /**
     * Updates an account's status to FROZEN, ACTIVE, or CLOSED.
     * Requires {@link com.bank.system.security.Permission#FREEZE_ACCOUNT} or
     * {@link com.bank.system.security.Permission#CLOSE_ACCOUNT}.
     *
     * <p>Request body: {@code { "status": "FROZEN" }}
     */
    private void handleUpdateStatus(HttpExchange exchange, String method,
                                    long accountNumber) throws IOException {
        if (!"PATCH".equals(method)) {
            HttpUtil.sendJson(exchange, 405, ApiResponse.error("Use PATCH for status updates."));
            return;
        }

        StatusUpdateRequest req = HttpUtil.readJson(exchange, StatusUpdateRequest.class);
        if (req == null || req.status == null) {
            HttpUtil.sendJson(exchange, 400, ApiResponse.error("'status' field is required."));
            return;
        }

        AccountStatus newStatus;
        try {
            newStatus = AccountStatus.valueOf(req.status.toUpperCase());
        } catch (IllegalArgumentException e) {
            HttpUtil.sendJson(exchange, 400,
                ApiResponse.error("Invalid status. Use ACTIVE, FROZEN, or CLOSED."));
            return;
        }

        secureService.updateAccountStatus(accountNumber, newStatus);
        HttpUtil.sendJson(exchange, 200, ApiResponse.ok(Map.of(
            "accountNumber", accountNumber,
            "newStatus", newStatus.name(),
            "message", "Account status updated."
        )));
    }

    // ── GET /api/accounts/{id}/balance ────────────────────────────────────────

    /** Returns the current balance for the account. */
    private void handleGetBalance(HttpExchange exchange, String method,
                                  long accountNumber) throws IOException {
        if (!"GET".equals(method)) {
            HttpUtil.sendJson(exchange, 405, ApiResponse.error("Use GET for balance."));
            return;
        }
        BigDecimal balance = secureService.getBalance(accountNumber);
        HttpUtil.sendJson(exchange, 200, ApiResponse.ok(Map.of(
            "accountNumber", accountNumber,
            "balance", balance
        )));
    }
}
