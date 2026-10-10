package com.bank.system.model;

import java.time.LocalDateTime;

/**
 * POJO representing a row in the {@code customers} table.
 */
public class Customer {

    private int customerId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String nationalId;
    private String address;
    private LocalDateTime createdAt;

    // ── Constructors ─────────────────────────────────────────────────────────

    public Customer() {}

    public Customer(int customerId, String firstName, String lastName,
                    String email, String phone, String nationalId,
                    String address, LocalDateTime createdAt) {
        this.customerId = customerId;
        this.firstName  = firstName;
        this.lastName   = lastName;
        this.email      = email;
        this.phone      = phone;
        this.nationalId = nationalId;
        this.address    = address;
        this.createdAt  = createdAt;
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public int getCustomerId()                         { return customerId; }
    public void setCustomerId(int customerId)          { this.customerId = customerId; }

    public String getFirstName()                       { return firstName; }
    public void setFirstName(String firstName)         { this.firstName = firstName; }

    public String getLastName()                        { return lastName; }
    public void setLastName(String lastName)           { this.lastName = lastName; }

    public String getEmail()                           { return email; }
    public void setEmail(String email)                 { this.email = email; }

    public String getPhone()                           { return phone; }
    public void setPhone(String phone)                 { this.phone = phone; }

    public String getNationalId()                      { return nationalId; }
    public void setNationalId(String nationalId)       { this.nationalId = nationalId; }

    public String getAddress()                         { return address; }
    public void setAddress(String address)             { this.address = address; }

    public LocalDateTime getCreatedAt()                { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt)  { this.createdAt = createdAt; }

    /** Convenience: full name. */
    public String getFullName() {
        return firstName + " " + lastName;
    }

    @Override
    public String toString() {
        return "Customer{customerId=" + customerId +
               ", name='"      + getFullName() + '\'' +
               ", email='"     + email         + '\'' +
               ", phone='"     + phone         + '\'' +
               ", nationalId='"+ nationalId    + '\'' +
               ", createdAt="  + createdAt     + '}';
    }
}
