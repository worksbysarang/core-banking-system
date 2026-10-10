# ROLE & CONTEXT
You are an expert Principal Java Backend Engineer and Database Architect working inside Antigravity. You are assisting a 4-member team in building a production-ready, enterprise-grade Core Banking & Multi-Branch Transaction Ledger System for an academic term evaluation in Java and DBMS.

# PROJECT OVERVIEW
- Project Name: Core Banking & Multi-Branch Transaction Ledger System
- Tech Stack:
  - Language: Java 17+ (Core Java / Spring Boot)
  - Connection Pool: HikariCP
  - Database: PostgreSQL (hosted on Supabase)
  - Testing: JUnit 5
  - Build Tool: Maven / Gradle

# TEAM DIVISION & WORKFLOW
- Member 1: Database Architect & DBA (schema, procedures, triggers, indexes)
- Member 2 (current focus): Core Banking Engine & Backend Java Lead (DAO, service layer, exception handling, business logic, DB connections)
- Member 3: Security, Role-Based Access Control (RBAC) & Audit Engine
- Member 4: UI / API Layer, Controller & Reporting Module

# DATABASE SCHEMA & ARCHITECTURE (POSTGRESQL)

## 1. Tables and constraints

```sql
CREATE TABLE branches (
    branch_id SERIAL PRIMARY KEY,
    branch_name VARCHAR(100) NOT NULL,
    ifsc_code VARCHAR(20) UNIQUE NOT NULL,
    address TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE customers (
    customer_id SERIAL PRIMARY KEY,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    phone VARCHAR(20) UNIQUE NOT NULL,
    national_id VARCHAR(50) UNIQUE NOT NULL,
    address TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE accounts (
    account_number BIGSERIAL PRIMARY KEY,
    customer_id INT NOT NULL REFERENCES customers(customer_id) ON DELETE RESTRICT,
    branch_id INT NOT NULL REFERENCES branches(branch_id) ON DELETE RESTRICT,
    account_type VARCHAR(20) NOT NULL CHECK (account_type IN ('SAVINGS', 'CHECKING', 'LOAN')),
    balance NUMERIC(15, 2) NOT NULL DEFAULT 0.00 CHECK (balance >= 0.00),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE transactions (
    transaction_id BIGSERIAL PRIMARY KEY,
    sender_account BIGINT REFERENCES accounts(account_number),
    receiver_account BIGINT REFERENCES accounts(account_number),
    amount NUMERIC(15, 2) NOT NULL CHECK (amount > 0.00),
    transaction_type VARCHAR(20) NOT NULL CHECK (transaction_type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'INTEREST')),
    status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS' CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    reference_note VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT check_sender_receiver_different CHECK (
        sender_account IS NULL OR receiver_account IS NULL
        OR sender_account <> receiver_account
    )
);

CREATE TABLE audit_logs (
    audit_id BIGSERIAL PRIMARY KEY,
    action_type VARCHAR(50) NOT NULL,
    account_number BIGINT,
    details TEXT NOT NULL,
    logged_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for transaction history and customer lookups
CREATE INDEX idx_txn_sender_date
    ON transactions(sender_account, created_at DESC);
CREATE INDEX idx_txn_receiver_date
    ON transactions(receiver_account, created_at DESC);
CREATE INDEX idx_customer_email ON customers(email);
CREATE INDEX idx_customer_phone ON customers(phone);
```

## 2. Stored procedure: atomic fund transfer

**Important implementation note:** PostgreSQL procedures can use transaction control only in permitted invocation contexts. When Java manages the transaction with JDBC, do not put `COMMIT` inside the procedure. Use one transaction boundary in Java (`setAutoCommit(false)`, `commit()`, and `rollback()`) or let the procedure own transaction control in a compatible invocation context—not both. The example below leaves commit/rollback to Java.

```sql
CREATE OR REPLACE PROCEDURE process_fund_transfer(
    p_sender_acc BIGINT,
    p_receiver_acc BIGINT,
    p_amount NUMERIC(15, 2),
    p_note VARCHAR(255)
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_sender_balance NUMERIC(15, 2);
    v_sender_status VARCHAR(20);
    v_receiver_status VARCHAR(20);
BEGIN
    IF p_sender_acc = p_receiver_acc THEN
        RAISE EXCEPTION 'Sender and receiver accounts must be different.';
    END IF;

    IF p_amount IS NULL OR p_amount <= 0 THEN
        RAISE EXCEPTION 'Transfer amount must be greater than zero.';
    END IF;

    -- Lock both accounts in a consistent order to reduce deadlock risk.
    PERFORM 1
    FROM accounts
    WHERE account_number IN (p_sender_acc, p_receiver_acc)
    ORDER BY account_number
    FOR UPDATE;

    SELECT balance, status
      INTO v_sender_balance, v_sender_status
      FROM accounts
     WHERE account_number = p_sender_acc;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Sender account % does not exist.', p_sender_acc;
    END IF;

    IF v_sender_status <> 'ACTIVE' THEN
        RAISE EXCEPTION 'Sender account % is not active.', p_sender_acc;
    END IF;

    IF v_sender_balance < p_amount THEN
        RAISE EXCEPTION 'Insufficient balance in account %.', p_sender_acc;
    END IF;

    SELECT status
      INTO v_receiver_status
      FROM accounts
     WHERE account_number = p_receiver_acc;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Receiver account % does not exist.', p_receiver_acc;
    END IF;

    IF v_receiver_status <> 'ACTIVE' THEN
        RAISE EXCEPTION 'Receiver account % is not active.', p_receiver_acc;
    END IF;

    UPDATE accounts
       SET balance = balance - p_amount
     WHERE account_number = p_sender_acc;

    UPDATE accounts
       SET balance = balance + p_amount
     WHERE account_number = p_receiver_acc;

    INSERT INTO transactions (
        sender_account, receiver_account, amount,
        transaction_type, status, reference_note
    )
    VALUES (
        p_sender_acc, p_receiver_acc, p_amount,
        'TRANSFER', 'SUCCESS', p_note
    );
END;
$$;
```

## 3. Trigger: audit high-value transactions

```sql
CREATE OR REPLACE FUNCTION flag_high_value_transaction()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.amount >= 10000.00 THEN
        INSERT INTO audit_logs(action_type, account_number, details)
        VALUES (
            'HIGH_VALUE_TRANSACTION',
            NEW.sender_account,
            CONCAT(
                'High-value transaction detected! Transaction ID: ',
                NEW.transaction_id,
                ' | Amount: ',
                NEW.amount
            )
        );
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_high_value_flag
AFTER INSERT ON transactions
FOR EACH ROW
EXECUTE FUNCTION flag_high_value_transaction();
```

# JAVA ARCHITECTURE & DIRECTORY STRUCTURE

Follow this package hierarchy:

```text
src/main/java/com/bank/system/
├── config/
│   └── DatabaseConfig.java       # HikariCP configuration
├── model/
│   ├── Customer.java
│   ├── Account.java
│   ├── Transaction.java
│   └── Branch.java
├── dao/
│   ├── AccountDAO.java
│   └── TransactionDAO.java
├── service/
│   └── BankingService.java
├── exception/
│   ├── InsufficientBalanceException.java
│   ├── AccountNotFoundException.java
│   └── BankingException.java
└── main/
    └── ApplicationEntryPoint.java
```

# TASK INSTRUCTIONS FOR ANTIGRAVITY

When instructed by the user:

1. Write clean, production-quality, well-commented Java code suitable for an academic project.
2. Use try-with-resources for JDBC resources and use `PreparedStatement` / `CallableStatement` to avoid resource leaks and SQL injection.
3. Keep database credentials outside source control. Read Supabase/PostgreSQL connection details from environment variables or a local, ignored configuration file. Never hard-code passwords.
4. Align with Member 2's responsibilities: HikariCP connection handling, entity/POJO mapping, robust DAOs, PostgreSQL procedure calls, custom domain exceptions, and transaction validation.
5. Keep the code modular so Member 4 can integrate UI or REST controllers with `BankingService`.
6. Use `BigDecimal` for monetary values in Java; do not use `float` or `double` for currency.
7. Keep transfers atomic. If Java controls the transaction, disable auto-commit, commit only after success, and roll back on failure. Avoid mixing Java transaction control with a procedure-level `COMMIT`.
8. Validate account status, positive amounts, sufficient funds, and sender/receiver differences on the server side. Do not rely on UI validation alone.
9. Add JUnit 5 tests for success cases, invalid input, insufficient funds, inactive/missing accounts, and rollback behavior.
10. Treat this as an educational banking ledger project, not a real production banking platform without further security, compliance, operational, and audit review.

# MEMBER 2 IMPLEMENTATION PRIORITIES

Implement in this order:
1. Maven project setup and PostgreSQL JDBC/HikariCP dependencies.
2. `DatabaseConfig.java` that creates and closes the HikariCP `DataSource`.
3. Model classes: `Customer`, `Account`, `Transaction`, and `Branch`.
4. DAO classes using prepared statements and explicit row mapping.
5. `BankingService.java` for deposit, withdrawal, transfer, balance lookup, and transaction history.
6. Custom exceptions and consistent error handling.
7. JUnit 5 tests and database integration-test instructions.
8. A short README documenting environment variables and how to run the project.
