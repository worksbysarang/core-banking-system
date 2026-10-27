package com.bank.system.api.dto;

/**
 * StatusUpdateRequest – JSON body for {@code PATCH /api/accounts/{id}/status}.
 *
 * <pre>
 * { "status": "FROZEN" }
 * </pre>
 */
public class StatusUpdateRequest {
    public String status;   // ACTIVE | FROZEN | CLOSED
}
