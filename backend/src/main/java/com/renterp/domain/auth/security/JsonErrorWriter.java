package com.renterp.domain.auth.security;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Writes the standard {@code {success:false, message, code, timestamp}} body
 * from the security layer, which runs before Spring MVC and its exception
 * handler. Messages are fixed strings from this package, escaped anyway.
 */
final class JsonErrorWriter {

    private JsonErrorWriter() {
    }

    static void write(HttpServletResponse response, int status, String message, String code)
            throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write("{\"success\":false,\"message\":\"" + escape(message)
                + "\",\"code\":\"" + escape(code) + "\",\"timestamp\":\"" + Instant.now() + "\"}");
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
