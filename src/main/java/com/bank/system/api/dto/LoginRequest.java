package com.bank.system.api.dto;

/**
 * LoginRequest – JSON body sent to {@code POST /api/auth/login}.
 *
 * <pre>
 * {
 *   "username": "alice",
 *   "password": "MySecret@1"
 * }
 * </pre>
 */
public class LoginRequest {
    public String username;
    public String password;
}
