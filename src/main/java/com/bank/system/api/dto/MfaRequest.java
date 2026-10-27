package com.bank.system.api.dto;

/**
 * MfaRequest – JSON body sent to {@code POST /api/auth/mfa}.
 *
 * <pre>
 * { "code": "123456" }
 * </pre>
 */
public class MfaRequest {
    public String code;
}
