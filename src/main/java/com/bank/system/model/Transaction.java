package com.bank.system.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * POJO representing a row in the {@code transactions} table.
 *
 * <p>Either {@code senderAccount} or {@code receiverAccount} may be
 * {@code null} for DEPOSIT / WITHDRAWAL operations.
 */
public class Transaction {

    /** Mirrors the CHECK constraint in the DDL. */
    public enum TransactionType { DEPOSIT, WITHDRAWAL, TRANSFER, INTEREST }

    /** Mirrors the CHECK constraint in the DDL. */
    public enum TransactionStatus { PENDING, SUCCESS, FAILED }

    private long transactionId;
    private Long senderAccount;    // nullable – null for DEPOSIT
    private Long receiverAccount;  // nullable – null for WITHDRAWAL
    private BigDecimal amount;
    private TransactionType transactionType;
    private TransactionStatus status;
    private String referenceNote;
    private LocalDateTime createdAt;

    // ── Constructors ─────────────────────────────────────────────────────────

    public Transaction() {}

    public Transaction(long transactionId, Long senderAccount, Long receiverAccount,
                       BigDecimal amount, TransactionType transactionType,
                       TransactionStatus status, String referenceNote,
                       LocalDateTime createdAt) {
        this.transactionId   = transactionId;
        this.senderAccount   = senderAccount;
        this.receiverAccount = receiverAccount;
        this.amount          = amount;
        this.transactionType = transactionType;
        this.status          = status;
        this.referenceNote   = referenceNote;
        this.createdAt       = createdAt;
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public long getTransactionId()                           { return transactionId; }
    public void setTransactionId(long transactionId)         { this.transactionId = transactionId; }

    public Long getSenderAccount()                           { return senderAccount; }
    public void setSenderAccount(Long senderAccount)         { this.senderAccount = senderAccount; }

    public Long getReceiverAccount()                         { return receiverAccount; }
    public void setReceiverAccount(Long receiverAccount)     { this.receiverAccount = receiverAccount; }

    public BigDecimal getAmount()                            { return amount; }
    public void setAmount(BigDecimal amount)                 { this.amount = amount; }

    public TransactionType getTransactionType()              { return transactionType; }
    public void setTransactionType(TransactionType t)        { this.transactionType = t; }

    public TransactionStatus getStatus()                     { return status; }
    public void setStatus(TransactionStatus status)          { this.status = status; }

    public String getReferenceNote()                         { return referenceNote; }
    public void setReferenceNote(String referenceNote)       { this.referenceNote = referenceNote; }

    public LocalDateTime getCreatedAt()                      { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt)        { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "Transaction{id="    + transactionId  +
               ", sender="          + senderAccount   +
               ", receiver="        + receiverAccount +
               ", amount="          + amount          +
               ", type="            + transactionType +
               ", status="          + status          +
               ", note='"           + referenceNote   + '\'' +
               ", createdAt="       + createdAt       + '}';
    }
}
