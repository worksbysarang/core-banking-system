package com.bank.system.main;

import com.bank.system.config.DatabaseConfig;
import com.bank.system.model.Account;
import com.bank.system.model.Account.AccountType;
import com.bank.system.model.Transaction;
import com.bank.system.service.BankingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;

/**
 * ApplicationEntryPoint – smoke-test / demo harness for the banking engine.
 *
 * <p>This class is <strong>not</strong> a production entry point.  It exists to
 * let Member 4 verify the service layer works end-to-end against a real
 * Supabase PostgreSQL database before the UI/REST layer is wired in.
 *
 * <p>Set the three environment variables before running:
 * <pre>
 *   export DB_URL=jdbc:postgresql://&lt;host&gt;:5432/&lt;dbname&gt;
 *   export DB_USER=&lt;username&gt;
 *   export DB_PASSWORD=&lt;password&gt;
 * </pre>
 */
public class ApplicationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(ApplicationEntryPoint.class);

    public static void main(String[] args) {
        log.info("=== Core Banking System – Demo Run ===");

        BankingService service = new BankingService();

        try {
            // ── 1. Open two accounts (assumes customer 1 & branch 1 exist) ───
            long accA = service.openAccount(1, 1, AccountType.SAVINGS,
                                            new BigDecimal("5000.00"));
            long accB = service.openAccount(1, 1, AccountType.CHECKING,
                                            new BigDecimal("1000.00"));
            log.info("Opened accounts: {} and {}", accA, accB);

            // ── 2. Deposit ────────────────────────────────────────────────────
            long depTx = service.deposit(accA, new BigDecimal("2000.00"), "Initial top-up");
            log.info("Deposit txId={}", depTx);

            // ── 3. Withdraw ───────────────────────────────────────────────────
            long wdTx = service.withdraw(accA, new BigDecimal("500.00"), "ATM withdrawal");
            log.info("Withdrawal txId={}", wdTx);

            // ── 4. Transfer ───────────────────────────────────────────────────
            long trTx = service.transfer(accA, accB, new BigDecimal("1000.00"),
                                         "Rent payment");
            log.info("Transfer txId={}", trTx);

            // ── 5. Balance check ──────────────────────────────────────────────
            log.info("Balance A: {}", service.getBalance(accA));
            log.info("Balance B: {}", service.getBalance(accB));

            // ── 6. Transaction history ────────────────────────────────────────
            List<Transaction> history = service.getTransactionHistory(accA, 10);
            log.info("Transaction history for account {}:", accA);
            history.forEach(tx -> log.info("  {}", tx));

            // ── 7. Account listing ────────────────────────────────────────────
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
