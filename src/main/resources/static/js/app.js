/**
 * Meridian Core Banking – Client Application Logic
 */

const TOKEN_KEY = "meridian.token";

const state = {
  token: localStorage.getItem(TOKEN_KEY),
  me: null,
  accounts: [],
  branches: [],
  customers: [],
  activeTab: "accounts"
};

const $ = (id) => document.getElementById(id);

// ── 1. API Helper ────────────────────────────────────────────────────────────

async function api(path, options = {}) {
  const headers = { "Content-Type": "application/json", ...(options.headers || {}) };
  if (state.token) {
    headers.Authorization = `Bearer ${state.token}`;
  }

  const res = await fetch(path, { ...options, headers });
  const body = await res.json().catch(() => ({ success: false, error: "Network or JSON error." }));

  if (res.status === 401 && body.error && body.error.toLowerCase().includes("log in")) {
    logout(false);
    showToast("Session expired. Please log in again.", "error");
    throw new Error(body.error);
  }

  if (!body.success) {
    const err = new Error(body.error || `HTTP ${res.status}`);
    err.status = res.status;
    throw err;
  }

  return body.data;
}

function has(permission) {
  return Boolean(state.me?.permissions?.includes(permission));
}

function money(value) {
  const n = Number(value ?? 0);
  return `₹${n.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function formatAccNumber(accNum) {
  const s = String(accNum);
  return s.replace(/(\d{4})/g, '$1 ').trim();
}

// ── 2. Toasts ────────────────────────────────────────────────────────────────

function showToast(message, type = "info") {
  const container = $("toast-container");
  const toast = document.createElement("div");
  toast.className = `toast ${type}`;
  toast.textContent = message;

  container.appendChild(toast);
  setTimeout(() => {
    toast.style.opacity = "0";
    toast.style.transform = "translateX(100%)";
    toast.style.transition = "all 0.3s ease";
    setTimeout(() => toast.remove(), 300);
  }, 4000);
}

// ── 3. Authentication & Lifecycle ───────────────────────────────────────────

async function login(username, password) {
  const submitBtn = $("login-submit-btn");
  if (submitBtn) {
    submitBtn.disabled = true;
    const span = submitBtn.querySelector("span");
    if (span) span.textContent = "Authenticating...";
  }
  $("login-error").hidden = true;

  try {
    const data = await api("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({ username, password })
    });
    state.token = data.token;
    localStorage.setItem(TOKEN_KEY, data.token);
    showToast(`Signed in as ${data.username} (${data.role})`, "success");
    await enterApp();
  } catch (err) {
    $("login-error").hidden = false;
    $("login-error").textContent = err.message;
    showToast(err.message, "error");
  } finally {
    if (submitBtn) {
      submitBtn.disabled = false;
      const span = submitBtn.querySelector("span");
      if (span) span.textContent = "Authenticate Session";
    }
  }
}

function logout(callApi = true) {
  if (callApi && state.token) {
    api("/api/auth/logout", { method: "POST" }).catch(() => {});
  }
  state.token = null;
  state.me = null;
  localStorage.removeItem(TOKEN_KEY);
  $("app-view").hidden = true;
  $("login-view").hidden = false;
  showToast("Session signed out.", "info");
}

async function enterApp() {
  try {
    state.me = await api("/api/auth/me");
  } catch (err) {
    console.error("Auth me failed:", err);
    logout(false);
    return;
  }

  // Switch views immediately
  $("login-view").hidden = true;
  $("app-view").hidden = false;

  // Header Details
  $("nav-username").textContent = state.me.username;
  $("nav-role").textContent = state.me.roleLabel || state.me.role;
  $("nav-avatar").textContent = (state.me.username || "U").substring(0, 2).toUpperCase();
  $("mfa-badge").hidden = !state.me.mfaVerified;

  // Security Tab Status
  updateMfaDisplay();

  // Role Permissions Configuration
  configureUiForRole();

  // Load Reference Data (branches & customers) safely
  try { await loadBranches(); } catch (e) { console.debug("loadBranches failed", e); }
  if (has("VIEW_ANY_ACCOUNT")) {
    try { await loadCustomers(); } catch (e) { console.debug("loadCustomers failed", e); }
  }

  // Load Accounts & Statements safely
  try { await loadAccounts(); } catch (e) { console.debug("loadAccounts failed", e); }
}

function updateMfaDisplay() {
  const verified = Boolean(state.me?.mfaVerified);
  $("mfa-badge").hidden = !verified;

  if (verified) {
    $("mfa-shield-icon").textContent = "✅";
    $("mfa-status-title").textContent = "MFA Verified for Current Session";
    $("mfa-status-desc").textContent = "All high-value transfers (≥ ₹10,000) are permitted.";
    $("mfa-transfer-warning").hidden = true;
  } else {
    $("mfa-shield-icon").textContent = "🛡️";
    $("mfa-status-title").textContent = "MFA Not Verified For Current Session";
    $("mfa-status-desc").textContent = "Transfers at or above ₹10,000 will require 6-digit TOTP verification.";
  }

  // Granted permissions list
  $("sec-current-role").textContent = state.me?.role || "CUSTOMER";
  const chipsContainer = $("granted-permissions-chips");
  chipsContainer.innerHTML = "";
  for (const p of state.me?.permissions || []) {
    const chip = document.createElement("span");
    chip.className = "perm-chip";
    chip.textContent = p;
    chipsContainer.appendChild(chip);
  }
}

function configureUiForRole() {
  const canOpenAccount = has("OPEN_ACCOUNT");
  $("btn-open-account-modal").hidden = !canOpenAccount;
  $("btn-create-customer-modal").hidden = !canOpenAccount;

  const canAudit = has("READ_AUDIT_LOG");
  $("tab-audit-btn").hidden = !canAudit;

  const isStaff = has("VIEW_ANY_ACCOUNT");
  $("customer-filter-group").hidden = !isStaff;
  $("deposit-panel").hidden = !has("DEPOSIT");
  $("withdraw-panel").hidden = !has("WITHDRAW");

  // Show Customer tab for staff
  $("tab-customers-btn").hidden = !isStaff;
}

// ── 4. Branches & Customers Data ─────────────────────────────────────────────

async function loadBranches() {
  try {
    state.branches = await api("/api/branches");
    $("metric-branch-count").textContent = state.branches.length;
    const branchSelect = $("open-branch-select");
    branchSelect.innerHTML = "";
    for (const b of state.branches) {
      const opt = new Option(`${b.branchName} (${b.ifscCode})`, b.branchId);
      branchSelect.add(opt);
    }
  } catch (err) {
    console.debug("Could not load branches:", err.message);
  }
}

async function loadCustomers() {
  try {
    state.customers = await api("/api/customers");
    renderCustomers(state.customers);

    // Populate Customer Filter & Modal selects
    const filterSelect = $("customer-filter-select");
    const openCustSelect = $("open-customer-select");
    filterSelect.innerHTML = '<option value="all">All Customers</option>';
    openCustSelect.innerHTML = "";

    for (const c of state.customers) {
      filterSelect.add(new Option(`${c.firstName} ${c.lastName} (#${c.customerId})`, c.customerId));
      openCustSelect.add(new Option(`${c.firstName} ${c.lastName} (${c.email})`, c.customerId));
    }
  } catch (err) {
    console.debug("Could not load customers:", err.message);
  }
}

function renderCustomers(customers) {
  const grid = $("customers-grid");
  grid.innerHTML = "";

  if (!customers.length) {
    grid.innerHTML = "<p class='muted'>No customers registered.</p>";
    return;
  }

  for (const c of customers) {
    const initials = (c.firstName[0] || "") + (c.lastName[0] || "");
    const card = document.createElement("article");
    card.className = "customer-card glass-card";
    card.innerHTML = `
      <div>
        <div class="cust-header">
          <div class="cust-avatar">${initials.toUpperCase()}</div>
          <div>
            <h4>${c.firstName} ${c.lastName}</h4>
            <span class="muted">Customer ID: #${c.customerId}</span>
          </div>
        </div>
        <div class="cust-details-grid">
          <div class="cust-row"><span class="muted">Email</span><span>${c.email}</span></div>
          <div class="cust-row"><span class="muted">Phone</span><span>${c.phone}</span></div>
          <div class="cust-row"><span class="muted">National ID</span><span><code>${c.nationalId}</code></span></div>
          <div class="cust-row"><span class="muted">Address</span><span>${c.address || '—'}</span></div>
        </div>
      </div>
      <div style="margin-top:1.2rem; display:flex; justify-content:flex-end;">
        <button type="button" class="btn btn-outline btn-sm cust-view-accs" data-custid="${c.customerId}">
          View Linked Accounts
        </button>
      </div>
    `;

    card.querySelector(".cust-view-accs").addEventListener("click", () => {
      $("customer-filter-select").value = c.customerId;
      switchTab("accounts");
      loadAccounts();
    });

    grid.appendChild(card);
  }
}

// ── 5. Accounts & Portfolio ──────────────────────────────────────────────────

async function loadAccounts() {
  try {
    let path = "/api/accounts";
    const selectedCust = $("customer-filter-select")?.value;
    if (selectedCust && selectedCust !== "all") {
      path += `?customerId=${selectedCust}`;
    }

    const accounts = await api(path);
    state.accounts = accounts;
    renderAccounts(accounts);
    updateMetrics(accounts);
    populateTransferAccountSelects(accounts);
    populateHistoryAccountSelect(accounts);
  } catch (err) {
    showToast(err.message, "error");
  }
}

function updateMetrics(accounts) {
  $("metric-accounts-count").textContent = accounts.length;
  const total = accounts.reduce((acc, curr) => acc + Number(curr.balance || 0), 0);
  $("metric-total-balance").textContent = money(total);
}

function renderAccounts(accounts) {
  const grid = $("accounts-grid");
  grid.innerHTML = "";

  if (!accounts.length) {
    grid.innerHTML = "<p class='muted'>No accounts found for the current selection.</p>";
    return;
  }

  for (const acc of accounts) {
    const card = document.createElement("article");
    card.className = "account-card";
    const typeClass = (acc.accountType || "SAVINGS").toLowerCase();
    const statusClass = (acc.status || "ACTIVE").toLowerCase();

    card.innerHTML = `
      <div>
        <div class="card-top">
          <span class="card-type-badge ${typeClass}">${acc.accountType}</span>
          <span class="card-status-badge ${statusClass}">${acc.status}</span>
        </div>
        <div class="card-chip-graphic"></div>
        <div class="card-acc-num">
          <span>${formatAccNumber(acc.accountNumber)}</span>
          <button type="button" class="card-copy-btn" title="Copy account number" data-acc="${acc.accountNumber}">
            <svg viewBox="0 0 20 20" fill="currentColor"><path d="M8 3a1 1 0 011-1h2a1 1 0 110 2H9a1 1 0 01-1-1z" /><path d="M6 3a2 2 0 00-2 2v11a2 2 0 002 2h8a2 2 0 002-2V5a2 2 0 00-2-2 3 3 0 01-3 3H9a3 3 0 01-3-3z" /></svg>
          </button>
        </div>
        <div class="card-balance-block">
          <span class="card-balance-label">Available Balance</span>
          <div class="card-balance-amount">${money(acc.balance)}</div>
        </div>
      </div>
      <div class="card-footer">
        <div class="card-meta-owner">
          <span>Customer #${acc.customerId}</span>
          <strong>Branch #${acc.branchId}</strong>
        </div>
        <div class="card-actions-quick">
          <button type="button" class="btn btn-outline btn-sm quick-transfer-btn" data-acc="${acc.accountNumber}" title="Transfer from this account">Transfer</button>
          <button type="button" class="btn btn-outline btn-sm quick-history-btn" data-acc="${acc.accountNumber}" title="View account statement">Statement</button>
          ${has("FREEZE_ACCOUNT") || has("CLOSE_ACCOUNT") ? `
            <button type="button" class="btn btn-outline btn-sm quick-status-btn" data-acc="${acc.accountNumber}" data-status="${acc.status}">Status</button>
          ` : ""}
        </div>
      </div>
    `;

    // Copy Account Number
    card.querySelector(".card-copy-btn").addEventListener("click", (e) => {
      e.stopPropagation();
      navigator.clipboard.writeText(acc.accountNumber);
      showToast(`Copied #${acc.accountNumber} to clipboard!`, "info");
    });

    // Quick Action: Transfer
    card.querySelector(".quick-transfer-btn").addEventListener("click", () => {
      switchTab("transactions");
      $("tf-sender").value = acc.accountNumber;
    });

    // Quick Action: Statement
    card.querySelector(".quick-history-btn").addEventListener("click", () => {
      switchTab("history");
      $("history-account-select").value = acc.accountNumber;
      loadHistory(acc.accountNumber);
    });

    // Quick Action: Status Change (Staff)
    const statusBtn = card.querySelector(".quick-status-btn");
    if (statusBtn) {
      statusBtn.addEventListener("click", () => {
        openStatusModal(acc.accountNumber, acc.status);
      });
    }

    grid.appendChild(card);
  }
}

function populateTransferAccountSelects(accounts) {
  const senderSelect = $("tf-sender");
  senderSelect.innerHTML = "";
  for (const a of accounts) {
    if (a.status === "ACTIVE") {
      senderSelect.add(new Option(`Account #${a.accountNumber} (${a.accountType} · ${money(a.balance)})`, a.accountNumber));
    }
  }
}

function populateHistoryAccountSelect(accounts) {
  const select = $("history-account-select");
  const prevVal = select.value;
  select.innerHTML = "";
  for (const a of accounts) {
    select.add(new Option(`Account #${a.accountNumber} (${a.accountType})`, a.accountNumber));
  }
  if (prevVal && Array.from(select.options).some(o => o.value === prevVal)) {
    select.value = prevVal;
  }
}

// ── 6. Transactions (Transfer / Deposit / Withdraw) ─────────────────────────

async function handleTransfer(e) {
  e.preventDefault();
  const senderAccountNumber = Number($("tf-sender").value);
  const receiverAccountNumber = Number($("tf-receiver").value);
  const amount = Number($("tf-amount").value);
  const note = $("tf-note").value.trim();
  const useStoredProcedure = $("tf-use-procedure").checked;

  if (senderAccountNumber === receiverAccountNumber) {
    showToast("Sender and receiver accounts must be different.", "error");
    return;
  }

  try {
    const res = await api("/api/transactions/transfer", {
      method: "POST",
      body: JSON.stringify({
        senderAccountNumber,
        receiverAccountNumber,
        amount,
        note,
        useStoredProcedure
      })
    });

    showToast(`${res.message} (Tx ID #${res.transactionId})`, "success");
    $("form-transfer").reset();
    await loadAccounts();
  } catch (err) {
    showToast(err.message, "error");
    if (err.message.includes("MFA") || err.status === 401) {
      $("mfa-transfer-warning").hidden = false;
    }
  }
}

async function handleDeposit(e) {
  e.preventDefault();
  const accountNumber = Number($("dep-account").value);
  const amount = Number($("dep-amount").value);
  const note = $("dep-note").value.trim();

  try {
    const res = await api("/api/transactions/deposit", {
      method: "POST",
      body: JSON.stringify({ accountNumber, amount, note })
    });
    showToast(`Deposit of ₹${amount} successful (Tx ID #${res.transactionId})`, "success");
    $("form-deposit").reset();
    await loadAccounts();
  } catch (err) {
    showToast(err.message, "error");
  }
}

async function handleWithdraw(e) {
  e.preventDefault();
  const accountNumber = Number($("wd-account").value);
  const amount = Number($("wd-amount").value);
  const note = $("wd-note").value.trim();

  try {
    const res = await api("/api/transactions/withdraw", {
      method: "POST",
      body: JSON.stringify({ accountNumber, amount, note })
    });
    showToast(`Withdrawal of ₹${amount} successful (Tx ID #${res.transactionId})`, "success");
    $("form-withdraw").reset();
    await loadAccounts();
  } catch (err) {
    showToast(err.message, "error");
  }
}

// ── 7. Transaction History ───────────────────────────────────────────────────

async function loadHistory(accountNumber) {
  const accNum = accountNumber || $("history-account-select").value;
  if (!accNum) return;

  const tbody = $("history-table-body");
  tbody.innerHTML = '<tr><td colspan="8" class="text-center muted">Loading statements...</td></tr>';

  try {
    const data = await api(`/api/transactions/history/${accNum}?limit=50`);
    renderHistoryTable(data.transactions || []);
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="8" class="text-center" style="color:var(--rose)">${err.message}</td></tr>`;
  }
}

function renderHistoryTable(transactions) {
  const tbody = $("history-table-body");
  tbody.innerHTML = "";

  if (!transactions.length) {
    tbody.innerHTML = '<tr><td colspan="8" class="text-center muted">No transaction records found for this account.</td></tr>';
    return;
  }

  for (const tx of transactions) {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td><code>#${tx.transactionId}</code></td>
      <td><span class="tx-type-tag ${tx.transactionType}">${tx.transactionType}</span></td>
      <td>${tx.senderAccount ? `Account #${tx.senderAccount}` : '—'}</td>
      <td>${tx.receiverAccount ? `Account #${tx.receiverAccount}` : '—'}</td>
      <td style="font-family:var(--font-mono); font-weight:600;">${money(tx.amount)}</td>
      <td><span class="card-status-badge active">${tx.status}</span></td>
      <td class="muted">${tx.referenceNote || '—'}</td>
      <td style="font-size:0.8rem; color:var(--text-subtle);">${tx.createdAt ? tx.createdAt.replace("T", " ") : '—'}</td>
    `;
    tbody.appendChild(tr);
  }
}

// ── 8. Security Audit Trail ──────────────────────────────────────────────────

async function loadAuditTrail() {
  if (!has("READ_AUDIT_LOG")) return;

  const tbody = $("audit-table-body");
  tbody.innerHTML = '<tr><td colspan="6" class="text-center muted">Loading audit events...</td></tr>';

  const typeFilter = $("audit-filter-type").value;
  let path = "/api/audit?limit=100";
  if (typeFilter) path += `&type=${typeFilter}`;

  try {
    const data = await api(path);
    renderAuditTable(data.events || []);
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="6" class="text-center" style="color:var(--rose)">${err.message}</td></tr>`;
  }
}

function renderAuditTable(events) {
  const tbody = $("audit-table-body");
  tbody.innerHTML = "";

  if (!events.length) {
    tbody.innerHTML = '<tr><td colspan="6" class="text-center muted">No audit events match the filter.</td></tr>';
    return;
  }

  let highValueCount = 0;
  for (const ev of events) {
    if (ev.eventType === "HIGH_VALUE_TRANSACTION") highValueCount++;
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td><code>#${ev.eventId}</code></td>
      <td><span class="audit-tag ${ev.eventType}">${ev.eventType}</span></td>
      <td><strong>${ev.username || 'SYSTEM'}</strong></td>
      <td>${ev.accountNumber ? `Account #${ev.accountNumber}` : '—'}</td>
      <td>${ev.details}</td>
      <td style="font-size:0.8rem; color:var(--text-subtle);">${ev.occurredAt ? ev.occurredAt.replace("T", " ") : '—'}</td>
    `;
    tbody.appendChild(tr);
  }

  if (highValueCount > 0) {
    $("metric-audit-flags").textContent = `${highValueCount} FLAGGED`;
  }
}

// ── 9. MFA Verification ──────────────────────────────────────────────────────

async function handleMfaVerify(e) {
  e.preventDefault();
  const code = $("mfa-code-input").value.trim();
  if (!code) {
    showToast("Please enter a 6-digit MFA code.", "error");
    return;
  }

  try {
    const res = await api("/api/auth/mfa", {
      method: "POST",
      body: JSON.stringify({ code })
    });
    state.me.mfaVerified = true;
    updateMfaDisplay();
    showToast(res.message, "success");
    $("mfa-code-input").value = "";
  } catch (err) {
    showToast(err.message, "error");
  }
}

// ── 10. Modals & Handlers ────────────────────────────────────────────────────

function openModal(modalId) {
  const m = $(modalId);
  if (m) m.hidden = false;
}

function closeModal(modalId) {
  const m = $(modalId);
  if (m) m.hidden = true;
}

function openStatusModal(accountNumber, currentStatus) {
  $("status-target-account").value = accountNumber;
  $("status-target-acc-display").textContent = accountNumber;
  $("status-new-value").value = currentStatus;
  openModal("modal-update-status");
}

async function handleOpenAccountSubmit(e) {
  e.preventDefault();
  const customerId = Number($("open-customer-select").value);
  const branchId = Number($("open-branch-select").value);
  const accountType = $("open-account-type").value;
  const initialBalance = Number($("open-initial-balance").value);

  try {
    const res = await api("/api/accounts", {
      method: "POST",
      body: JSON.stringify({ customerId, branchId, accountType, initialBalance })
    });
    showToast(`Account #${res.accountNumber} successfully created!`, "success");
    closeModal("modal-open-account");
    await loadAccounts();
  } catch (err) {
    showToast(err.message, "error");
  }
}

async function handleCreateCustomerSubmit(e) {
  e.preventDefault();
  const body = {
    firstName: $("cust-first-name").value.trim(),
    lastName: $("cust-last-name").value.trim(),
    email: $("cust-email").value.trim(),
    phone: $("cust-phone").value.trim(),
    nationalId: $("cust-national-id").value.trim(),
    address: $("cust-address").value.trim()
  };

  try {
    const res = await api("/api/customers", {
      method: "POST",
      body: JSON.stringify(body)
    });
    showToast(`Customer registered (ID #${res.customerId})!`, "success");
    closeModal("modal-create-customer");
    $("form-create-customer").reset();
    await loadCustomers();
  } catch (err) {
    showToast(err.message, "error");
  }
}

async function handleStatusUpdateSubmit(e) {
  e.preventDefault();
  const accNum = Number($("status-target-account").value);
  const status = $("status-new-value").value;

  try {
    const res = await api(`/api/accounts/${accNum}/status`, {
      method: "PATCH",
      body: JSON.stringify({ status })
    });
    showToast(res.message, "success");
    closeModal("modal-update-status");
    await loadAccounts();
  } catch (err) {
    showToast(err.message, "error");
  }
}

// ── 11. Tab Switching & Global Events ────────────────────────────────────────

function switchTab(tabName) {
  state.activeTab = tabName;
  document.querySelectorAll(".tab-btn").forEach(btn => {
    btn.classList.toggle("active", btn.dataset.tab === tabName);
  });
  document.querySelectorAll(".tab-pane").forEach(pane => {
    pane.classList.toggle("active", pane.id === `tab-${tabName}`);
  });

  // Lazy load tab data
  if (tabName === "history") {
    loadHistory();
  } else if (tabName === "audit") {
    loadAuditTrail();
  } else if (tabName === "customers" && has("VIEW_ANY_ACCOUNT")) {
    loadCustomers();
  }
}

function wireEvents() {
  // Login Form
  $("login-form").addEventListener("submit", (e) => {
    e.preventDefault();
    login($("username").value.trim(), $("password").value);
  });

  // Demo Persona Chips
  $("demo-chips").addEventListener("click", (e) => {
    const chip = e.target.closest(".demo-chip");
    if (!chip) return;
    chip.style.opacity = "0.6";
    $("username").value = chip.dataset.user;
    $("password").value = chip.dataset.pass;
    login(chip.dataset.user, chip.dataset.pass).finally(() => {
      chip.style.opacity = "";
    });
  });

  // Logout
  $("logout-btn").addEventListener("click", () => logout(true));

  // Role Switcher Modal
  $("switch-role-btn").addEventListener("click", () => openModal("modal-switch-role"));
  document.querySelectorAll(".modal-switch-chip").forEach(chip => {
    chip.addEventListener("click", () => {
      closeModal("modal-switch-role");
      login(chip.dataset.user, chip.dataset.pass);
    });
  });

  // Subnav Tabs
  document.querySelectorAll(".tab-btn").forEach(btn => {
    btn.addEventListener("click", () => switchTab(btn.dataset.tab));
  });

  // Modals Open Buttons
  $("btn-open-account-modal").addEventListener("click", () => openModal("modal-open-account"));
  $("btn-create-customer-modal").addEventListener("click", () => openModal("modal-create-customer"));

  // Modals Close Buttons
  document.querySelectorAll("[data-close]").forEach(btn => {
    btn.addEventListener("click", () => closeModal(btn.dataset.close));
  });

  // Modals Outer Click to Close
  document.querySelectorAll(".modal-overlay").forEach(overlay => {
    overlay.addEventListener("click", (e) => {
      if (e.target === overlay) overlay.hidden = true;
    });
  });

  // Forms
  $("form-transfer").addEventListener("submit", handleTransfer);
  $("form-deposit").addEventListener("submit", handleDeposit);
  $("form-withdraw").addEventListener("submit", handleWithdraw);
  $("form-mfa").addEventListener("submit", handleMfaVerify);
  $("form-open-account").addEventListener("submit", handleOpenAccountSubmit);
  $("form-create-customer").addEventListener("submit", handleCreateCustomerSubmit);
  $("form-update-status").addEventListener("submit", handleStatusUpdateSubmit);

  // Transfer Amount Presets
  document.querySelectorAll(".preset-btn").forEach(btn => {
    btn.addEventListener("click", () => {
      $("tf-amount").value = btn.dataset.amt;
      if (Number(btn.dataset.amt) >= 10000 && !state.me?.mfaVerified) {
        $("mfa-transfer-warning").hidden = false;
      }
    });
  });

  // Amount input listener for MFA warning
  $("tf-amount").addEventListener("input", (e) => {
    const val = Number(e.target.value);
    if (val >= 10000 && !state.me?.mfaVerified) {
      $("mfa-transfer-warning").hidden = false;
    } else {
      $("mfa-transfer-warning").hidden = true;
    }
  });

  $("quick-verify-mfa-btn").addEventListener("click", () => switchTab("security"));
  $("autofill-mfa-btn").addEventListener("click", () => $("mfa-code-input").value = "123456");

  // Refresh Buttons
  $("refresh-accounts-btn").addEventListener("click", loadAccounts);
  $("customer-filter-select").addEventListener("change", loadAccounts);
  $("history-account-select").addEventListener("change", () => loadHistory());
  $("refresh-history-btn").addEventListener("click", () => loadHistory());
  $("refresh-customers-btn").addEventListener("click", loadCustomers);
  $("refresh-audit-btn").addEventListener("click", loadAuditTrail);
  $("audit-filter-type").addEventListener("change", loadAuditTrail);

  // Global Search
  $("global-search-input").addEventListener("keyup", (e) => {
    const query = e.target.value.toLowerCase().trim();
    if (!query) {
      renderAccounts(state.accounts);
      return;
    }
    const filtered = state.accounts.filter(a =>
      String(a.accountNumber).includes(query) ||
      String(a.customerId).includes(query) ||
      a.accountType.toLowerCase().includes(query) ||
      a.status.toLowerCase().includes(query)
    );
    renderAccounts(filtered);
  });
}

// ── 12. App Launch ───────────────────────────────────────────────────────────

wireEvents();
if (state.token) {
  enterApp().catch(() => logout(false));
}
