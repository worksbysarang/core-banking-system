package com.bank.system.api.controller;

import com.bank.system.api.dto.ApiResponse;
import com.bank.system.api.dto.TransactionRequest;
import com.bank.system.api.util.HttpUtil;
import com.bank.system.model.Transaction;
import com.bank.system.security.auth.AuthService;
import com.bank.system.security.exception.AuthenticationException;
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
 * TransactionController – handles money movement and history endpoints.
 *
 * <h3>Endpoints</h3>
 * <pre>
 *   POST /api/transactions/deposit       – deposit into an account
 *   POST /api/transactions/withdraw      – withdraw from an account
 *   POST /api/transactions/transfer      – transfer between two accounts
 *   GET  /api/transactions/history/{id}  – transaction history for an account
 * </pre>
 *
 * <h3>High-value transfers</h3>
 * <p>Transfers ≥ ₹10,000 require prior MFA step-up via
 * {@code POST /api/auth/mfa}.  If not completed, the response is
 * {@code 401 Unauthenticated} with an explanatory message.
 *
 * <h3>Security</h3>
 * <p>All endpoints sit behind {@link com.bank.system.api.filter.AuthFilter}.
 * RBAC and ownership checks run inside {@link SecureBankingService}.
 */
public class TransactionController implements HttpHandler {

    private static final Logger log = LoggerFactory.getLogger(TransactionController.class);

    private static final int DEFAULT_HISTORY_LIMIT = 20;

    private final SecureBankingService secureService;

    public TransactionController(SecureBankingService secureService) {
        this.secureService = secureService;
    }

    // ── Dispatch ──────────────────────────────────────────────────────────────

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase();
        String path   = exchange.getRequestURI().getPath();

        try {
            if ("OPTIONS".equals(method)) { HttpUtil.handleOptions(exchange); return; }

            String[] segments = path.split("/");
            // segments: ["", "api", "transactions", <action>, <id?>]

            if (segments.length < 4) {
                HttpUtil.sendJson(exchange, 404, ApiResponse.error("Transaction route not found."));
                return;
            }

            String action = segments[3];   // deposit | withdraw | transfer | history

            switch (action) {
                case "deposit"  -> handleDeposit(exchange, method);
                case "withdraw" -> handleWithdraw(exchange, method);
                case "transfer" -> handleTransfer(exchange, method);
                case "history"  -> {
                    if (segments.length < 5) {
                        HttpUtil.sendJson(exchange, 400,
                            ApiResponse.error("Account number required: /api/transactions/history/{id}"));
                        return;
                    }
                    long accId = Long.parseLong(segments[4]);
                    handleHistory(exchange, method, accId);
                }
                default -> HttpUtil.sendJson(exchange, 404,
                               ApiResponse.error("Unknown transaction action: " + action));
            }

        } catch (AuthenticationException e) {
            // Covers both "not logged in" and "MFA required" errors
            HttpUtil.sendJson(exchange, 401, ApiResponse.error(e.getMessage()));
        } catch (AuthorizationException e) {
            HttpUtil.sendJson(exchange, 403, ApiResponse.error(e.getMessage()));
        } catch (NumberFormatException e) {
            HttpUtil.sendJson(exchange, 400,
                ApiResponse.error("Invalid account number in path."));
        } catch (com.bank.system.exception.BankingException e) {
            HttpUtil.sendJson(exchange, 400, ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("TransactionController error: {}", e.getMessage(), e);
            HttpUtil.sendJson(exchange, 500, ApiResponse.error("Internal server error."));
        }
    }

    // ── POST /api/transactions/deposit ────────────────────────────────────────

    /**
     * Deposits a positive amount into the specified account.
     * Requires {@link com.bank.system.security.Permission#DEPOSIT}.
     *
     * <p>Request body:
     * <pre>{ "accountNumber": 1001, "amount": 500.00, "note": "Salary" }</pre>
     */
    private void handleDeposit(HttpExchange exchange, String method) throws IOException {
        if (!"POST".equals(method)) {
            HttpUtil.sendJson(exchange, 405, ApiResponse.error("Use POST."));
            return;
        }
        TransactionRequest req = HttpUtil.readJson(exchange, TransactionRequest.class);
        if (req == null || req.accountNumber == 0 || req.amount == null) {
            HttpUtil.sendJson(exchange, 400,
                ApiResponse.error("'accountNumber' and 'amount' are required."));
            return;
        }

        long txId = secureService.deposit(req.accountNumber, req.amount, req.note);
        HttpUtil.sendJson(exchange, 200, ApiResponse.ok(transactionResult(txId, "Deposit successful.")));
    }

    // ── POST /api/transactions/withdraw ───────────────────────────────────────

    /**
     * Withdraws a positive amount from the specified account.
     * Requires {@link com.bank.system.security.Permission#WITHDRAW}.
     *
     * <p>Request body:
     * <pre>{ "accountNumber": 1001, "amount": 200.00, "note": "ATM" }</pre>
     */
    private void handleWithdraw(HttpExchange exchange, String method) throws IOException {
        if (!"POST".equals(method)) {
            HttpUtil.sendJson(exchange, 405, ApiResponse.error("Use POST."));
            return;
        }
        TransactionRequest req = HttpUtil.readJson(exchange, TransactionRequest.class);
        if (req == null || req.accountNumber == 0 || req.amount == null) {
            HttpUtil.sendJson(exchange, 400,
                ApiResponse.error("'accountNumber' and 'amount' are required."));
            return;
        }

        long txId = secureService.withdraw(req.accountNumber, req.amount, req.note);
        HttpUtil.sendJson(exchange, 200, ApiResponse.ok(transactionResult(txId, "Withdrawal successful.")));
    }

    // ── POST /api/transactions/transfer ───────────────────────────────────────

    /**
     * Transfers funds between two accounts.
     * Requires {@link com.bank.system.security.Permission#TRANSFER} and
     * account ownership (customers) or staff role.
     *
     * <p>Transfers ≥ ₹{@value AuthService#HIGH_VALUE_THRESHOLD} additionally
     * require MFA step-up ({@code POST /api/auth/mfa}).
     *
     * <p>Request body:
     * <pre>
     * {
     *   "senderAccountNumber":   1001,
     *   "receiverAccountNumber": 1002,
     *   "amount": 500.00,
     *   "note": "Rent payment"
     * }
     * </pre>
     */
    private void handleTransfer(HttpExchange exchange, String method) throws IOException {
        if (!"POST".equals(method)) {
            HttpUtil.sendJson(exchange, 405, ApiResponse.error("Use POST."));
            return;
        }
        TransactionRequest req = HttpUtil.readJson(exchange, TransactionRequest.class);
        if (req == null || req.senderAccountNumber == 0
                || req.receiverAccountNumber == 0 || req.amount == null) {
            HttpUtil.sendJson(exchange, 400,
                ApiResponse.error(
                    "'senderAccountNumber', 'receiverAccountNumber', and 'amount' are required."));
            return;
        }

        // Inform caller if MFA is needed (they can pre-empt the 401)
        boolean mfaRequired = AuthService.requiresMfa(req.amount);

        long txId;
        if (Boolean.TRUE.equals(req.useStoredProcedure)) {
            txId = secureService.transferViaProcedure(
                req.senderAccountNumber, req.receiverAccountNumber,
                req.amount, req.note);
        } else {
            txId = secureService.transfer(
                req.senderAccountNumber, req.receiverAccountNumber,
                req.amount, req.note);
        }

        HttpUtil.sendJson(exchange, 200, ApiResponse.ok(Map.of(
            "transactionId", txId,
            "message",       Boolean.TRUE.equals(req.useStoredProcedure) 
                ? "Transfer successful via PostgreSQL stored procedure." 
                : "Transfer successful.",
            "mfaWasRequired", mfaRequired
        )));
    }

    // ── GET /api/transactions/history/{accountNumber} ─────────────────────────

    /**
     * Returns recent transaction history for an account.
     * Requires {@link com.bank.system.security.Permission#VIEW_OWN_TRANSACTIONS}
     * or {@link com.bank.system.security.Permission#VIEW_ALL_TRANSACTIONS}.
     *
     * <p>Optional query parameter: {@code ?limit=N} (default 20).
     */
    private void handleHistory(HttpExchange exchange, String method,
                               long accountNumber) throws IOException {
        if (!"GET".equals(method)) {
            HttpUtil.sendJson(exchange, 405, ApiResponse.error("Use GET."));
            return;
        }

        int limit = DEFAULT_HISTORY_LIMIT;
        String limitStr = HttpUtil.queryParam(exchange, "limit");
        if (limitStr != null) {
            try { limit = Integer.parseInt(limitStr); }
            catch (NumberFormatException e) { /* use default */ }
        }
        if (limit <= 0 || limit > 200) limit = DEFAULT_HISTORY_LIMIT;

        List<Transaction> history = secureService.getTransactionHistory(accountNumber, limit);
        HttpUtil.sendJson(exchange, 200, ApiResponse.ok(Map.of(
            "accountNumber", accountNumber,
            "count",         history.size(),
            "transactions",  history
        )));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Map<String, Object> transactionResult(long txId, String message) {
        return Map.of("transactionId", txId, "message", message);
    }
}
