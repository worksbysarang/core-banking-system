# Core Banking & Multi-Branch Transaction Ledger System

Academic project – **Member 2 scope**: Backend Engine (Java 17, HikariCP, PostgreSQL/Supabase).

---

## Prerequisites

| Tool | Version |
|------|---------|
| JDK  | 17+     |
| Maven| 3.9+    |
| PostgreSQL (Supabase) | any recent |

---

## Environment Variables

**Never hard-code credentials.** Export these three variables before running or testing:

```bash
# Windows PowerShell
$env:DB_URL      = "jdbc:postgresql://<host>:5432/<database>?sslmode=require"
$env:DB_USER     = "<username>"
$env:DB_PASSWORD = "<password>"

# macOS / Linux (bash/zsh)
export DB_URL="jdbc:postgresql://<host>:5432/<database>?sslmode=require"
export DB_USER="<username>"
export DB_PASSWORD="<password>"
```

For a **Supabase** project the JDBC URL looks like:
```
jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres?sslmode=require
```

---

## Database Setup

Run the schema once against your Supabase database:

```bash
psql "$DB_URL" -f sql/schema.sql
```

Or paste `sql/schema.sql` directly into the Supabase SQL Editor.

---

## Build

```bash
mvn clean package -DskipTests
```

This produces `target/core-banking-system-1.0.0.jar` (executable fat-JAR).

---

## Run Unit Tests (no DB required)

```bash
mvn test
```

Tests use Mockito mocks – no database connection needed.

---

## Run the Demo (requires DB)

```bash
java -jar target/core-banking-system-1.0.0.jar
```

The demo in `ApplicationEntryPoint` opens two accounts, performs a deposit,
withdrawal, and transfer, then prints the transaction history.

---

## Project Structure

```
src/main/java/com/bank/system/
├── config/
│   └── DatabaseConfig.java        # HikariCP singleton
├── model/
│   ├── Branch.java
│   ├── Customer.java
│   ├── Account.java               # enums: AccountType, AccountStatus
│   └── Transaction.java           # enums: TransactionType, TransactionStatus
├── dao/
│   ├── AccountDAO.java            # CRUD + balance update
│   └── TransactionDAO.java        # insert + history query
├── service/
│   └── BankingService.java        # deposit / withdraw / transfer / balance / history
├── exception/
│   ├── BankingException.java      # root domain exception
│   ├── AccountNotFoundException.java
│   └── InsufficientBalanceException.java
└── main/
    └── ApplicationEntryPoint.java # demo harness

sql/
└── schema.sql                     # DDL + stored procedure + trigger + seed data

src/test/java/com/bank/system/
└── BankingServiceTest.java        # JUnit 5 + Mockito unit tests
```

---

## Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| `BigDecimal` for money | Eliminates floating-point rounding errors |
| HikariCP pool | Efficient connection reuse; single singleton instance |
| Credentials via env vars | Keeps secrets out of source control |
| JDBC transaction in Java | `setAutoCommit(false)` → `commit/rollback`; no COMMIT inside stored procedure |
| Account-lock ordering | Acquire locks on both accounts in ascending order to prevent deadlocks |
| DAO accepts `Connection` overload | Lets service layer share one transaction across multiple DAO calls |
| Custom exception hierarchy | `BankingException` > `AccountNotFoundException` / `InsufficientBalanceException` |

---

## Team Integration Notes

- **Member 3 (Security/RBAC)**: Wrap `BankingService` methods with authorization checks before execution.
- **Member 4 (UI/API)**: Inject or instantiate `BankingService` in controllers/servlets. All methods are thread-safe.
- **Member 1 (DBA)**: The stored procedure `process_fund_transfer` is defined in `sql/schema.sql`. It can be called via `CallableStatement` if required by the controller layer.
