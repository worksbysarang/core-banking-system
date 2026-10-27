package com.bank.system.api.dto;

/**
 * ApiResponse – standard JSON envelope returned by every endpoint.
 *
 * <h3>Success</h3>
 * <pre>
 * { "success": true,  "data": { ... } }
 * </pre>
 *
 * <h3>Failure</h3>
 * <pre>
 * { "success": false, "error": "Insufficient balance." }
 * </pre>
 *
 * @param <T> the type of the {@code data} payload (ignored on error responses)
 */
public class ApiResponse<T> {

    public final boolean success;
    public final T       data;    // non-null on success
    public final String  error;   // non-null on failure

    private ApiResponse(boolean success, T data, String error) {
        this.success = success;
        this.data    = data;
        this.error   = error;
    }

    /** Creates a success response with a payload. */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /** Creates a success response with no payload. */
    public static <Void> ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    /** Creates an error response. */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message);
    }
}
