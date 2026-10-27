package com.bank.system;

import com.bank.system.dao.AccountDAO;
import com.bank.system.dao.TransactionDAO;
import com.bank.system.exception.AccountNotFoundException;
import com.bank.system.exception.BankingException;
import com.bank.system.exception.InsufficientBalanceException;
import com.bank.system.model.Account;
import com.bank.system.model.Account.AccountStatus;
import com.bank.system.model.Account.AccountType;
import com.bank.system.model.Transaction;
import com.bank.system.model.Transaction.TransactionStatus;
import com.bank.system.model.Transaction.TransactionType;
import com.bank.system.service.BankingService;
import com.bank.system.service.BankingService.ConnectionSupplier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BankingService}.
 *
 * <p>All database access is mocked via Mockito so these tests run without a
 * live database connection.  A mock {@link DataSource} and {@link Connection}
 * are injected into the service so that the {@code DatabaseConfig} singleton
 * (which requires env vars) is never touched.
 *
 * <p>Coverage targets:
 * <ul>
 *   <li>Happy paths: deposit, withdrawal, transfer, balance, history</li>
 *   <li>Invalid input: zero/negative amounts, null amount</li>
 *   <li>Insufficient balance on withdrawal and transfer</li>
 *   <li>Inactive / missing accounts</li>
 *   <li>Same-account transfer rejection</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BankingService Unit Tests")
class BankingServiceTest {

    // ── Mocks ─────────────────────────────────────────────────────────────────

    @Mock private AccountDAO     accountDAO;
    @Mock private TransactionDAO transactionDAO;
    @Mock private Connection     connection;

    /** Service under test – built manually so we can pass a mock Connection. */
    private BankingService service;

    @BeforeEach
    void setUp() throws Exception {
        // Stubs are lenient because tests that throw before reaching the
        // connection phase (e.g. same-account, invalid-amount) do not consume
        // them – Mockito strict mode would otherwise fail those tests.
        lenient().doNothing().when(connection).setAutoCommit(false);
        lenient().doNothing().when(connection).commit();
        lenient().doNothing().when(connection).rollback();
        lenient().doNothing().when(connection).close();   // try-with-resources

        // Inject a simple lambda as the ConnectionSupplier
        ConnectionSupplier supplier = () -> connection;
        service = new BankingService(accountDAO, transactionDAO, supplier);
    }

    // ── Test fixtures ─────────────────────────────────────────────────────────

    private static final long ACC_A = 1001L;
    private static final long ACC_B = 1002L;

    private Account makeAccount(long number, BigDecimal balance, AccountStatus status) {
        return new Account(number, 1, 1, AccountType.SAVINGS, balance, status, LocalDateTime.now());
    }

    // =========================================================================
    // deposit()
    // =========================================================================

    @Nested
    @DisplayName("deposit()")
    class DepositTests {

        @Test
        @DisplayName("deposits a valid amount and returns a transaction ID")
        void deposit_success() throws Exception {
            Account acc = makeAccount(ACC_A, new BigDecimal("1000.00"), AccountStatus.ACTIVE);
            when(accountDAO.findById(any(), eq(ACC_A))).thenReturn(Optional.of(acc));
            doNothing().when(accountDAO).updateBalance(any(), eq(ACC_A), any());
            when(transactionDAO.insertTransaction(any(), isNull(), eq(ACC_A),
                    eq(new BigDecimal("500.00")),
                    eq(TransactionType.DEPOSIT), eq(TransactionStatus.SUCCESS), anyString()))
                .thenReturn(42L);

            long txId = service.deposit(ACC_A, new BigDecimal("500.00"), "test deposit");
            assertEquals(42L, txId);
        }

        @Test
        @DisplayName("throws BankingException when amount is zero")
        void deposit_zeroAmount() {
            assertThrows(BankingException.class,
                () -> service.deposit(ACC_A, BigDecimal.ZERO, "zero"));
        }

        @Test
        @DisplayName("throws BankingException when amount is negative")
        void deposit_negativeAmount() {
            assertThrows(BankingException.class,
                () -> service.deposit(ACC_A, new BigDecimal("-100.00"), "negative"));
        }

        @Test
        @DisplayName("throws BankingException when amount is null")
        void deposit_nullAmount() {
            assertThrows(BankingException.class,
                () -> service.deposit(ACC_A, null, "null"));
        }

        @Test
        @DisplayName("throws BankingException when account is FROZEN")
        void deposit_frozenAccount() throws Exception {
            Account acc = makeAccount(ACC_A, new BigDecimal("1000.00"), AccountStatus.FROZEN);
            when(accountDAO.findById(any(), eq(ACC_A))).thenReturn(Optional.of(acc));

            assertThrows(BankingException.class,
                () -> service.deposit(ACC_A, new BigDecimal("500.00"), "frozen"));
        }

        @Test
        @DisplayName("throws AccountNotFoundException when account does not exist")
        void deposit_accountNotFound() throws Exception {
            when(accountDAO.findById(any(), eq(ACC_A))).thenReturn(Optional.empty());

            assertThrows(AccountNotFoundException.class,
                () -> service.deposit(ACC_A, new BigDecimal("100.00"), "missing"));
        }
    }

    // =========================================================================
    // withdraw()
    // =========================================================================

    @Nested
    @DisplayName("withdraw()")
    class WithdrawTests {

        @Test
        @DisplayName("withdraws a valid amount and returns a transaction ID")
        void withdraw_success() throws Exception {
            Account acc = makeAccount(ACC_A, new BigDecimal("1000.00"), AccountStatus.ACTIVE);
            when(accountDAO.findById(any(), eq(ACC_A))).thenReturn(Optional.of(acc));
            doNothing().when(accountDAO).updateBalance(any(), eq(ACC_A), any());
            when(transactionDAO.insertTransaction(any(), eq(ACC_A), isNull(),
                    eq(new BigDecimal("300.00")),
                    eq(TransactionType.WITHDRAWAL), eq(TransactionStatus.SUCCESS), anyString()))
                .thenReturn(77L);

            long txId = service.withdraw(ACC_A, new BigDecimal("300.00"), "ATM");
            assertEquals(77L, txId);
        }

        @Test
        @DisplayName("throws InsufficientBalanceException when balance is too low")
        void withdraw_insufficientBalance() throws Exception {
            Account acc = makeAccount(ACC_A, new BigDecimal("100.00"), AccountStatus.ACTIVE);
            when(accountDAO.findById(any(), eq(ACC_A))).thenReturn(Optional.of(acc));

            InsufficientBalanceException ex = assertThrows(InsufficientBalanceException.class,
                () -> service.withdraw(ACC_A, new BigDecimal("500.00"), "over-draft"));

            assertEquals(ACC_A, ex.getAccountNumber());
            assertEquals(0, new BigDecimal("100.00").compareTo(ex.getCurrentBalance()));
            assertEquals(0, new BigDecimal("500.00").compareTo(ex.getRequestedAmount()));
        }

        @Test
        @DisplayName("throws BankingException when account is CLOSED")
        void withdraw_closedAccount() throws Exception {
            Account acc = makeAccount(ACC_A, new BigDecimal("500.00"), AccountStatus.CLOSED);
            when(accountDAO.findById(any(), eq(ACC_A))).thenReturn(Optional.of(acc));

            assertThrows(BankingException.class,
                () -> service.withdraw(ACC_A, new BigDecimal("100.00"), "closed"));
        }

        @Test
        @DisplayName("throws BankingException when amount is zero or negative")
        void withdraw_invalidAmount() {
            assertThrows(BankingException.class,
                () -> service.withdraw(ACC_A, BigDecimal.ZERO, "zero"));
            assertThrows(BankingException.class,
                () -> service.withdraw(ACC_A, new BigDecimal("-50.00"), "neg"));
        }
    }

    // =========================================================================
    // transfer()
    // =========================================================================

    @Nested
    @DisplayName("transfer()")
    class TransferTests {

        @Test
        @DisplayName("transfers between two active accounts and returns a transaction ID")
        void transfer_success() throws Exception {
            Account sender   = makeAccount(ACC_A, new BigDecimal("2000.00"), AccountStatus.ACTIVE);
            Account receiver = makeAccount(ACC_B, new BigDecimal("500.00"),  AccountStatus.ACTIVE);

            // Accounts are fetched in ascending order (ACC_A < ACC_B)
            when(accountDAO.findById(any(), eq(ACC_A))).thenReturn(Optional.of(sender));
            when(accountDAO.findById(any(), eq(ACC_B))).thenReturn(Optional.of(receiver));
            doNothing().when(accountDAO).updateBalance(any(), anyLong(), any());

            when(transactionDAO.insertTransaction(any(), eq(ACC_A), eq(ACC_B),
                    eq(new BigDecimal("1000.00")),
                    eq(TransactionType.TRANSFER), eq(TransactionStatus.SUCCESS), anyString()))
                .thenReturn(99L);

            long txId = service.transfer(ACC_A, ACC_B, new BigDecimal("1000.00"), "rent");
            assertEquals(99L, txId);
        }

        @Test
        @DisplayName("throws BankingException when sender == receiver")
        void transfer_sameAccount() {
            assertThrows(BankingException.class,
                () -> service.transfer(ACC_A, ACC_A, new BigDecimal("100.00"), "same"));
        }

        @Test
        @DisplayName("throws InsufficientBalanceException when sender has insufficient funds")
        void transfer_insufficientBalance() throws Exception {
            Account sender   = makeAccount(ACC_A, new BigDecimal("50.00"), AccountStatus.ACTIVE);
            Account receiver = makeAccount(ACC_B, new BigDecimal("500.00"), AccountStatus.ACTIVE);

            when(accountDAO.findById(any(), eq(ACC_A))).thenReturn(Optional.of(sender));
            when(accountDAO.findById(any(), eq(ACC_B))).thenReturn(Optional.of(receiver));

            assertThrows(InsufficientBalanceException.class,
                () -> service.transfer(ACC_A, ACC_B, new BigDecimal("500.00"), "broke"));
        }

        @Test
        @DisplayName("throws AccountNotFoundException when sender does not exist")
        void transfer_senderNotFound() throws Exception {
            when(accountDAO.findById(any(), eq(ACC_A))).thenReturn(Optional.empty());

            assertThrows(AccountNotFoundException.class,
                () -> service.transfer(ACC_A, ACC_B, new BigDecimal("100.00"), "missing sender"));
        }

        @Test
        @DisplayName("throws AccountNotFoundException when receiver does not exist")
        void transfer_receiverNotFound() throws Exception {
            Account sender = makeAccount(ACC_A, new BigDecimal("2000.00"), AccountStatus.ACTIVE);
            when(accountDAO.findById(any(), eq(ACC_A))).thenReturn(Optional.of(sender));
            when(accountDAO.findById(any(), eq(ACC_B))).thenReturn(Optional.empty());

            assertThrows(AccountNotFoundException.class,
                () -> service.transfer(ACC_A, ACC_B, new BigDecimal("100.00"), "missing receiver"));
        }

        @Test
        @DisplayName("throws BankingException when receiver account is FROZEN")
        void transfer_frozenReceiver() throws Exception {
            Account sender   = makeAccount(ACC_A, new BigDecimal("2000.00"), AccountStatus.ACTIVE);
            Account receiver = makeAccount(ACC_B, new BigDecimal("500.00"),  AccountStatus.FROZEN);

            when(accountDAO.findById(any(), eq(ACC_A))).thenReturn(Optional.of(sender));
            when(accountDAO.findById(any(), eq(ACC_B))).thenReturn(Optional.of(receiver));

            assertThrows(BankingException.class,
                () -> service.transfer(ACC_A, ACC_B, new BigDecimal("100.00"), "frozen recv"));
        }

        @Test
        @DisplayName("throws BankingException when amount is zero or negative")
        void transfer_invalidAmount() {
            assertThrows(BankingException.class,
                () -> service.transfer(ACC_A, ACC_B, BigDecimal.ZERO, "zero"));
            assertThrows(BankingException.class,
                () -> service.transfer(ACC_A, ACC_B, new BigDecimal("-10"), "neg"));
        }
    }

    // =========================================================================
    // getBalance()
    // =========================================================================

    @Nested
    @DisplayName("getBalance()")
    class BalanceTests {

        @Test
        @DisplayName("returns the correct balance for an existing account")
        void getBalance_success() {
            when(accountDAO.getBalance(ACC_A)).thenReturn(new BigDecimal("9999.99"));
            BigDecimal balance = service.getBalance(ACC_A);
            assertEquals(0, new BigDecimal("9999.99").compareTo(balance));
        }

        @Test
        @DisplayName("propagates AccountNotFoundException for unknown account")
        void getBalance_notFound() {
            when(accountDAO.getBalance(ACC_A)).thenThrow(new AccountNotFoundException(ACC_A));
            assertThrows(AccountNotFoundException.class, () -> service.getBalance(ACC_A));
        }
    }

    // =========================================================================
    // getTransactionHistory()
    // =========================================================================

    @Nested
    @DisplayName("getTransactionHistory()")
    class HistoryTests {

        @Test
        @DisplayName("returns a list of transactions")
        void history_success() {
            Transaction tx = new Transaction(1L, null, ACC_A, new BigDecimal("500.00"),
                TransactionType.DEPOSIT, TransactionStatus.SUCCESS, "note", LocalDateTime.now());
            when(transactionDAO.getHistory(ACC_A, 10)).thenReturn(List.of(tx));

            List<Transaction> history = service.getTransactionHistory(ACC_A, 10);
            assertEquals(1, history.size());
            assertEquals(1L, history.get(0).getTransactionId());
        }

        @Test
        @DisplayName("throws BankingException when limit is zero or negative")
        void history_invalidLimit() {
            assertThrows(BankingException.class,
                () -> service.getTransactionHistory(ACC_A, 0));
            assertThrows(BankingException.class,
                () -> service.getTransactionHistory(ACC_A, -5));
        }
    }

    // =========================================================================
    // openAccount()
    // =========================================================================

    @Nested
    @DisplayName("openAccount()")
    class OpenAccountTests {

        @Test
        @DisplayName("creates account and returns account number")
        void openAccount_success() {
            when(accountDAO.createAccount(1, 1, AccountType.SAVINGS, new BigDecimal("500.00")))
                .thenReturn(ACC_A);

            long number = service.openAccount(1, 1, AccountType.SAVINGS, new BigDecimal("500.00"));
            assertEquals(ACC_A, number);
        }

        @Test
        @DisplayName("throws BankingException for negative initial balance")
        void openAccount_negativeBalance() {
            assertThrows(BankingException.class,
                () -> service.openAccount(1, 1, AccountType.SAVINGS, new BigDecimal("-1.00")));
        }
    }
}
