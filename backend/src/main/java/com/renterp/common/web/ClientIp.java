package com.renterp.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The caller's IP address for the current request (rate limits, H11).
 *
 * Behind a reverse proxy the real address arrives in X-Forwarded-For; with
 * {@code server.forward-headers-strategy=native} (application.yml) Tomcat
 * replaces the remote address with it, but only when the proxy itself is on a
 * private network, so a client cannot fake its address by sending the header.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String current() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servlet) {
            HttpServletRequest request = servlet.getRequest();
            String ip = request.getRemoteAddr();
            return ip == null || ip.isBlank() ? "unknown" : ip;
        }
        return "no-request";
    }
}
