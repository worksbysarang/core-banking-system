"""
Executive-grade Presentation Summary PDF Generator for the
Core Banking & Multi-Branch Transaction Ledger System (Meridian Ledger).
Creates a 4-page structured presentation report.
"""

import os
from reportlab.lib.pagesizes import letter
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, PageBreak, HRFlowable
)
from reportlab.pdfgen import canvas

class NumberedCanvas(canvas.Canvas):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self._saved_page_states = []

    def showPage(self):
        self._saved_page_states.append(dict(self.__dict__))
        self._startPage()

    def save(self):
        num_pages = len(self._saved_page_states)
        for state in self._saved_page_states:
            self.__dict__.update(state)
            self.draw_page_decorations(num_pages)
            super().showPage()
        super().save()

    def draw_page_decorations(self, page_count):
        self.saveState()
        self.setFont("Helvetica", 8)
        self.setFillColor(colors.HexColor("#64748B"))

        # Running Header (all pages)
        self.drawString(40, letter[1] - 28, "Core Banking & Multi-Branch Transaction Ledger System")
        self.drawRightString(letter[0] - 40, letter[1] - 28, "Project Summary & Presentation Guide")
        self.setStrokeColor(colors.HexColor("#CBD5E1"))
        self.setLineWidth(0.5)
        self.line(40, letter[1] - 32, letter[0] - 40, letter[1] - 32)

        # Running Footer
        self.line(40, 32, letter[0] - 40, 32)
        self.drawString(40, 22, "Academic Term Project · Java 17, HikariCP, PostgreSQL & RBAC Security Engine")
        page_str = f"Page {self._pageNumber} of {page_count}"
        self.drawRightString(letter[0] - 40, 22, page_str)
        self.restoreState()


def create_summary_pdf(output_filename="Core_Banking_System_Project_Presentation_Summary.pdf"):
    # 40pt margins = 532pt printable width (letter is 612 x 792)
    pdf = SimpleDocTemplate(
        output_filename,
        pagesize=letter,
        leftMargin=40,
        rightMargin=40,
        topMargin=42,
        bottomMargin=42
    )

    styles = getSampleStyleSheet()

    # Color tokens
    c_primary = colors.HexColor("#0F172A")    # Slate 900
    c_blue    = colors.HexColor("#1D4ED8")    # Blue 700
    c_blue_bg = colors.HexColor("#EFF6FF")    # Blue 50
    c_dark    = colors.HexColor("#334155")    # Slate 700
    c_light   = colors.HexColor("#F8FAFC")    # Slate 50
    c_border  = colors.HexColor("#CBD5E1")    # Slate 300
    c_border_light = colors.HexColor("#E2E8F0")

    # Typography styles
    title_style = ParagraphStyle(
        'CoverTitle',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=20,
        leading=23,
        textColor=c_primary,
        spaceAfter=4
    )

    subtitle_style = ParagraphStyle(
        'CoverSubtitle',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=10,
        leading=13,
        textColor=c_blue,
        spaceAfter=8
    )

    h1_style = ParagraphStyle(
        'SectionH1',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=12,
        leading=15,
        textColor=c_primary,
        spaceBefore=7,
        spaceAfter=4,
        keepWithNext=True
    )

    h2_style = ParagraphStyle(
        'SectionH2',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=9.5,
        leading=12,
        textColor=c_blue,
        spaceBefore=5,
        spaceAfter=3,
        keepWithNext=True
    )

    body_style = ParagraphStyle(
        'BodyDark',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=8,
        leading=10.5,
        textColor=c_dark,
        spaceAfter=4
    )

    bullet_style = ParagraphStyle(
        'BulletText',
        parent=body_style,
        leftIndent=10,
        firstLineIndent=-7,
        spaceAfter=2
    )

    table_header_style = ParagraphStyle(
        'TableHeader',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=8,
        leading=9.5,
        textColor=colors.white
    )

    table_cell_style = ParagraphStyle(
        'TableCell',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=7.5,
        leading=9.5,
        textColor=c_dark
    )

    table_cell_bold = ParagraphStyle(
        'TableCellBold',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=7.5,
        leading=9.5,
        textColor=c_primary
    )

    code_style = ParagraphStyle(
        'CodeSnippet',
        parent=styles['Normal'],
        fontName='Courier',
        fontSize=7.2,
        leading=9,
        textColor=colors.HexColor("#0F172A")
    )

    story = []

    # =========================================================================
    # PAGE 1: PROJECT OVERVIEW & ARCHITECTURE
    # =========================================================================
    top_badge = Table([[
        Paragraph("<b>ACADEMIC TERM EVALUATION &amp; ARCHITECTURAL SPECIFICATION</b>", ParagraphStyle('B1', fontName='Helvetica-Bold', fontSize=7.5, textColor=c_blue)),
        Paragraph("<b>STATUS: PRODUCTION-READY (70/70 TESTS PASSING)</b>", ParagraphStyle('B2', fontName='Helvetica-Bold', fontSize=7.5, textColor=colors.HexColor("#047857"), alignment=2))
    ]], colWidths=[310, 222])
    top_badge.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,-1), c_blue_bg),
        ('BOX', (0,0), (-1,-1), 0.5, colors.HexColor("#BFDBFE")),
        ('TOPPADDING', (0,0), (-1,-1), 3),
        ('BOTTOMPADDING', (0,0), (-1,-1), 3),
        ('LEFTPADDING', (0,0), (-1,-1), 6),
        ('RIGHTPADDING', (0,0), (-1,-1), 6),
    ]))
    story.append(top_badge)
    story.append(Spacer(1, 6))

    story.append(Paragraph("Core Banking &amp; Multi-Branch Transaction Ledger System", title_style))
    story.append(Paragraph("Comprehensive Project Architecture, Concurrency Controls, RBAC Security, and Presentation Guide", subtitle_style))
    story.append(HRFlowable(width="100%", thickness=1, color=c_blue, spaceBefore=0, spaceAfter=6))

    # Meta specs table
    meta_data = [
        [
            Paragraph("<b>Core Tech Stack:</b>", table_cell_bold),
            Paragraph("Java 17 (JDK LTS), HikariCP 5.1.0, PostgreSQL (Supabase Cloud), Gson 2.11, SLF4J + Logback, JUnit 5 + Mockito", table_cell_style)
        ],
        [
            Paragraph("<b>4-Tier Architecture:</b>", table_cell_bold),
            Paragraph("Embedded REST API (JDK HttpServer) &rarr; RBAC Security Guard Proxy &rarr; Core Banking Service &rarr; HikariCP DAO &rarr; PostgreSQL (DDL + PL/pgSQL)", table_cell_style)
        ],
        [
            Paragraph("<b>Web Banking Portal:</b>", table_cell_bold),
            Paragraph("Single-Page Application (HTML5, Vanilla CSS3, Modern ES6+ JavaScript, zero external CDN dependencies, self-hosted)", table_cell_style)
        ],
        [
            Paragraph("<b>Quality Assurance:</b>", table_cell_bold),
            Paragraph("70 automated unit tests (100% passing) verifying deadlock avoidance, overdraft protection, RBAC rules, and PBKDF2 hashing", table_cell_style)
        ]
    ]
    meta_table = Table(meta_data, colWidths=[105, 427])
    meta_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,-1), c_light),
        ('BOX', (0,0), (-1,-1), 0.5, c_border_light),
        ('INNERGRID', (0,0), (-1,-1), 0.5, colors.HexColor("#F1F5F9")),
        ('TOPPADDING', (0,0), (-1,-1), 3),
        ('BOTTOMPADDING', (0,0), (-1,-1), 3),
        ('LEFTPADDING', (0,0), (-1,-1), 6),
        ('RIGHTPADDING', (0,0), (-1,-1), 6),
    ]))
    story.append(meta_table)
    story.append(Spacer(1, 8))

    story.append(Paragraph("1. Executive Summary &amp; Core Value Proposition", h1_style))
    story.append(Paragraph(
        "Financial core ledgers require mathematical guarantees: zero balance drift, strict serializability, deterministic transaction "
        "concurrency without deadlocks, and an untamperable audit trail. The <b>Meridian Core Banking System</b> achieves academic rigor "
        "and production stability by integrating low-latency Java 17 service engineering with PostgreSQL enterprise database primitives.",
        body_style
    ))

    exec_pillars = [
        [
            Paragraph("<b>Atomic Financial Ledger</b>", table_cell_bold),
            Paragraph("<b>Deadlock-Free Two-Phase Locks</b>", table_cell_bold),
            Paragraph("<b>5-Tier RBAC &amp; Step-Up MFA</b>", table_cell_bold)
        ],
        [
            Paragraph("All money amounts use immutable <code>BigDecimal</code> arithmetic. Transfers execute within strict JDBC boundaries with automatic rollback on any failure and trigger auditing.", table_cell_style),
            Paragraph("Transfers lock accounts in deterministic primary key order (<code>min(A,B)</code> then <code>max(A,B)</code>) via <code>FOR UPDATE</code>, mathematically preventing circular waits.", table_cell_style),
            Paragraph("PBKDF2-HMAC-SHA256 password hashing (65,536 iterations, 16-byte salt), 5 distinct roles, 5-attempt brute-force lockout, and step-up MFA for transfers &ge; Rs. 10,000.", table_cell_style)
        ]
    ]
    exec_table = Table(exec_pillars, colWidths=[177, 177, 178])
    exec_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,-1), colors.HexColor("#F8FAFC")),
        ('BOX', (0,0), (-1,-1), 0.5, c_border_light),
        ('LINEBEFORE', (1,0), (1,-1), 0.5, c_border_light),
        ('LINEBEFORE', (2,0), (2,-1), 0.5, c_border_light),
        ('LINEBELOW', (0,0), (-1,0), 0.5, c_border_light),
        ('TOPPADDING', (0,0), (-1,-1), 4),
        ('BOTTOMPADDING', (0,0), (-1,-1), 4),
        ('LEFTPADDING', (0,0), (-1,-1), 5),
        ('RIGHTPADDING', (0,0), (-1,-1), 5),
    ]))
    story.append(exec_table)
    story.append(Spacer(1, 8))

    story.append(Paragraph("2. Concurrency &amp; Deadlock Prevention Mechanism", h1_style))
    story.append(Paragraph(
        "<b>The Challenge:</b> In a multi-threaded banking system, if Customer 1 transfers money to Customer 2 while Customer 2 transfers to Customer 1, "
        "concurrent threads acquire opposing locks, producing an immediate database deadlock.<br/>"
        "<b>The Algorithm:</b> In <code>BankingService.java</code>, the system computes numerical account order prior to acquiring row locks:<br/>"
        "&nbsp;&nbsp;&bull; <code>long firstLock  = Math.min(fromAccount, toAccount);</code><br/>"
        "&nbsp;&nbsp;&bull; <code>long secondLock = Math.max(fromAccount, toAccount);</code><br/>"
        "Because both transactions acquire locks in strictly increasing numerical sequence (#1001 then #1002), Coffman's circular wait condition is eliminated.",
        body_style
    ))

    story.append(PageBreak())

    # =========================================================================
    # PAGE 2: TEAM RESPONSIBILITIES & ENGINEERING CONTRIBUTIONS
    # =========================================================================
    story.append(Paragraph("3. Team Division &amp; Core Architectural Responsibilities", h1_style))
    story.append(Paragraph(
        "The project is structured according to real-world software engineering roles, with clean layer boundaries and modular contracts:",
        body_style
    ))

    team_data = [
        [
            Paragraph("Role &amp; Owner", table_header_style),
            Paragraph("Architectural Scope", table_header_style),
            Paragraph("Key Responsibilities &amp; Code Deliverables", table_header_style)
        ],
        [
            Paragraph("<b>Member 1</b><br/>Database Architect &amp; DBA", table_cell_bold),
            Paragraph("Relational schema design (3NF), declarative constraints, stored procedures, audit triggers, and query performance indexing.", table_cell_style),
            Paragraph(
                "&bull; <code>sql/schema.sql</code>: DDL for <code>branches</code>, <code>customers</code>, <code>accounts</code>, <code>transactions</code>, <code>audit_logs</code>.<br/>"
                "&bull; <code>process_fund_transfer</code>: PL/pgSQL stored procedure with atomic row locks.<br/>"
                "&bull; <code>trg_high_value_flag</code>: Trigger auditing high-value transactions (&ge; Rs. 10,000).<br/>"
                "&bull; B-Tree indexes on <code>transactions(sender, date)</code> and customer search fields.",
                table_cell_style
            )
        ],
        [
            Paragraph("<b>Member 2</b><br/>Core Banking Engine &amp; Java Lead", table_cell_bold),
            Paragraph("Database connection pooling singleton, domain entities, transaction orchestration, deadlock prevention, and domain exceptions.", table_cell_style),
            Paragraph(
                "&bull; <code>DatabaseConfig.java</code>: Thread-safe HikariCP singleton with env/.env file fallback.<br/>"
                "&bull; <code>BankingService.java</code>: ACID deposit, withdraw, balance, and atomic transfer logic.<br/>"
                "&bull; <code>AccountDAO.java</code> &amp; <code>TransactionDAO.java</code>: PreparedStatements &amp; row mappers.<br/>"
                "&bull; Custom Exceptions: <code>InsufficientBalanceException</code>, <code>AccountNotFoundException</code>.<br/>"
                "&bull; <code>BankingServiceTest.java</code>: Comprehensive JUnit 5 + Mockito test suite.",
                table_cell_style
            )
        ],
        [
            Paragraph("<b>Member 3</b><br/>Security, RBAC &amp; Audit Engine", table_cell_bold),
            Paragraph("Cryptographic credential protection, role-based authorization guards, session security context, and forensic compliance auditing.", table_cell_style),
            Paragraph(
                "&bull; <code>PasswordHasher.java</code>: PBKDF2-HMAC-SHA256 (65,536 iterations, 16-byte secure salt).<br/>"
                "&bull; <code>Role.java</code> &amp; <code>Permission.java</code>: 5 system roles with 8 granular permissions.<br/>"
                "&bull; <code>SecureBankingService.java</code>: Proxy guard validating RBAC permissions before execution.<br/>"
                "&bull; <code>sql/security_schema.sql</code>: DDL for <code>bank_users</code> and <code>security_audit_log</code>.<br/>"
                "&bull; <code>DemoUserSeeder.java</code>: Auto-seeds 6 test users with valid cryptographic hashes.<br/>"
                "&bull; <code>SecurityTest.java</code>: Complete test suite for hashing, RBAC, and lockout rules.",
                table_cell_style
            )
        ],
        [
            Paragraph("<b>Member 4</b><br/>UI, REST API &amp; Presentation Lead", table_cell_bold),
            Paragraph("Lightweight embedded HTTP server, RESTful JSON routing, session authentication filters, and responsive single-page web application.", table_cell_style),
            Paragraph(
                "&bull; <code>ApiServer.java</code>: Embedded JDK HttpServer with fixed thread pool executor.<br/>"
                "&bull; <code>AuthFilter.java</code> &amp; <code>SessionStore.java</code>: Bearer token validation &amp; session TTL.<br/>"
                "&bull; REST Controllers: <code>Auth</code>, <code>Account</code>, <code>Transaction</code>, <code>Customer</code>, <code>Branch</code>, <code>Audit</code>.<br/>"
                "&bull; <code>index.html</code>, <code>styles.css</code>, <code>app.js</code>: Modern glassmorphism SPA banking portal.<br/>"
                "&bull; Features: 1-click persona switch, step-up MFA challenge modal, printable receipts.",
                table_cell_style
            )
        ]
    ]

    team_table = Table(team_data, colWidths=[95, 165, 272])
    team_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,0), c_primary),
        ('BOX', (0,0), (-1,-1), 0.5, c_border),
        ('INNERGRID', (0,0), (-1,-1), 0.5, c_border_light),
        ('VALIGN', (0,0), (-1,-1), 'TOP'),
        ('TOPPADDING', (0,0), (-1,-1), 3),
        ('BOTTOMPADDING', (0,0), (-1,-1), 3),
        ('LEFTPADDING', (0,0), (-1,-1), 5),
        ('RIGHTPADDING', (0,0), (-1,-1), 5),
        ('ROWBACKGROUNDS', (0,1), (-1,-1), [colors.white, c_light])
    ]))
    story.append(team_table)
    story.append(Spacer(1, 8))

    story.append(Paragraph("4. Project Directory Hierarchy", h1_style))
    dir_summary = [
        [
            Paragraph("<b>Package / Path</b>", table_header_style),
            Paragraph("<b>Architectural Responsibility</b>", table_header_style),
            Paragraph("<b>Notable Classes / Files</b>", table_header_style)
        ],
        [
            Paragraph("<code>com.bank.system.config</code>", code_style),
            Paragraph("HikariCP connection pool &amp; auto-schema executor", table_cell_style),
            Paragraph("<code>DatabaseConfig</code>, <code>DatabaseInitializer</code>", table_cell_style)
        ],
        [
            Paragraph("<code>com.bank.system.model</code>", code_style),
            Paragraph("Domain entities &amp; business state enumerations", table_cell_style),
            Paragraph("<code>Account</code>, <code>Transaction</code>, <code>Customer</code>, <code>Branch</code>", table_cell_style)
        ],
        [
            Paragraph("<code>com.bank.system.dao</code>", code_style),
            Paragraph("Data Access Objects using JDBC prepared statements", table_cell_style),
            Paragraph("<code>AccountDAO</code>, <code>TransactionDAO</code>, <code>CustomerDAO</code>, <code>BranchDAO</code>", table_cell_style)
        ],
        [
            Paragraph("<code>com.bank.system.service</code>", code_style),
            Paragraph("Core banking domain logic &amp; JDBC transaction boundaries", table_cell_style),
            Paragraph("<code>BankingService</code> (deposit, withdraw, transfer, history)", table_cell_style)
        ],
        [
            Paragraph("<code>com.bank.system.security.*</code>", code_style),
            Paragraph("RBAC engine, PBKDF2 cryptography &amp; security guards", table_cell_style),
            Paragraph("<code>SecureBankingService</code>, <code>PasswordHasher</code>, <code>Role</code>, <code>Permission</code>", table_cell_style)
        ],
        [
            Paragraph("<code>com.bank.system.api.*</code>", code_style),
            Paragraph("JDK embedded HTTP REST server, auth filter &amp; controllers", table_cell_style),
            Paragraph("<code>ApiServer</code>, <code>AuthFilter</code>, <code>AccountController</code>, <code>TransactionController</code>", table_cell_style)
        ],
        [
            Paragraph("<code>src/main/resources/static</code>", code_style),
            Paragraph("Single-Page Web Application frontend assets", table_cell_style),
            Paragraph("<code>index.html</code>, <code>css/app.css</code>, <code>js/app.js</code>", table_cell_style)
        ]
    ]
    dir_table = Table(dir_summary, colWidths=[140, 192, 200])
    dir_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,0), colors.HexColor("#1E293B")),
        ('BOX', (0,0), (-1,-1), 0.5, c_border),
        ('INNERGRID', (0,0), (-1,-1), 0.5, c_border_light),
        ('VALIGN', (0,0), (-1,-1), 'MIDDLE'),
        ('TOPPADDING', (0,0), (-1,-1), 2.5),
        ('BOTTOMPADDING', (0,0), (-1,-1), 2.5),
        ('LEFTPADDING', (0,0), (-1,-1), 5),
        ('RIGHTPADDING', (0,0), (-1,-1), 5),
        ('ROWBACKGROUNDS', (0,1), (-1,-1), [colors.white, c_light])
    ]))
    story.append(dir_table)

    story.append(PageBreak())

    # =========================================================================
    # PAGE 3: DATABASE & SECURITY SPECIFICATIONS
    # =========================================================================
    story.append(Paragraph("5. Relational Database Schema &amp; Storage Architecture", h1_style))
    story.append(Paragraph(
        "Hosted on PostgreSQL (Supabase Cloud). Enforces declarative constraints, primary keys, and foreign keys with <code>ON DELETE RESTRICT</code>:",
        body_style
    ))

    schema_rows = [
        [
            Paragraph("Table Name", table_header_style),
            Paragraph("Primary Key", table_header_style),
            Paragraph("Key Attributes &amp; Domain Constraints", table_header_style),
            Paragraph("Foreign Keys &amp; Indexes", table_header_style)
        ],
        [
            Paragraph("<b>branches</b>", table_cell_bold),
            Paragraph("<code>branch_id</code> (SERIAL)", code_style),
            Paragraph("<code>branch_name</code>, <code>ifsc_code</code> (UNIQUE), <code>address</code>", table_cell_style),
            Paragraph("Referenced by <code>accounts.branch_id</code>", table_cell_style)
        ],
        [
            Paragraph("<b>customers</b>", table_cell_bold),
            Paragraph("<code>customer_id</code> (SERIAL)", code_style),
            Paragraph("<code>first_name</code>, <code>last_name</code>, <code>email</code> (UNIQUE), <code>phone</code> (UNIQUE), <code>national_id</code> (UNIQUE)", table_cell_style),
            Paragraph("B-Tree indexes on <code>email</code>, <code>phone</code>", table_cell_style)
        ],
        [
            Paragraph("<b>accounts</b>", table_cell_bold),
            Paragraph("<code>account_number</code> (BIGSERIAL)", code_style),
            Paragraph("<code>account_type</code> IN ('SAVINGS','CHECKING','LOAN'), <code>balance</code> NUMERIC(15,2) CHECK(&ge;0.00), <code>status</code> IN ('ACTIVE','FROZEN','CLOSED')", table_cell_style),
            Paragraph("FK to <code>customers</code> &amp; <code>branches</code>. Pessimistic row locking via <code>SELECT FOR UPDATE</code>", table_cell_style)
        ],
        [
            Paragraph("<b>transactions</b>", table_cell_bold),
            Paragraph("<code>transaction_id</code> (BIGSERIAL)", code_style),
            Paragraph("<code>amount</code> NUMERIC(15,2) CHECK(&gt;0.00), <code>transaction_type</code> IN ('DEPOSIT','WITHDRAWAL','TRANSFER','INTEREST'), <code>status</code>, <code>reference_note</code>", table_cell_style),
            Paragraph("FK: <code>sender_account</code>, <code>receiver_account</code>. Composite indexes on <code>(sender, date)</code> and <code>(receiver, date)</code>", table_cell_style)
        ],
        [
            Paragraph("<b>audit_logs</b>", table_cell_bold),
            Paragraph("<code>audit_id</code> (BIGSERIAL)", code_style),
            Paragraph("<code>action_type</code>, <code>account_number</code>, <code>details</code>, <code>logged_at</code>", table_cell_style),
            Paragraph("Populated by trigger <code>trg_high_value_flag</code> for tx &ge; Rs. 10,000", table_cell_style)
        ],
        [
            Paragraph("<b>bank_users</b>", table_cell_bold),
            Paragraph("<code>user_id</code> (SERIAL)", code_style),
            Paragraph("<code>username</code> (UNIQUE), <code>password_hash</code> (PBKDF2), <code>role</code>, <code>failed_attempts</code>, <code>is_locked</code>, <code>last_login_at</code>", table_cell_style),
            Paragraph("FK: <code>customer_id</code> (links online banking user). Partial index on <code>is_locked=TRUE</code>", table_cell_style)
        ],
        [
            Paragraph("<b>security_audit_log</b>", table_cell_bold),
            Paragraph("<code>event_id</code> (BIGSERIAL)", code_style),
            Paragraph("<code>event_type</code>, <code>username</code>, <code>account_number</code>, <code>details</code>, <code>ip_address</code>, <code>occurred_at</code>", table_cell_style),
            Paragraph("Append-only immutable audit trail for security audits and compliance", table_cell_style)
        ]
    ]
    schema_table = Table(schema_rows, colWidths=[90, 95, 192, 155])
    schema_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,0), c_primary),
        ('BOX', (0,0), (-1,-1), 0.5, c_border),
        ('INNERGRID', (0,0), (-1,-1), 0.5, c_border_light),
        ('VALIGN', (0,0), (-1,-1), 'TOP'),
        ('TOPPADDING', (0,0), (-1,-1), 2.5),
        ('BOTTOMPADDING', (0,0), (-1,-1), 2.5),
        ('LEFTPADDING', (0,0), (-1,-1), 5),
        ('RIGHTPADDING', (0,0), (-1,-1), 5),
        ('ROWBACKGROUNDS', (0,1), (-1,-1), [colors.white, c_light])
    ]))
    story.append(schema_table)
    story.append(Spacer(1, 8))

    story.append(Paragraph("6. Security Architecture &amp; Role-Based Access Control (RBAC)", h1_style))
    story.append(Paragraph(
        "A 5-tiered RBAC model governs every method invocation in <code>SecureBankingService</code>:",
        body_style
    ))

    rbac_rows = [
        [
            Paragraph("System Role", table_header_style),
            Paragraph("Pre-Seeded Persona", table_header_style),
            Paragraph("Permitted Domain Operations", table_header_style),
            Paragraph("Security Restrictions", table_header_style)
        ],
        [
            Paragraph("<b>CUSTOMER</b>", table_cell_bold),
            Paragraph("alice (Cust #1)<br/>bob (Cust #2)", table_cell_style),
            Paragraph("&bull; View own balances &amp; transaction history<br/>&bull; Execute inter-account fund transfers<br/>&bull; Submit step-up MFA verification PIN", table_cell_style),
            Paragraph("Restricted to own accounts (<code>customer_id</code>). Cannot deposit cash directly or open accounts.", table_cell_style)
        ],
        [
            Paragraph("<b>TELLER</b>", table_cell_bold),
            Paragraph("teller01 (Front Desk)", table_cell_style),
            Paragraph("&bull; Cash deposit into any customer account<br/>&bull; Cash withdrawal with balance validation<br/>&bull; Lookup customer balances and account status", table_cell_style),
            Paragraph("Cannot freeze accounts, alter status, view compliance audit logs, or open new branches.", table_cell_style)
        ],
        [
            Paragraph("<b>BRANCH_MANAGER</b>", table_cell_bold),
            Paragraph("mgr01 (Manager)", table_cell_style),
            Paragraph("&bull; Approve and open new customer accounts<br/>&bull; Freeze, unfreeze, and close accounts<br/>&bull; Review branch transactions &amp; audit trails", table_cell_style),
            Paragraph("Cannot delete customer profiles or alter core database tables.", table_cell_style)
        ],
        [
            Paragraph("<b>AUDITOR</b>", table_cell_bold),
            Paragraph("audit01 (Compliance)", table_cell_style),
            Paragraph("&bull; Inspect full <code>security_audit_log</code><br/>&bull; Review high-value transaction flags (&ge; Rs. 10k)<br/>&bull; Inspect failed login attempts &amp; locked users", table_cell_style),
            Paragraph("Strictly read-only access. Cannot execute transfers, deposits, or alter account states.", table_cell_style)
        ],
        [
            Paragraph("<b>ADMIN</b>", table_cell_bold),
            Paragraph("admin (System Admin)", table_cell_style),
            Paragraph("&bull; Full administrative supervisory privileges<br/>&bull; Unlock locked user accounts &amp; user management<br/>&bull; System diagnostics &amp; health telemetry", table_cell_style),
            Paragraph("All administrative actions are permanently recorded to immutable audit log.", table_cell_style)
        ]
    ]
    rbac_table = Table(rbac_rows, colWidths=[90, 95, 192, 155])
    rbac_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,0), c_primary),
        ('BOX', (0,0), (-1,-1), 0.5, c_border),
        ('INNERGRID', (0,0), (-1,-1), 0.5, c_border_light),
        ('VALIGN', (0,0), (-1,-1), 'TOP'),
        ('TOPPADDING', (0,0), (-1,-1), 2.5),
        ('BOTTOMPADDING', (0,0), (-1,-1), 2.5),
        ('LEFTPADDING', (0,0), (-1,-1), 5),
        ('RIGHTPADDING', (0,0), (-1,-1), 5),
        ('ROWBACKGROUNDS', (0,1), (-1,-1), [colors.white, c_light])
    ]))
    story.append(rbac_table)

    story.append(PageBreak())

    # =========================================================================
    # PAGE 4: REST API, WEB PORTAL & VIVA PRESENTATION GUIDE
    # =========================================================================
    story.append(Paragraph("7. REST API Endpoints &amp; Single-Page Banking Portal", h1_style))

    api_rows = [
        [
            Paragraph("HTTP Method &amp; Route", table_header_style),
            Paragraph("Auth / Role", table_header_style),
            Paragraph("Function &amp; Payload", table_header_style),
            Paragraph("HTTP Status Codes", table_header_style)
        ],
        [
            Paragraph("<code>POST /api/auth/login</code>", code_style),
            Paragraph("Public", table_cell_bold),
            Paragraph("Authenticates username &amp; password; issues Bearer token", table_cell_style),
            Paragraph("200 OK / 401 Unauthorized", table_cell_style)
        ],
        [
            Paragraph("<code>POST /api/auth/mfa</code>", code_style),
            Paragraph("Authenticated", table_cell_bold),
            Paragraph("Verifies 6-digit PIN for high-value transfer step-up", table_cell_style),
            Paragraph("200 OK / 400 Bad Request", table_cell_style)
        ],
        [
            Paragraph("<code>GET /api/accounts</code>", code_style),
            Paragraph("CUSTOMER+", table_cell_bold),
            Paragraph("Returns accounts for caller or specified customer", table_cell_style),
            Paragraph("200 OK [JSON Array]", table_cell_style)
        ],
        [
            Paragraph("<code>POST /api/accounts</code>", code_style),
            Paragraph("MANAGER+", table_cell_bold),
            Paragraph("Opens account: <code>{customerId, branchId, type, initialBalance}</code>", table_cell_style),
            Paragraph("201 Created / 400 Bad Request", table_cell_style)
        ],
        [
            Paragraph("<code>PATCH /api/accounts/{id}/status</code>", code_style),
            Paragraph("MANAGER+", table_cell_bold),
            Paragraph("Modifies status: <code>ACTIVE</code>, <code>FROZEN</code>, or <code>CLOSED</code>", table_cell_style),
            Paragraph("200 OK / 404 Not Found", table_cell_style)
        ],
        [
            Paragraph("<code>POST /api/transactions/transfer</code>", code_style),
            Paragraph("CUSTOMER+", table_cell_bold),
            Paragraph("Atomic transfer: <code>{fromAccount, toAccount, amount, note}</code>", table_cell_style),
            Paragraph("200 OK / 402 Balance / 403 MFA", table_cell_style)
        ],
        [
            Paragraph("<code>POST /api/transactions/deposit</code>", code_style),
            Paragraph("TELLER+", table_cell_bold),
            Paragraph("Cash deposit: <code>{accountNumber, amount, note}</code>", table_cell_style),
            Paragraph("200 OK / 400 Bad Request", table_cell_style)
        ],
        [
            Paragraph("<code>POST /api/transactions/withdraw</code>", code_style),
            Paragraph("TELLER+", table_cell_bold),
            Paragraph("Cash withdrawal: <code>{accountNumber, amount, note}</code>", table_cell_style),
            Paragraph("200 OK / 402 Insufficient", table_cell_style)
        ],
        [
            Paragraph("<code>GET /api/audit</code>", code_style),
            Paragraph("AUDITOR, ADMIN", table_cell_bold),
            Paragraph("Chronological audit log query with event &amp; user filters", table_cell_style),
            Paragraph("200 OK [JSON Array]", table_cell_style)
        ],
        [
            Paragraph("<code>GET /health</code>", code_style),
            Paragraph("Public", table_cell_bold),
            Paragraph("System health probe: returns <code>{\"status\":\"UP\"}</code>", table_cell_style),
            Paragraph("200 OK", table_cell_style)
        ]
    ]
    api_table = Table(api_rows, colWidths=[130, 85, 217, 100])
    api_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,0), c_primary),
        ('BOX', (0,0), (-1,-1), 0.5, c_border),
        ('INNERGRID', (0,0), (-1,-1), 0.5, c_border_light),
        ('VALIGN', (0,0), (-1,-1), 'TOP'),
        ('TOPPADDING', (0,0), (-1,-1), 2),
        ('BOTTOMPADDING', (0,0), (-1,-1), 2),
        ('LEFTPADDING', (0,0), (-1,-1), 5),
        ('RIGHTPADDING', (0,0), (-1,-1), 5),
        ('ROWBACKGROUNDS', (0,1), (-1,-1), [colors.white, c_light])
    ]))
    story.append(api_table)
    story.append(Spacer(1, 8))

    story.append(Paragraph("8. Evaluation &amp; Live Presentation Guide (Step-by-Step)", h1_style))
    story.append(Paragraph(
        "Demonstrate the complete platform during the academic presentation or viva using this 6-step workflow:",
        body_style
    ))

    viva_steps = [
        [
            Paragraph("Phase &amp; Goal", table_header_style),
            Paragraph("Command / Action", table_header_style),
            Paragraph("Talking Points &amp; Technical Explanation for Examiners", table_header_style)
        ],
        [
            Paragraph("<b>1. Unit Test Suite</b>", table_cell_bold),
            Paragraph("<code>mvn test</code>", code_style),
            Paragraph("Show 70 automated JUnit 5 tests passing in ~3s. Mention Mockito mocks verify lock ordering, balance boundaries, and crypto without requiring an external DB.", table_cell_style)
        ],
        [
            Paragraph("<b>2. Boot Platform</b>", table_cell_bold),
            Paragraph("<code>java -jar target/core-banking-system-1.0.0.jar</code>", code_style),
            Paragraph("Demonstrate <code>DatabaseInitializer</code> verifying tables on Supabase, HikariCP pool creation, and <code>DemoUserSeeder</code> provisioning test accounts.", table_cell_style)
        ],
        [
            Paragraph("<b>3. Customer Transfer</b>", table_cell_bold),
            Paragraph("Sign in as <b>alice</b> (Customer #1)<br/>Transfer Rs. 1,000 to Bob (#1002)", table_cell_style),
            Paragraph("Demonstrate atomic balance update, printable transaction receipt generation, and two-phase account lock ordering in terminal logs.", table_cell_style)
        ],
        [
            Paragraph("<b>4. Step-Up MFA</b>", table_cell_bold),
            Paragraph("Transfer Rs. 15,000 from Alice to Bob", table_cell_style),
            Paragraph("Demonstrate how the system intercepts transfers &ge; Rs. 10,000, requiring one-time MFA PIN step-up verification before the transaction is committed.", table_cell_style)
        ],
        [
            Paragraph("<b>5. Teller Operations</b>", table_cell_bold),
            Paragraph("Switch to <b>teller01</b><br/>Perform Cash Deposit", table_cell_style),
            Paragraph("Show role differentiation: tellers can deposit and withdraw cash, but cannot approve accounts, change interest, or view audit logs.", table_cell_style)
        ],
        [
            Paragraph("<b>6. Compliance Audit</b>", table_cell_bold),
            Paragraph("Switch to <b>audit01</b><br/>Open <i>Compliance &amp; Audit Trail</i>", table_cell_style),
            Paragraph("Showcase the immutable audit log: records transfers, trigger-flagged high-value events, IP addresses, and timestamps for regulatory compliance.", table_cell_style)
        ]
    ]
    viva_table = Table(viva_steps, colWidths=[105, 160, 267])
    viva_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,0), c_primary),
        ('BOX', (0,0), (-1,-1), 0.5, c_border),
        ('INNERGRID', (0,0), (-1,-1), 0.5, c_border_light),
        ('VALIGN', (0,0), (-1,-1), 'TOP'),
        ('TOPPADDING', (0,0), (-1,-1), 2.5),
        ('BOTTOMPADDING', (0,0), (-1,-1), 2.5),
        ('LEFTPADDING', (0,0), (-1,-1), 5),
        ('RIGHTPADDING', (0,0), (-1,-1), 5),
        ('ROWBACKGROUNDS', (0,1), (-1,-1), [colors.white, c_light])
    ]))
    story.append(viva_table)

    # Build PDF
    pdf.build(story, canvasmaker=NumberedCanvas)
    print(f"Generated 4-page presentation PDF: {output_filename}")

if __name__ == "__main__":
    create_summary_pdf()
