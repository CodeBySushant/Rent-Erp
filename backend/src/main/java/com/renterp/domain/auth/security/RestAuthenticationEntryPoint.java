package com.renterp.domain.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/** 401 for routes that need a user. Code tells the app whether to refresh or log in again. */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Object reason = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
        String code = reason == null ? "UNAUTHENTICATED" : reason.toString();
        String message = switch (code) {
            case "TOKEN_EXPIRED" -> "Your session token has expired. Refresh it and try again.";
            case "SESSION_REVOKED" -> "This session has ended. Please log in again.";
            case "TOKEN_INVALID" -> "The access token is not valid. Please log in again.";
            default -> "Please log in to continue.";
        };
        JsonErrorWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, message, code);
    }
}
