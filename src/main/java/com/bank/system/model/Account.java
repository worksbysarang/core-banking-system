package com.bank.system.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * POJO representing a row in the {@code accounts} table.
 *
 * <p>Monetary values use {@link BigDecimal} to avoid floating-point rounding
 * errors.  Never substitute {@code float} or {@code double} for currency.
 */
public class Account {

    /** Mirrors the CHECK constraint in the DDL. */
    public enum AccountType { SAVINGS, CHECKING, LOAN }

    /** Mirrors the CHECK constraint in the DDL. */
    public enum AccountStatus { ACTIVE, FROZEN, CLOSED }

    private long accountNumber;
    private int customerId;
    private int branchId;
    private AccountType accountType;
    private BigDecimal balance;
    private AccountStatus status;
    private LocalDateTime createdAt;

    // ── Constructors ─────────────────────────────────────────────────────────

    public Account() {}

    public Account(long accountNumber, int customerId, int branchId,
                   AccountType accountType, BigDecimal balance,
                   AccountStatus status, LocalDateTime createdAt) {
        this.accountNumber = accountNumber;
        this.customerId    = customerId;
        this.branchId      = branchId;
        this.accountType   = accountType;
        this.balance       = balance;
        this.status        = status;
        this.createdAt     = createdAt;
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public long getAccountNumber()                         { return accountNumber; }
    public void setAccountNumber(long accountNumber)       { this.accountNumber = accountNumber; }

    public int getCustomerId()                             { return customerId; }
    public void setCustomerId(int customerId)              { this.customerId = customerId; }

    public int getBranchId()                               { return branchId; }
    public void setBranchId(int branchId)                  { this.branchId = branchId; }

    public AccountType getAccountType()                    { return accountType; }
    public void setAccountType(AccountType accountType)    { this.accountType = accountType; }

    public BigDecimal getBalance()                         { return balance; }
    public void setBalance(BigDecimal balance)             { this.balance = balance; }

    public AccountStatus getStatus()                       { return status; }
    public void setStatus(AccountStatus status)            { this.status = status; }

    public LocalDateTime getCreatedAt()                    { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt)      { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "Account{accountNumber=" + accountNumber +
               ", customerId="    + customerId    +
               ", branchId="      + branchId      +
               ", type="          + accountType   +
               ", balance="       + balance       +
               ", status="        + status        +
               ", createdAt="     + createdAt     + '}';
    }
}
