package com.bank.system.model;

import java.time.LocalDateTime;

/**
 * POJO representing a row in the {@code branches} table.
 */
public class Branch {

    private int branchId;
    private String branchName;
    private String ifscCode;
    private String address;
    private LocalDateTime createdAt;

    // ── Constructors ─────────────────────────────────────────────────────────

    public Branch() {}

    public Branch(int branchId, String branchName, String ifscCode,
                  String address, LocalDateTime createdAt) {
        this.branchId   = branchId;
        this.branchName = branchName;
        this.ifscCode   = ifscCode;
        this.address    = address;
        this.createdAt  = createdAt;
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public int getBranchId()                   { return branchId; }
    public void setBranchId(int branchId)      { this.branchId = branchId; }

    public String getBranchName()                        { return branchName; }
    public void setBranchName(String branchName)         { this.branchName = branchName; }

    public String getIfscCode()                          { return ifscCode; }
    public void setIfscCode(String ifscCode)             { this.ifscCode = ifscCode; }

    public String getAddress()                           { return address; }
    public void setAddress(String address)               { this.address = address; }

    public LocalDateTime getCreatedAt()                  { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt)    { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "Branch{branchId=" + branchId +
               ", branchName='" + branchName + '\'' +
               ", ifscCode='"  + ifscCode  + '\'' +
               ", address='"   + address   + '\'' +
               ", createdAt="  + createdAt + '}';
    }
}
