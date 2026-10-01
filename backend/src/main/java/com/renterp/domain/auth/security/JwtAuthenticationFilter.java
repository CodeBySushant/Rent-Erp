package com.renterp.domain.auth.security;

import com.renterp.domain.auth.repository.UserSessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Reads {@code Authorization: Bearer <access token>}, verifies it, and checks
 * the session behind it is still active (not logged out, not revoked, not
 * expired). On success the request runs as that {@link AuthUser}.
 *
 * A missing or bad token is not rejected here: the request continues
 * unauthenticated, and Spring Security answers 401 if the route needs a user.
 * The reason (TOKEN_EXPIRED, TOKEN_INVALID, SESSION_REVOKED) is left on the
 * request for {@link RestAuthenticationEntryPoint}, so the app can tell
 * "refresh your token" from "log in again".
 *
 * Not a Spring bean on purpose: it is added to the security chain only, so the
 * servlet container does not register it a second time.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_ERROR_ATTRIBUTE = "renterp.auth.error";

    private final JwtService jwtService;
    private final UserSessionRepository sessionRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserSessionRepository sessionRepository) {
        this.jwtService = jwtService;
        this.sessionRepository = sessionRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            JwtService.Verification v = jwtService.verify(header.substring(7).trim());
            if (!v.ok()) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, v.errorCode());
            } else if (!sessionRepository.isActive(v.user().sessionId(), v.user().userId(), Instant.now())) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, "SESSION_REVOKED");
            } else {
                AuthUser user = v.user();
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }
}
