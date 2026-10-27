package com.bank.system.security;

import com.bank.system.model.Account;
import com.bank.system.model.Account.AccountStatus;
import com.bank.system.model.Account.AccountType;
import com.bank.system.security.audit.AuditService;
import com.bank.system.security.auth.AuthService;
import com.bank.system.security.auth.PasswordHasher;
import com.bank.system.security.exception.AuthenticationException;
import com.bank.system.security.exception.AuthorizationException;
import com.bank.system.security.guard.SecureBankingService;
import com.bank.system.security.rbac.RbacPolicy;
import com.bank.system.service.BankingService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SecurityTest – comprehensive unit tests for the Member 3 security layer.
 *
 * <h3>Test coverage</h3>
 * <ul>
 *   <li>PasswordHasher: hash/verify, empty input, wrong password, timing-safety.</li>
 *   <li>Role: hierarchy check, permission resolution.</li>
 *   <li>Permission: role-to-permission mapping.</li>
 *   <li>UserPrincipal: ownership check, MFA flag.</li>
 *   <li>SecurityContext: set/get/require/clear, thread isolation.</li>
 *   <li>RbacPolicy: permission grants and denials for every role.</li>
 *   <li>SecureBankingService: unauthenticated access, customer vs staff rules,
 *       MFA gate, audit event recording.</li>
 * </ul>
 *
 * <p>All tests are pure unit tests – no database connection is required.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Member 3 – Security / RBAC / Audit Tests")
class SecurityTest {

    // ── Shared helpers ────────────────────────────────────────────────────────

    private static final int    CUSTOMER_ID   = 42;
    private static final int    OTHER_CUST_ID = 99;
    private static final long   ACCOUNT_NUM   = 1001L;
    private static final String USERNAME      = "testuser";

    private UserPrincipal customer() {
        return new UserPrincipal(1, USERNAME, Role.CUSTOMER,
                                 CUSTOMER_ID, LocalDateTime.now());
    }

    private UserPrincipal teller() {
        return new UserPrincipal(2, "teller01", Role.TELLER,
                                 null, LocalDateTime.now());
    }

    private UserPrincipal manager() {
        return new UserPrincipal(3, "mgr01", Role.BRANCH_MANAGER,
                                 null, LocalDateTime.now());
    }

    private UserPrincipal auditor() {
        return new UserPrincipal(4, "audit01", Role.AUDITOR,
                                 null, LocalDateTime.now());
    }

    private UserPrincipal admin() {
        return new UserPrincipal(5, "admin", Role.ADMIN,
                                 null, LocalDateTime.now());
    }

    @AfterEach
    void clearContext() {
        SecurityContext.clear();
    }

    // =========================================================================
    // PasswordHasher Tests
    // =========================================================================

    @Nested
    @DisplayName("PasswordHasher")
    class PasswordHasherTests {

        @Test
        @DisplayName("hash() returns a non-blank credential string with correct prefix")
        void hashReturnsPbkdf2String() {
            String hashed = PasswordHasher.hash("SecureP@ssw0rd");
            assertNotNull(hashed);
            assertTrue(hashed.startsWith("pbkdf2sha256:"),
                       "Stored credential must start with 'pbkdf2sha256:'");
        }

        @Test
        @DisplayName("Two hash() calls on the same password produce different salts")
        void hashIsRandomlySalted() {
            String h1 = PasswordHasher.hash("SamePassword!");
            String h2 = PasswordHasher.hash("SamePassword!");
            assertNotEquals(h1, h2, "Same password must not produce identical stored value");
        }

        @Test
        @DisplayName("verify() returns true for correct password")
        void verifyCorrectPassword() {
            String plaintext = "MyS3cur3Pass!";
            String hash = PasswordHasher.hash(plaintext);
            assertTrue(PasswordHasher.verify(plaintext, hash));
        }

        @Test
        @DisplayName("verify() returns false for wrong password")
        void verifyWrongPassword() {
            String hash = PasswordHasher.hash("CorrectPassword");
            assertFalse(PasswordHasher.verify("WrongPassword", hash));
        }

        @Test
        @DisplayName("hash() throws IllegalArgumentException for blank input")
        void hashThrowsOnBlankInput() {
            assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hash(""));
            assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hash(null));
            assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hash("   "));
        }

        @Test
        @DisplayName("verify() throws IllegalArgumentException for malformed stored credential")
        void verifyThrowsOnMalformedCredential() {
            assertThrows(IllegalArgumentException.class,
                         () -> PasswordHasher.verify("pass", "invalid:format"));
        }

        @Test
        @DisplayName("Credential string contains exactly 4 colon-delimited segments")
        void hashHasFourSegments() {
            String hash = PasswordHasher.hash("TestPass1!");
            assertEquals(4, hash.split(":").length,
                         "Credential must have 4 segments: id:iterations:salt:hash");
        }
    }

    // =========================================================================
    // Role Tests
    // =========================================================================

    @Nested
    @DisplayName("Role hierarchy")
    class RoleTests {

        @Test
        @DisplayName("isAtLeast() reflects enum ordinal hierarchy")
        void roleHierarchy() {
            assertTrue(Role.ADMIN.isAtLeast(Role.CUSTOMER));
            assertTrue(Role.ADMIN.isAtLeast(Role.ADMIN));
            assertTrue(Role.BRANCH_MANAGER.isAtLeast(Role.TELLER));
            assertFalse(Role.CUSTOMER.isAtLeast(Role.TELLER));
            assertFalse(Role.TELLER.isAtLeast(Role.BRANCH_MANAGER));
        }

        @Test
        @DisplayName("All roles have non-blank label and description")
        void roleMetadata() {
            for (Role role : Role.values()) {
                assertNotNull(role.getLabel());
                assertFalse(role.getLabel().isBlank());
                assertNotNull(role.getDescription());
                assertFalse(role.getDescription().isBlank());
            }
        }
    }

    // =========================================================================
    // Permission Tests
    // =========================================================================

    @Nested
    @DisplayName("Permission resolution per role")
    class PermissionTests {

        @Test
        @DisplayName("CUSTOMER: has VIEW_OWN_ACCOUNT and TRANSFER but NOT DEPOSIT")
        void customerPermissions() {
            var perms = Permission.forRole(Role.CUSTOMER);
            assertTrue(perms.contains(Permission.VIEW_OWN_ACCOUNT));
            assertTrue(perms.contains(Permission.TRANSFER));
            assertFalse(perms.contains(Permission.DEPOSIT));
            assertFalse(perms.contains(Permission.WITHDRAW));
            assertFalse(perms.contains(Permission.OPEN_ACCOUNT));
        }

        @Test
        @DisplayName("TELLER: has DEPOSIT, WITHDRAW, TRANSFER, VIEW_ANY_ACCOUNT")
        void tellerPermissions() {
            var perms = Permission.forRole(Role.TELLER);
            assertTrue(perms.contains(Permission.DEPOSIT));
            assertTrue(perms.contains(Permission.WITHDRAW));
            assertTrue(perms.contains(Permission.TRANSFER));
            assertTrue(perms.contains(Permission.VIEW_ANY_ACCOUNT));
            assertFalse(perms.contains(Permission.OPEN_ACCOUNT));
        }

        @Test
        @DisplayName("BRANCH_MANAGER: has OPEN_ACCOUNT, FREEZE_ACCOUNT, READ_AUDIT_LOG")
        void branchManagerPermissions() {
            var perms = Permission.forRole(Role.BRANCH_MANAGER);
            assertTrue(perms.contains(Permission.OPEN_ACCOUNT));
            assertTrue(perms.contains(Permission.FREEZE_ACCOUNT));
            assertTrue(perms.contains(Permission.CLOSE_ACCOUNT));
            assertTrue(perms.contains(Permission.READ_AUDIT_LOG));
        }

        @Test
        @DisplayName("AUDITOR: read-only – no DEPOSIT / WITHDRAW / TRANSFER")
        void auditorPermissions() {
            var perms = Permission.forRole(Role.AUDITOR);
            assertTrue(perms.contains(Permission.READ_AUDIT_LOG));
            assertTrue(perms.contains(Permission.VIEW_ANY_ACCOUNT));
            assertFalse(perms.contains(Permission.DEPOSIT));
            assertFalse(perms.contains(Permission.WITHDRAW));
            assertFalse(perms.contains(Permission.TRANSFER));
            assertFalse(perms.contains(Permission.OPEN_ACCOUNT));
        }

        @Test
        @DisplayName("ADMIN: holds every single permission")
        void adminHasAllPermissions() {
            var perms = Permission.forRole(Role.ADMIN);
            for (Permission p : Permission.values()) {
                assertTrue(perms.contains(p),
                           "ADMIN should hold permission: " + p);
            }
        }
    }

    // =========================================================================
    // UserPrincipal Tests
    // =========================================================================

    @Nested
    @DisplayName("UserPrincipal")
    class UserPrincipalTests {

        @Test
        @DisplayName("Customer owns own account but not another customer's account")
        void ownershipCheck() {
            UserPrincipal cust = customer();
            assertTrue(cust.ownsOrIsStaff(CUSTOMER_ID));
            assertFalse(cust.ownsOrIsStaff(OTHER_CUST_ID));
        }

        @Test
        @DisplayName("Teller (staff) can access any account via ownsOrIsStaff")
        void staffBypassesOwnership() {
            assertTrue(teller().ownsOrIsStaff(CUSTOMER_ID));
            assertTrue(teller().ownsOrIsStaff(OTHER_CUST_ID));
            assertTrue(admin().ownsOrIsStaff(999));
        }

        @Test
        @DisplayName("MFA flag starts false and can be set to true")
        void mfaFlag() {
            UserPrincipal p = customer();
            assertFalse(p.isMfaVerified(), "MFA should be false on creation");
            p.markMfaVerified();
            assertTrue(p.isMfaVerified(), "MFA should be true after markMfaVerified()");
        }

        @Test
        @DisplayName("hasPermission and hasAllPermissions work correctly")
        void permissionHelpers() {
            UserPrincipal admin = admin();
            assertTrue(admin.hasPermission(Permission.MANAGE_USERS));
            assertTrue(admin.hasAllPermissions(Permission.DEPOSIT, Permission.WITHDRAW));

            UserPrincipal cust = customer();
            assertFalse(cust.hasPermission(Permission.DEPOSIT));
            assertFalse(cust.hasAllPermissions(Permission.DEPOSIT, Permission.TRANSFER));
        }
    }

    // =========================================================================
    // SecurityContext Tests
    // =========================================================================

    @Nested
    @DisplayName("SecurityContext")
    class SecurityContextTests {

        @Test
        @DisplayName("set() and get() store/retrieve the principal correctly")
        void setAndGet() {
            UserPrincipal p = customer();
            SecurityContext.set(p);
            assertSame(p, SecurityContext.get());
        }

        @Test
        @DisplayName("clear() removes the principal")
        void clearRemovesPrincipal() {
            SecurityContext.set(customer());
            SecurityContext.clear();
            assertNull(SecurityContext.get());
        }

        @Test
        @DisplayName("require() throws AuthenticationException when no principal is set")
        void requireThrowsWhenNotAuthenticated() {
            SecurityContext.clear();
            assertThrows(AuthenticationException.class, SecurityContext::require);
        }

        @Test
        @DisplayName("isAuthenticated() returns true/false correctly")
        void isAuthenticated() {
            SecurityContext.clear();
            assertFalse(SecurityContext.isAuthenticated());
            SecurityContext.set(customer());
            assertTrue(SecurityContext.isAuthenticated());
        }

        @Test
        @DisplayName("set() throws IllegalArgumentException for null principal")
        void setThrowsForNull() {
            assertThrows(IllegalArgumentException.class,
                         () -> SecurityContext.set(null));
        }
    }

    // =========================================================================
    // RbacPolicy Tests
    // =========================================================================

    @Nested
    @DisplayName("RbacPolicy")
    class RbacPolicyTests {

        private final RbacPolicy policy = new RbacPolicy();

        @Test
        @DisplayName("require() grants access when permission is held")
        void requireGrantsWhenPermissionHeld() {
            assertDoesNotThrow(() -> policy.require(teller(), Permission.DEPOSIT));
            assertDoesNotThrow(() -> policy.require(admin(), Permission.MANAGE_USERS));
        }

        @Test
        @DisplayName("require() denies access when permission is NOT held")
        void requireDeniesWhenPermissionMissing() {
            assertThrows(AuthorizationException.class,
                         () -> policy.require(customer(), Permission.DEPOSIT));
            assertThrows(AuthorizationException.class,
                         () -> policy.require(auditor(), Permission.TRANSFER));
        }

        @Test
        @DisplayName("requireViewAccount() allows customer to view own account")
        void requireViewAccountOwnAccount() {
            assertDoesNotThrow(() ->
                policy.requireViewAccount(customer(), CUSTOMER_ID));
        }

        @Test
        @DisplayName("requireViewAccount() denies customer access to other customer's account")
        void requireViewAccountOtherAccount() {
            assertThrows(AuthorizationException.class, () ->
                policy.requireViewAccount(customer(), OTHER_CUST_ID));
        }

        @Test
        @DisplayName("requireViewAccount() allows teller to view any account")
        void requireViewAccountTellerAny() {
            assertDoesNotThrow(() ->
                policy.requireViewAccount(teller(), OTHER_CUST_ID));
        }

        @Test
        @DisplayName("requireTransfer() allows customer to transfer from own account")
        void requireTransferOwnAccount() {
            assertDoesNotThrow(() ->
                policy.requireTransfer(customer(), CUSTOMER_ID));
        }

        @Test
        @DisplayName("requireTransfer() denies customer from transferring from other's account")
        void requireTransferOtherAccount() {
            assertThrows(AuthorizationException.class, () ->
                policy.requireTransfer(customer(), OTHER_CUST_ID));
        }

        @Test
        @DisplayName("requireMfa() throws AuthenticationException when MFA not verified")
        void requireMfaThrowsWhenNotVerified() {
            UserPrincipal p = admin();
            assertFalse(p.isMfaVerified());
            assertThrows(AuthenticationException.class,
                         () -> policy.requireMfa(p));
        }

        @Test
        @DisplayName("requireMfa() passes after markMfaVerified()")
        void requireMfaPassesAfterVerification() {
            UserPrincipal p = admin();
            p.markMfaVerified();
            assertDoesNotThrow(() -> policy.requireMfa(p));
        }

        @Test
        @DisplayName("requireReadAuditLog() denied for customer and teller")
        void requireReadAuditLogDenied() {
            assertThrows(AuthorizationException.class,
                         () -> policy.requireReadAuditLog(customer()));
            assertThrows(AuthorizationException.class,
                         () -> policy.requireReadAuditLog(teller()));
        }

        @Test
        @DisplayName("requireReadAuditLog() granted for manager, auditor, admin")
        void requireReadAuditLogGranted() {
            assertDoesNotThrow(() -> policy.requireReadAuditLog(manager()));
            assertDoesNotThrow(() -> policy.requireReadAuditLog(auditor()));
            assertDoesNotThrow(() -> policy.requireReadAuditLog(admin()));
        }
    }

    // =========================================================================
    // SecureBankingService Tests
    // =========================================================================

    @Nested
    @DisplayName("SecureBankingService")
    class SecureBankingServiceTests {

        @Mock BankingService mockBankingService;
        @Mock AuditService   mockAuditService;

        private SecureBankingService secureService;
        private RbacPolicy           realPolicy;

        @BeforeEach
        void setup() {
            realPolicy    = new RbacPolicy();
            secureService = new SecureBankingService(
                mockBankingService, realPolicy, mockAuditService);
        }

        // ── Unauthenticated access ────────────────────────────────────────────

        @Test
        @DisplayName("deposit() throws AuthenticationException when no session")
        void depositUnauthenticated() {
            SecurityContext.clear();
            assertThrows(AuthenticationException.class, () ->
                secureService.deposit(ACCOUNT_NUM, BigDecimal.TEN, "test"));
        }

        @Test
        @DisplayName("transfer() throws AuthenticationException when no session")
        void transferUnauthenticated() {
            SecurityContext.clear();
            assertThrows(AuthenticationException.class, () ->
                secureService.transfer(1001L, 1002L, BigDecimal.TEN, "test"));
        }

        @Test
        @DisplayName("getAuditLog() throws AuthenticationException when no session")
        void auditLogUnauthenticated() {
            SecurityContext.clear();
            assertThrows(AuthenticationException.class, () ->
                secureService.getAuditLog(10));
        }

        // ── RBAC denial ───────────────────────────────────────────────────────

        @Test
        @DisplayName("deposit() throws AuthorizationException for CUSTOMER role")
        void depositDeniedForCustomer() {
            SecurityContext.set(customer());
            assertThrows(AuthorizationException.class, () ->
                secureService.deposit(ACCOUNT_NUM, BigDecimal.TEN, "test"));
        }

        @Test
        @DisplayName("openAccount() throws AuthorizationException for TELLER role")
        void openAccountDeniedForTeller() {
            SecurityContext.set(teller());
            assertThrows(AuthorizationException.class, () ->
                secureService.openAccount(1, 1, AccountType.SAVINGS,
                                          BigDecimal.valueOf(500)));
        }

        @Test
        @DisplayName("getAuditLog() throws AuthorizationException for TELLER role")
        void auditLogDeniedForTeller() {
            SecurityContext.set(teller());
            assertThrows(AuthorizationException.class, () ->
                secureService.getAuditLog(10));
        }

        // ── Successful operations ─────────────────────────────────────────────

        @Test
        @DisplayName("deposit() succeeds for TELLER role and records audit event")
        void depositSucceedsForTeller() {
            SecurityContext.set(teller());
            when(mockBankingService.deposit(ACCOUNT_NUM,
                 BigDecimal.valueOf(500), "salary")).thenReturn(99L);

            long txId = secureService.deposit(ACCOUNT_NUM,
                                              BigDecimal.valueOf(500), "salary");

            assertEquals(99L, txId);
            verify(mockBankingService).deposit(ACCOUNT_NUM,
                                               BigDecimal.valueOf(500), "salary");
            verify(mockAuditService).logTransactionEvent(eq("DEPOSIT"),
                                                         eq(ACCOUNT_NUM), anyString());
        }

        @Test
        @DisplayName("openAccount() succeeds for BRANCH_MANAGER and records audit event")
        void openAccountSucceedsForManager() {
            SecurityContext.set(manager());
            when(mockBankingService.openAccount(1, 1, AccountType.SAVINGS,
                                                BigDecimal.valueOf(500)))
                .thenReturn(ACCOUNT_NUM);

            long acc = secureService.openAccount(1, 1, AccountType.SAVINGS,
                                                 BigDecimal.valueOf(500));

            assertEquals(ACCOUNT_NUM, acc);
            verify(mockAuditService).logEvent(eq("ACCOUNT_OPENED"),
                                              eq(ACCOUNT_NUM), anyString());
        }

        // ── MFA gate ──────────────────────────────────────────────────────────

        @Test
        @DisplayName("transfer() of high-value amount requires MFA step-up")
        void highValueTransferRequiresMfa() {
            SecurityContext.set(teller());   // teller has TRANSFER permission

            // Sender account returned by mock
            Account senderAcc = new Account(ACCOUNT_NUM, 10, 1,
                AccountType.SAVINGS, BigDecimal.valueOf(50_000),
                AccountStatus.ACTIVE, LocalDateTime.now());
            when(mockBankingService.getAccount(ACCOUNT_NUM)).thenReturn(senderAcc);

            BigDecimal highValue = new BigDecimal("15000.00");

            // Principal does NOT have MFA verified → should throw
            assertThrows(AuthenticationException.class, () ->
                secureService.transfer(ACCOUNT_NUM, 1002L, highValue, "high-value"));
        }

        @Test
        @DisplayName("transfer() of high-value amount succeeds after MFA step-up")
        void highValueTransferSucceedsWithMfa() {
            UserPrincipal tellerPrincipal = teller();
            tellerPrincipal.markMfaVerified();     // simulate completed MFA
            SecurityContext.set(tellerPrincipal);

            Account senderAcc = new Account(ACCOUNT_NUM, 10, 1,
                AccountType.SAVINGS, BigDecimal.valueOf(50_000),
                AccountStatus.ACTIVE, LocalDateTime.now());
            when(mockBankingService.getAccount(ACCOUNT_NUM)).thenReturn(senderAcc);

            BigDecimal highValue = new BigDecimal("15000.00");
            when(mockBankingService.transfer(ACCOUNT_NUM, 1002L, highValue, "hv"))
                .thenReturn(77L);

            long txId = secureService.transfer(ACCOUNT_NUM, 1002L, highValue, "hv");

            assertEquals(77L, txId);
            verify(mockAuditService).logTransactionEvent(
                eq("TRANSFER"), eq(ACCOUNT_NUM), anyString());
        }

        // ── Ownership enforcement ─────────────────────────────────────────────

        @Test
        @DisplayName("Customer cannot transfer from an account they don't own")
        void customerCannotTransferFromOtherAccount() {
            SecurityContext.set(customer());     // owns CUSTOMER_ID = 42

            // Sender account owned by customer 99 – NOT the logged-in customer
            Account otherAcc = new Account(ACCOUNT_NUM, OTHER_CUST_ID, 1,
                AccountType.SAVINGS, BigDecimal.valueOf(5_000),
                AccountStatus.ACTIVE, LocalDateTime.now());
            when(mockBankingService.getAccount(ACCOUNT_NUM)).thenReturn(otherAcc);

            assertThrows(AuthorizationException.class, () ->
                secureService.transfer(ACCOUNT_NUM, 1002L,
                                       BigDecimal.valueOf(100), "note"));
        }
    }

    // =========================================================================
    // AuthService – requiresMfa utility
    // =========================================================================

    @Nested
    @DisplayName("AuthService.requiresMfa threshold")
    class AuthServiceMfaTests {

        @Test
        @DisplayName("requiresMfa returns true for amounts >= 10000")
        void highValueThreshold() {
            assertTrue(AuthService.requiresMfa(new BigDecimal("10000.00")));
            assertTrue(AuthService.requiresMfa(new BigDecimal("10000.01")));
            assertTrue(AuthService.requiresMfa(new BigDecimal("99999.99")));
        }

        @Test
        @DisplayName("requiresMfa returns false for amounts < 10000")
        void normalValueThreshold() {
            assertFalse(AuthService.requiresMfa(new BigDecimal("9999.99")));
            assertFalse(AuthService.requiresMfa(BigDecimal.ONE));
            assertFalse(AuthService.requiresMfa(BigDecimal.ZERO));
            assertFalse(AuthService.requiresMfa(null));
        }
    }
}
