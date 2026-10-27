package com.bank.system.api;

import com.bank.system.api.controller.AccountController;
import com.bank.system.api.controller.AuditController;
import com.bank.system.api.controller.AuthController;
import com.bank.system.api.controller.BranchController;
import com.bank.system.api.controller.CustomerController;
import com.bank.system.api.controller.TransactionController;
import com.bank.system.api.filter.AuthFilter;
import com.bank.system.api.handler.StaticFileHandler;
import com.bank.system.security.auth.AuthService;
import com.bank.system.security.guard.SecureBankingService;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * ApiServer – wires all HTTP handlers and starts the embedded JDK HTTP server.
 *
 * <h3>Design</h3>
 * <p>Uses {@link com.sun.net.httpserver.HttpServer}, which is part of the
 * standard JDK.  No external server (Tomcat, Jetty, Netty) is required,
 * keeping dependencies minimal for an academic project.
 *
 * <h3>Route table</h3>
 * <pre>
 *   ┌──────────────────────────────────────────┬────────────┬──────────────┐
 *   │ Context path                             │ Controller │ Auth filter? │
 *   ├──────────────────────────────────────────┼────────────┼──────────────┤
 *   │ /api/auth/login                          │ Auth       │ No           │
 *   │ /api/auth/logout, /mfa, /me              │ Auth       │ Yes          │
 *   │ /api/accounts/**                         │ Account    │ Yes          │
 *   │ /api/transactions/**                     │ Transaction│ Yes          │
 *   │ /api/audit/**                            │ Audit      │ Yes          │
 *   └──────────────────────────────────────────┴────────────┴──────────────┘
 * </pre>
 *
 * <h3>Thread pool</h3>
 * <p>A fixed thread-pool executor with {@value #THREAD_POOL_SIZE} threads is
 * used so that concurrent requests do not share a single thread (the default).
 */
public class ApiServer {

    private static final Logger log = LoggerFactory.getLogger(ApiServer.class);

    /** Port the server listens on.  Override by passing {@code --port=N} arg. */
    public static final int DEFAULT_PORT = 8080;

    /** Concurrent handler threads. */
    private static final int THREAD_POOL_SIZE = 10;

    private final HttpServer          httpServer;
    private final SecureBankingService secureService;
    private final AuthService          authService;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * Creates the API server bound to the given port.
     *
     * @param port the TCP port to listen on
     * @throws IOException if the server socket cannot be opened
     */
    public ApiServer(int port) throws IOException {
        this.secureService = new SecureBankingService();
        this.authService   = new AuthService();
        this.httpServer    = HttpServer.create(new InetSocketAddress(port), 0);
        registerHandlers();
        httpServer.setExecutor(Executors.newFixedThreadPool(THREAD_POOL_SIZE));
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Starts the HTTP server. */
    public void start() {
        httpServer.start();
        log.info("=== Core Banking API started on port {} ===", httpServer.getAddress().getPort());
        log.info("Endpoints:");
        log.info("  POST   http://localhost:{}/api/auth/login", httpServer.getAddress().getPort());
        log.info("  POST   http://localhost:{}/api/auth/logout  [auth required]", httpServer.getAddress().getPort());
        log.info("  POST   http://localhost:{}/api/auth/mfa     [auth required]", httpServer.getAddress().getPort());
        log.info("  GET    http://localhost:{}/api/auth/me      [auth required]", httpServer.getAddress().getPort());
        log.info("  POST   http://localhost:{}/api/accounts     [BRANCH_MANAGER+]", httpServer.getAddress().getPort());
        log.info("  GET    http://localhost:{}/api/accounts?customerId=N  [auth required]", httpServer.getAddress().getPort());
        log.info("  GET    http://localhost:{}/api/accounts/{{id}}        [auth required]", httpServer.getAddress().getPort());
        log.info("  PATCH  http://localhost:{}/api/accounts/{{id}}/status [MANAGER+]", httpServer.getAddress().getPort());
        log.info("  GET    http://localhost:{}/api/accounts/{{id}}/balance [auth required]", httpServer.getAddress().getPort());
        log.info("  POST   http://localhost:{}/api/transactions/deposit   [TELLER+]", httpServer.getAddress().getPort());
        log.info("  POST   http://localhost:{}/api/transactions/withdraw  [TELLER+]", httpServer.getAddress().getPort());
        log.info("  POST   http://localhost:{}/api/transactions/transfer  [CUSTOMER+]", httpServer.getAddress().getPort());
        log.info("  GET    http://localhost:{}/api/transactions/history/{{id}} [auth required]", httpServer.getAddress().getPort());
        log.info("  GET    http://localhost:{}/api/audit         [MANAGER/AUDITOR/ADMIN]", httpServer.getAddress().getPort());
        log.info("  UI     http://localhost:{}/", httpServer.getAddress().getPort());
    }

    /** Stops the HTTP server gracefully. */
    public void stop() {
        httpServer.stop(2);   // 2-second graceful drain
        log.info("API server stopped.");
    }

    // ── Handler registration ──────────────────────────────────────────────────

    private void registerHandlers() {
        AuthController        authCtrl   = new AuthController(authService);
        AccountController     accountCtrl= new AccountController(secureService);
        TransactionController txCtrl     = new TransactionController(secureService);
        AuditController       auditCtrl  = new AuditController(secureService);

        // ── /api/auth/login – PUBLIC (no auth filter) ─────────────────────────
        // Only login is public; all other auth paths go through the filter.
        httpServer.createContext("/api/auth/login",  exchange -> {
            // Delegate directly to AuthController for the login path only
            authCtrl.handle(exchange);
        });

        // ── All other /api/auth/** – protected ────────────────────────────────
        httpServer.createContext("/api/auth",  new AuthFilter(authCtrl));

        // ── /api/accounts/** – protected ──────────────────────────────────────
        httpServer.createContext("/api/accounts", new AuthFilter(accountCtrl));

        // ── /api/transactions/** – protected ──────────────────────────────────
        httpServer.createContext("/api/transactions", new AuthFilter(txCtrl));

        CustomerController    customerCtrl = new CustomerController(secureService);
        BranchController      branchCtrl   = new BranchController(secureService);

        // ── /api/customers/** – protected ─────────────────────────────────────
        httpServer.createContext("/api/customers", new AuthFilter(customerCtrl));

        // ── /api/branches/** – protected ──────────────────────────────────────
        httpServer.createContext("/api/branches", new AuthFilter(branchCtrl));

        // ── /api/audit/** – protected ─────────────────────────────────────────
        httpServer.createContext("/api/audit", new AuthFilter(auditCtrl));

        // ── Health check – PUBLIC ─────────────────────────────────────────────
        httpServer.createContext("/health", exchange -> {
            byte[] body = "{\"status\":\"UP\"}".getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var os = exchange.getResponseBody()) { os.write(body); }
        });

        // ── Banking UI (longest-prefix match leaves /api/* on controllers) ───
        httpServer.createContext("/", new StaticFileHandler());

        log.debug("HTTP handlers registered.");
    }
}
