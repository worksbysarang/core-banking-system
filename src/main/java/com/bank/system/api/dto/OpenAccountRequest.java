package com.bank.system.api.dto;

import java.math.BigDecimal;

/**
 * OpenAccountRequest – JSON body sent to {@code POST /api/accounts}.
 *
 * <pre>
 * {
 *   "customerId": 1,
 *   "branchId": 1,
 *   "accountType": "SAVINGS",
 *   "initialBalance": 1000.00
 * }
 * </pre>
 */
public class OpenAccountRequest {
    public int        customerId;
    public int        branchId;
    public String     accountType;   // SAVINGS | CHECKING | LOAN
    public BigDecimal initialBalance;
}
