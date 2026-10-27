package com.bank.system.main;

import com.bank.system.api.ApiServer;
import com.bank.system.config.DatabaseConfig;
import com.bank.system.model.Account;
import com.bank.system.model.Account.AccountType;
import com.bank.system.model.Transaction;
import com.bank.system.security.auth.DemoUserSeeder;
import com.bank.system.service.BankingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

/**
 * Starts the REST API and banking UI. Pass {@code --demo} for the old
 * console smoke test, or {@code --port=N} to change the listen port.
 */
public class ApplicationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(ApplicationEntryPoint.class);

    public static void main(String[] args) {
        if (hasFlag(args, "--demo")) {
            runDemo();
            return;
        }

        int port = ApiServer.DEFAULT_PORT;
        for (String arg : args) {
            if (arg.startsWith("--port=")) {
                port = Integer.parseInt(arg.substring("--port=".length()));
            }
        }

        try {
            com.bank.system.config.DatabaseInitializer.initialize();
            DemoUserSeeder.seed();
            ApiServer server = new ApiServer(port);
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                server.stop();
                DatabaseConfig.getInstance().shutdown();
            }));
            log.info("Open http://localhost:{}/ in a browser.", port);
            Thread.currentThread().join();
        } catch (IOException e) {
            log.error("Could not start API server: {}", e.getMessage(), e);
            System.exit(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (flag.equals(arg)) return true;
        }
        return false;
    }

    private static void runDemo() {
        log.info("=== Core Banking System – Demo Run ===");
        BankingService service = new BankingService();
        try {
            long accA = service.openAccount(1, 1, AccountType.SAVINGS,
                new BigDecimal("5000.00"));
            long accB = service.openAccount(1, 1, AccountType.CHECKING,
                new BigDecimal("1000.00"));
            log.info("Opened accounts: {} and {}", accA, accB);

            log.info("Deposit txId={}",
                service.deposit(accA, new BigDecimal("2000.00"), "Initial top-up"));
            log.info("Withdrawal txId={}",
                service.withdraw(accA, new BigDecimal("500.00"), "ATM withdrawal"));
            log.info("Transfer txId={}",
                service.transfer(accA, accB, new BigDecimal("1000.00"), "Rent payment"));
            log.info("Balance A: {}", service.getBalance(accA));
            log.info("Balance B: {}", service.getBalance(accB));

            List<Transaction> history = service.getTransactionHistory(accA, 10);
            log.info("Transaction history for account {}:", accA);
            history.forEach(tx -> log.info("  {}", tx));

            List<Account> accounts = service.getAccountsByCustomer(1);
            log.info("All accounts for customer 1:");
            accounts.forEach(a -> log.info("  {}", a));
        } catch (Exception e) {
            log.error("Demo failed: {}", e.getMessage(), e);
        } finally {
            DatabaseConfig.getInstance().shutdown();
        }
        log.info("=== Demo complete ===");
    }
}
