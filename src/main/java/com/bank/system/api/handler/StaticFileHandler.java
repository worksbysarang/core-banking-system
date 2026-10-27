package com.bank.system.api.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Serves the banking UI from {@code /static} on the classpath
 * (and from {@code src/main/resources/static} during local Maven runs).
 */
public class StaticFileHandler implements HttpHandler {

    private static final Logger log = LoggerFactory.getLogger(StaticFileHandler.class);

    private static final Map<String, String> CONTENT_TYPES = Map.of(
        ".html", "text/html; charset=UTF-8",
        ".css",  "text/css; charset=UTF-8",
        ".js",   "application/javascript; charset=UTF-8",
        ".svg",  "image/svg+xml",
        ".png",  "image/png",
        ".ico",  "image/x-icon",
        ".json", "application/json; charset=UTF-8"
    );

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())
                && !"HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        String path = exchange.getRequestURI().getPath();
        if (path == null || path.isBlank() || "/".equals(path)) {
            path = "/index.html";
        }
        if (path.contains("..") || path.contains("\\")) {
            sendNotFound(exchange);
            return;
        }

        byte[] bytes = readResource(path);
        if (bytes == null && !path.contains(".")) {
            bytes = readResource("/index.html");
        }
        if (bytes == null) {
            sendNotFound(exchange);
            return;
        }

        exchange.getResponseHeaders().set("Content-Type", contentType(path));
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, bytes.length);
            return;
        }
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private byte[] readResource(String path) {
        String resource = "/static" + (path.startsWith("/") ? path : "/" + path);

        // 1. Try disk first so local file modifications are served immediately
        Path disk = Path.of("src", "main", "resources").resolve(resource.substring(1));
        if (Files.isRegularFile(disk)) {
            try {
                return Files.readAllBytes(disk);
            } catch (IOException e) {
                log.debug("Disk read error for {}: {}", disk, e.getMessage());
            }
        }

        // 2. Fallback to classpath (for standalone fat-JAR deployment)
        try (InputStream in = StaticFileHandler.class.getResourceAsStream(resource)) {
            if (in != null) {
                return in.readAllBytes();
            }
        } catch (IOException e) {
            log.debug("Classpath miss for {}: {}", resource, e.getMessage());
        }

        return null;
    }

    private static String contentType(String path) {
        int dot = path.lastIndexOf('.');
        if (dot < 0) return "application/octet-stream";
        return CONTENT_TYPES.getOrDefault(path.substring(dot).toLowerCase(),
            "application/octet-stream");
    }

    private static void sendNotFound(HttpExchange exchange) throws IOException {
        byte[] body = """
            {"success":false,"error":"Not found"}
            """.getBytes();
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(404, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}
