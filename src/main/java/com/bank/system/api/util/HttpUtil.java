package com.bank.system.api.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * HttpUtil – HTTP request/response helpers shared across all controllers.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Deserialise JSON request bodies with Gson.</li>
 *   <li>Serialise response objects and write them with the correct headers.</li>
 *   <li>Extract path segments and query parameters.</li>
 *   <li>Provide safe CORS headers for browser testing.</li>
 * </ul>
 *
 * <h3>Thread safety</h3>
 * <p>{@link Gson} is thread-safe – the shared instance is fine.
 */
public final class HttpUtil {

    /** Shared Gson instance configured with ISO-8601 date serialisation. */
    public static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(LocalDateTime.class,
            (com.google.gson.JsonSerializer<LocalDateTime>) (src, type, ctx) ->
                new com.google.gson.JsonPrimitive(
                    src.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
        .setPrettyPrinting()
        .create();

    private HttpUtil() {}

    // ── Request helpers ───────────────────────────────────────────────────────

    /**
     * Deserialises the request body from JSON into the given class.
     *
     * @param exchange the current HTTP exchange
     * @param clazz    target class
     * @param <T>      target type
     * @return parsed object, or {@code null} if the body is empty
     * @throws IOException on read errors
     */
    public static <T> T readJson(HttpExchange exchange, Class<T> clazz) throws IOException {
        try (InputStream body = exchange.getRequestBody();
             InputStreamReader reader = new InputStreamReader(body, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, (Type) clazz);
        }
    }

    /**
     * Extracts the last path segment as a {@code long}.
     * For a path like {@code /api/accounts/1001} this returns {@code 1001}.
     *
     * @param exchange the current HTTP exchange
     * @return the last segment as a long
     * @throws NumberFormatException if the segment is not a valid long
     */
    public static long lastPathSegmentLong(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");
        return Long.parseLong(parts[parts.length - 1]);
    }

    /**
     * Extracts a named query parameter from the request URI.
     *
     * @param exchange the current HTTP exchange
     * @param name     the parameter name
     * @return the value, or {@code null} if absent
     */
    public static String queryParam(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null) return null;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(name)) return kv[1];
        }
        return null;
    }

    /**
     * Extracts the Bearer token from the {@code Authorization} header.
     *
     * @param exchange the current HTTP exchange
     * @return the raw token, or {@code null} if the header is absent/malformed
     */
    public static String bearerToken(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        return header.substring(7).strip();
    }

    // ── Response helpers ──────────────────────────────────────────────────────

    /**
     * Writes a JSON response with the given HTTP status code.
     *
     * @param exchange   the current HTTP exchange
     * @param statusCode HTTP status (e.g., 200, 400, 401, 403, 500)
     * @param body       the object to serialise as JSON
     * @throws IOException on write errors
     */
    public static void sendJson(HttpExchange exchange, int statusCode,
                                Object body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        addCorsHeaders(exchange);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /**
     * Handles HTTP OPTIONS pre-flight requests (CORS).
     * Returns 204 No Content immediately.
     *
     * @param exchange the current HTTP exchange
     * @throws IOException on write errors
     */
    public static void handleOptions(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);
        exchange.sendResponseHeaders(204, -1);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin",  "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods",
                                          "GET, POST, PATCH, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers",
                                          "Authorization, Content-Type");
    }
}
