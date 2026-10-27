# Core Banking & Multi-Branch Transaction Ledger System

Academic project – **Member 2 scope**: Backend Engine (Java 17, HikariCP, PostgreSQL/Supabase).

---

## Prerequisites

| Tool | Version | Description |
|------|---------|-------------|
| **JDK**  | 17+     | Eclipse Adoptium / OpenJDK 17 or higher |
| **Maven**| 3.9+    | Apache Maven build tool |
| **PostgreSQL / Supabase** | 14+ | Cloud-hosted Supabase or local PostgreSQL instance |

---

## ⚙️ Configuration & Environment Setup

The application connects to PostgreSQL using HikariCP. Connection credentials can be provided in **either of two ways**:

### Option A: Using a `.env` file in the project root (Recommended)

Create or edit a `.env` file directly in the project root folder (note: `.env` is git-ignored):

```env
DB_URL=jdbc:postgresql://<host>:5432/<database>?sslmode=require
DB_USER=<username>
DB_PASSWORD=<password>
```

> **Note:** For Supabase, your JDBC URL format is:
> `jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres?sslmode=require`

### Option B: System Environment Variables

Alternatively, export the variables in your terminal:

**Windows PowerShell:**
```powershell
$env:DB_URL      = "jdbc:postgresql://<host>:5432/<database>?sslmode=require"
$env:DB_USER     = "<username>"
$env:DB_PASSWORD = "<password>"
```

**macOS / Linux (bash / zsh):**
```bash
export DB_URL="jdbc:postgresql://<host>:5432/<database>?sslmode=require"
export DB_USER="<username>"
export DB_PASSWORD="<password>"
```

---

## 🗄️ Database Initialization & Auto-Seeding

You **do not** need to manually run SQL scripts to get started:
- On startup, `DatabaseInitializer` automatically applies and verifies [sql/schema.sql](file:///c:/Users/rangb/OneDrive/Desktop/core-banking-system/sql/schema.sql) and [sql/security_schema.sql](file:///c:/Users/rangb/OneDrive/Desktop/core-banking-system/sql/security_schema.sql).
- `DemoUserSeeder` automatically provisions real PBKDF2-hashed accounts for testing.

*(Optional)* If you wish to execute the schema manually beforehand:
```bash
# Using psql:
psql "$DB_URL" -f sql/schema.sql
psql "$DB_URL" -f sql/security_schema.sql

# Or paste contents into the Supabase SQL Editor.
```

---

## 🚀 How to Run the Project

### 1. Build the Executable Fat-JAR

Run the Maven package command from the root directory:

```bash
mvn clean package -DskipTests
```

This compiles all classes and bundles dependencies into an executable fat-JAR at `target/core-banking-system-1.0.0.jar`.

---

### 2. Run the Web Application & REST API (Default Mode)

Run the packaged JAR:

```bash
java -jar target/core-banking-system-1.0.0.jar
```

Or run directly without building a JAR using Maven:

```bash
mvn compile exec:java -Dexec.mainClass="com.bank.system.main.ApplicationEntryPoint"
```

Once started, the application starts an embedded HTTP server (JDK `HttpServer` on port 8080):
- **Web Banking Portal UI**: [http://localhost:8080/](http://localhost:8080/)
- **Health Check Endpoint**: [http://localhost:8080/health](http://localhost:8080/health)
- **REST API Endpoints**: `/api/auth/*`, `/api/accounts/*`, `/api/transactions/*`, `/api/audit/*`

---

### 3. Demo User Accounts

Use any of the seeded credentials to log into the web portal at [http://localhost:8080/](http://localhost:8080/):

| Username | Password | Role | Description & Privileges |
|----------|----------|------|--------------------------|
| `admin` | `Admin@1234` | **ADMIN** | Full administrative rights, user & security management |
| `mgr01` | `Manager@1234` | **BRANCH_MANAGER** | Open accounts, adjust account status, audit log reviews |
| `teller01` | `Teller@1234` | **TELLER** | In-branch deposits and withdrawals |
| `audit01` | `Auditor@1234` | **AUDITOR** | Read-only compliance & security event audits |
| `alice` | `Customer@1234` | **CUSTOMER** | Customer ID 1 – Online banking, balance view, fund transfers |
| `bob` | `Customer@1234` | **CUSTOMER** | Customer ID 2 – Online banking, balance view, fund transfers |

---

### 4. Advanced Run Options

#### Custom Port
To run the server on a different port (e.g. `9090`):
```bash
java -jar target/core-banking-system-1.0.0.jar --port=9090
```

#### Console Smoke-Test Demo (CLI Mode)
To run the standalone console-based transaction demonstration (opens accounts, performs deposit, withdraw, transfer, and prints transaction history to console):
```bash
java -jar target/core-banking-system-1.0.0.jar --demo
```

---

## 🧪 Run Unit Tests (No Database Required)

Execute the 70+ automated unit tests covering business logic, concurrency ordering, RBAC policies, and cryptographic hashing:

```bash
mvn test
```

> All tests use Mockito mocks for the DAO and JDBC layers, so no external database connection is required to run the test suite.

---

## 📁 Project Structure

```
core-banking-system/
├── .env                                  # Local DB environment variables (git-ignored)
├── pom.xml                               # Maven build configuration and dependencies
├── sql/
│   ├── schema.sql                        # DDL: branches, customers, accounts, transactions, audit
│   └── security_schema.sql               # DDL: bank_users, security_audit_events
├── src/
│   ├── main/
│   │   ├── java/com/bank/system/
│   │   │   ├── api/                      # Member 4: REST API & Web Server
│   │   │   │   ├── controller/           # Auth, Account, Transaction, Customer, Branch, Audit controllers
│   │   │   │   ├── filter/               # AuthFilter (Session token validation)
│   │   │   │   ├── handler/              # StaticFileHandler (serves SPA web UI)
│   │   │   │   ├── session/              # SessionStore (in-memory token store)
│   │   │   │   └── ApiServer.java        # JDK HttpServer bootstrap
│   │   │   ├── config/                   # Member 2: Database & Connection Pool
│   │   │   │   ├── DatabaseConfig.java   # HikariCP singleton pool configuration
│   │   │   │   └── DatabaseInitializer.java # Auto-executes SQL schema on boot
│   │   │   ├── dao/                      # Member 2: Data Access Objects
│   │   │   │   ├── AccountDAO.java       # Account CRUD & balance operations
│   │   │   │   ├── BranchDAO.java        # Branch lookups
│   │   │   │   ├── CustomerDAO.java      # Customer data access
│   │   │   │   └── TransactionDAO.java   # Ledger recording and history query
│   │   │   ├── exception/                # Domain exception hierarchy
│   │   │   ├── model/                    # Domain entity POJOs & Enums
│   │   │   ├── security/                 # Member 3: RBAC & Audit Engine
│   │   │   │   ├── audit/                # Security audit logger
│   │   │   │   ├── auth/                 # PBKDF2 PasswordHasher & DemoUserSeeder
│   │   │   │   ├── guard/                # SecureBankingService (RBAC enforcement wrapper)
│   │   │   │   ├── rbac/                 # Permissions, Roles, Context
│   │   │   │   └── Role.java, Permission.java
│   │   │   ├── service/                  # Member 2: Core Banking Business Logic
│   │   │   │   └── BankingService.java   # Transfer, deposit, withdraw, balance
│   │   │   └── main/
│   │   │       └── ApplicationEntryPoint.java # Main entry point (HTTP server / --demo)
│   │   └── resources/
│   │       ├── logback.xml               # Logback logging config
│   │       └── static/                   # Member 4: Frontend Web UI
│   │           ├── index.html            # Single Page Banking Portal
│   │           ├── css/styles.css        # Modern glassmorphism UI styles
│   │           └── js/app.js             # Banking client application logic
│   └── test/java/com/bank/system/
│       ├── BankingServiceTest.java       # Mockito unit tests for banking operations
│       └── security/SecurityTest.java    # Security, RBAC & hashing unit tests
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


## Member 3 Roles

For a banking application, security should be designed as a separate architecture, not just “add authentication.” If you're aiming for a serious production-grade fintech/banking system, this is the security stack I'd choose.

🔐 Recommended banking security stack

Security area	Best choice	Purpose

Identity	Enterprise IAM / Keycloak	Central identity management
Authentication	OAuth 2.1 + OpenID Connect	Secure authentication
Strong MFA	FIDO2/WebAuthn passkeys	Phishing-resistant login
Authorization	RBAC + ABAC	Fine-grained permissions
API security	OAuth access tokens + mTLS where appropriate	Service/API protection
Passwords	Argon2id	Password hashing
Encryption in transit	TLS 1.3	Network encryption
Encryption at rest	AES-256	Database/storage encryption
Key management	Cloud KMS + HSM	Protect encryption/signing keys
Secrets	HashiCorp Vault / cloud secret manager	API/database credentials
WAF	AWS WAF / Cloudflare WAF	Block malicious web traffic
DDoS	AWS Shield / Cloudflare	DDoS protection
API Gateway	Kong / AWS API Gateway	API security/rate limiting
Fraud detection	Dedicated risk/fraud engine	Detect suspicious transactions
Audit	Immutable audit log + SIEM	Investigations/compliance
Monitoring	OpenTelemetry + SIEM	Detect attacks
Dependency security	Snyk / Dependabot	Vulnerable dependencies
Container security	Trivy	Scan Docker images
Code security	Semgrep + CodeQL	SAST
Secrets scanning	Gitleaks	Prevent leaked credentials
DAST	OWASP ZAP	Test running application
Pen testing	Professional security testing	Find real vulnerabilities
Infrastructure	AWS + Terraform + private networking	Secure deployment


The architecture I'd aim for

INTERNET
                       │
                       ▼
                ┌─────────────┐
                │ Cloudflare /│
                │ AWS Shield   │
                └──────┬──────┘
                       │
                       ▼
                 ┌───────────┐
                 │    WAF    │
                 └─────┬─────┘
                       │
                       ▼
                ┌─────────────┐
                │ API Gateway │
                └──────┬──────┘
                       │
             ┌─────────┴─────────┐
             │                   │
             ▼                   ▼
       Authentication       Application
       / Authorization        Services
             │                   │
             │             ┌─────┴─────┐
             │             │           │
             ▼             ▼           ▼
        IAM / OIDC       Ledger      Payments
        WebAuthn         Service     Service
             │             │           │
             └─────────────┼───────────┘
                           │
                    ┌──────▼──────┐
                    │ PostgreSQL  │
                    └──────┬──────┘
                           │
                  ┌────────▼────────┐
                  │ KMS / HSM       │
                  └─────────────────┘

             ┌─────────────────────────┐
             │ SIEM / Security Logs    │
             │ Audit / Monitoring      │
             └─────────────────────────┘

The most important security decisions

1. Don't rely on passwords alone

Use:

Passkeys/WebAuthn + MFA

rather than:

username + password

For sensitive operations, step-up authentication is important:

Login
  ↓
Passkey
  ↓
Normal access

Large transfer
  ↓
Step-up authentication
  ↓
Transaction authorization


---

2. Protect the transaction itself

This is more important than protecting the login page.

For every transfer, use:

- Transaction IDs
- Idempotency keys
- Authorization checks
- Atomic database transactions
- Concurrency controls
- Double-entry ledger
- Transaction limits
- Risk scoring
- Audit trail
- Reconciliation

For example:

User
 ↓
Authenticate
 ↓
Authorize
 ↓
Risk/Fraud check
 ↓
Idempotency check
 ↓
Ledger transaction
 ↓
Atomic commit
 ↓
Audit event
 ↓
Notification


---

3. Never trust the frontend

If your frontend says:

{
  "amount": 100
}

the backend must not assume that the user is allowed to transfer ₹100.

The backend must independently verify:

Who is the user?
      ↓
Does the account belong to them?
      ↓
Is the account active?
      ↓
Is the beneficiary allowed?
      ↓
Is the amount permitted?
      ↓
Is the transaction suspicious?
      ↓
Does the account have sufficient funds?
      ↓
Execute transaction


---

4. Use a proper secrets architecture

Never:

GitHub
   ↓
.env
   ↓
production secrets

Instead:

Application
     ↓
Secret Manager / Vault
     ↓
KMS
     ↓
HSM where required

Keys should have rotation, access controls and auditability.


---

5. Build security into your development pipeline

Every pull request should ideally go through:

Developer
   ↓
GitHub
   ↓
Secret scanning
   ↓
SAST
   ↓
Dependency scanning
   ↓
Container scanning
   ↓
Unit tests
   ↓
Integration tests
   ↓
Security tests
   ↓
Code review
   ↓
Deploy

A good toolchain:

GitHub + CodeQL + Semgrep + Gitleaks + Dependabot + Trivy + OWASP ZAP


---

🔥 If I had to choose only one security stack

For your project, I'd use:

SECURITY STACK

Identity
└── Keycloak / Enterprise IAM

Authentication
├── OAuth 2.1
├── OpenID Connect
├── WebAuthn / FIDO2
└── MFA

Authorization
├── RBAC
└── ABAC

Application
├── Spring Security
├── Input validation
├── CSRF protection
├── Rate limiting
├── Idempotency
└── Secure headers

Encryption
├── TLS 1.3
├── AES-256
├── Argon2id
└── KMS / HSM

Secrets
└── Vault / Cloud Secrets Manager

Network
├── WAF
├── DDoS protection
├── Private subnets
├── Security groups
└── mTLS where appropriate

Database
├── PostgreSQL
├── Encryption
├── Least privilege
├── Row-level controls where appropriate
└── Immutable audit trail

Detection
├── SIEM
├── OpenTelemetry
├── Security alerts
└── Fraud detection

Code
├── CodeQL
├── Semgrep
├── Gitleaks
├── Dependabot
└── Trivy

Testing
├── JUnit
├── Testcontainers
├── OWASP ZAP
├── Penetration testing
└── Threat modeling

One thing I'd change from my previous recommendation

For a banking system, don't make Supabase/Firebase the security foundation simply because they're convenient. They can be useful for prototypes, but for the core financial system I'd build around Spring Security + PostgreSQL + dedicated IAM + KMS/HSM + proper audit/fraud infrastructure.

And if this is going to handle real money, don't try to make it secure by asking an AI to generate security code. Use AI as a development/review assistant, but have experienced security engineers and financial-system specialists validate the architecture and implementation.