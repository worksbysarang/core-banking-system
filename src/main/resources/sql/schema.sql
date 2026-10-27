-- ============================================================
-- Core Banking System – PostgreSQL Schema
-- Run this script once against your Supabase database.
-- ============================================================

-- 1. TABLES ---------------------------------------------------

CREATE TABLE IF NOT EXISTS branches (
    branch_id   SERIAL PRIMARY KEY,
    branch_name VARCHAR(100) NOT NULL,
    ifsc_code   VARCHAR(20)  UNIQUE NOT NULL,
    address     TEXT         NOT NULL,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS customers (
    customer_id SERIAL PRIMARY KEY,
    first_name  VARCHAR(50)  NOT NULL,
    last_name   VARCHAR(50)  NOT NULL,
    email       VARCHAR(100) UNIQUE NOT NULL,
    phone       VARCHAR(20)  UNIQUE NOT NULL,
    national_id VARCHAR(50)  UNIQUE NOT NULL,
    address     TEXT,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS accounts (
    account_number BIGSERIAL PRIMARY KEY,
    customer_id    INT NOT NULL REFERENCES customers(customer_id) ON DELETE RESTRICT,
    branch_id      INT NOT NULL REFERENCES branches(branch_id)   ON DELETE RESTRICT,
    account_type   VARCHAR(20) NOT NULL
                   CHECK (account_type IN ('SAVINGS', 'CHECKING', 'LOAN')),
    balance        NUMERIC(15, 2) NOT NULL DEFAULT 0.00
                   CHECK (balance >= 0.00),
    status         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                   CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS transactions (
    transaction_id   BIGSERIAL PRIMARY KEY,
    sender_account   BIGINT REFERENCES accounts(account_number),
    receiver_account BIGINT REFERENCES accounts(account_number),
    amount           NUMERIC(15, 2) NOT NULL CHECK (amount > 0.00),
    transaction_type VARCHAR(20)    NOT NULL
                     CHECK (transaction_type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'INTEREST')),
    status           VARCHAR(20)    NOT NULL DEFAULT 'SUCCESS'
                     CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    reference_note   VARCHAR(255),
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT check_sender_receiver_different
        CHECK (sender_account IS NULL
               OR receiver_account IS NULL
               OR sender_account <> receiver_account)
);

CREATE TABLE IF NOT EXISTS audit_logs (
    audit_id       BIGSERIAL PRIMARY KEY,
    action_type    VARCHAR(50) NOT NULL,
    account_number BIGINT,
    details        TEXT        NOT NULL,
    logged_at      TIMESTAMP   DEFAULT CURRENT_TIMESTAMP
);

-- 2. INDEXES --------------------------------------------------

CREATE INDEX IF NOT EXISTS idx_txn_sender_date
    ON transactions(sender_account, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_txn_receiver_date
    ON transactions(receiver_account, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_customer_email ON customers(email);
CREATE INDEX IF NOT EXISTS idx_customer_phone ON customers(phone);

-- 3. STORED PROCEDURE: atomic fund transfer -------------------
--
-- NOTE: Java controls the JDBC transaction boundary.
-- Do NOT add COMMIT / ROLLBACK inside this procedure.
-- The Java layer calls setAutoCommit(false), then CALL, then
-- commit() on success or rollback() on failure.

CREATE OR REPLACE PROCEDURE process_fund_transfer(
    p_sender_acc   BIGINT,
    p_receiver_acc BIGINT,
    p_amount       NUMERIC(15, 2),
    p_note         VARCHAR(255)
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_sender_balance  NUMERIC(15, 2);
    v_sender_status   VARCHAR(20);
    v_receiver_status VARCHAR(20);
BEGIN
    IF p_sender_acc = p_receiver_acc THEN
        RAISE EXCEPTION 'Sender and receiver accounts must be different.';
    END IF;

    IF p_amount IS NULL OR p_amount <= 0 THEN
        RAISE EXCEPTION 'Transfer amount must be greater than zero.';
    END IF;

    -- Lock both accounts in ascending order to avoid deadlocks.
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

    INSERT INTO transactions
        (sender_account, receiver_account, amount,
         transaction_type, status, reference_note)
    VALUES
        (p_sender_acc, p_receiver_acc, p_amount,
         'TRANSFER', 'SUCCESS', p_note);
END;
$$;

-- 4. TRIGGER: flag high-value transactions --------------------

CREATE OR REPLACE FUNCTION flag_high_value_transaction()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.amount >= 10000.00 THEN
        INSERT INTO audit_logs (action_type, account_number, details)
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

CREATE OR REPLACE TRIGGER trg_high_value_flag
AFTER INSERT ON transactions
FOR EACH ROW
EXECUTE FUNCTION flag_high_value_transaction();

-- 5. SEED DATA (optional – remove for production) -------------

INSERT INTO branches (branch_name, ifsc_code, address)
VALUES
    ('Main Branch',  'BANK0001001', '1 Main Street, Mumbai'),
    ('North Branch', 'BANK0001002', '45 North Road, Delhi')
ON CONFLICT (ifsc_code) DO NOTHING;

INSERT INTO customers (first_name, last_name, email, phone, national_id, address)
VALUES
    ('Alice', 'Smith', 'alice@example.com', '9000000001', 'NID100001', '10 Elm St, Mumbai'),
    ('Bob',   'Jones', 'bob@example.com',   '9000000002', 'NID100002', '22 Oak Ave, Delhi')
ON CONFLICT DO NOTHING;
