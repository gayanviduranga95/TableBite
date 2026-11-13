package com.tablebite.server.api;

import com.sun.net.httpserver.HttpExchange;
import com.tablebite.util.SecurityUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * Stores admin-uploaded food images on disk (UPLOAD_DIR, default ./uploads) and
 * serves them at /uploads/{file}. Files are validated by their magic bytes and
 * given random names, so user input never becomes part of a file path.
 */
public final class UploadStore {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final Pattern FILE_NAME = Pattern.compile("^[a-f0-9]{24}\\.(jpg|png|webp)$");

    private final Path directory;

    public UploadStore() throws IOException {
        directory = Paths.get(System.getenv().getOrDefault("UPLOAD_DIR", "uploads"))
                .toAbsolutePath().normalize();
        Files.createDirectories(directory);
    }

    public Path directory() {
        return directory;
    }

    /** Saves an image and returns its public URL path, e.g. /uploads/ab12....jpg */
    public String save(byte[] data) throws IOException {
        String extension = detectExtension(data);
        if (extension == null) {
            throw new IllegalArgumentException("Only JPEG, PNG or WebP images are supported");
        }
        String name = SecurityUtil.randomHex(12) + "." + extension;
        Files.write(directory.resolve(name), data);
        return "/uploads/" + name;
    }

    public void serve(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            String name = path.substring(path.lastIndexOf('/') + 1);
            if (!"GET".equals(exchange.getRequestMethod()) && !"HEAD".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            Path file = directory.resolve(name).normalize();
            if (!FILE_NAME.matcher(name).matches() || !file.startsWith(directory) || !Files.isRegularFile(file)) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            String type = name.endsWith(".png") ? "image/png"
                    : name.endsWith(".webp") ? "image/webp" : "image/jpeg";
            byte[] data = Files.readAllBytes(file);
            exchange.getResponseHeaders().set("Content-Type", type);
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=31536000, immutable");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            if ("HEAD".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(data);
            }
        }
    }

    static String detectExtension(byte[] data) {
        if (data == null || data.length < 12) {
            return null;
        }
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if ((data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return "png";
        }
        if (data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return "webp";
        }
        return null;
    }
}
