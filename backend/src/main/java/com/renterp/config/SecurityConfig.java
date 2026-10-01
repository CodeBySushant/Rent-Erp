package com.renterp.config;

import com.renterp.domain.auth.repository.UserSessionRepository;
import com.renterp.domain.auth.security.JwtAuthenticationFilter;
import com.renterp.domain.auth.security.JwtService;
import com.renterp.domain.auth.security.RestAccessDeniedHandler;
import com.renterp.domain.auth.security.RestAuthenticationEntryPoint;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless token security.
 *
 * Public: the sign-up / login / refresh endpoints and the health probe.
 * Everything else needs a valid access token ({@code Authorization: Bearer})
 * whose session is still active. Resource-level rules (whose property, whose
 * bill) are enforced in the services through AccessGuard.
 *
 * {@code app.auth.enforce} (AUTH_ENFORCED, default true): set false in a
 * developer's .env only, to run the pre-auth Postman collections. It never
 * defaults to false and is logged loudly at startup when off.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LogManager.getLogger(SecurityConfig.class);

    private static final String[] PUBLIC_POST = {
            "/api/v1/auth/otp/request",
            "/api/v1/auth/otp/verify",
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/login/password",
            "/api/v1/auth/refresh",
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtService jwtService,
                                           UserSessionRepository sessionRepository,
                                           @Value("${app.auth.enforce:true}") boolean enforce) throws Exception {
        if (!enforce) {
            log.warn("AUTH_ENFORCED=false - requests without a token are allowed. Development only.");
        }
        http
            // REST API with bearer tokens: no cookies, no CSRF, no server session.
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e
                    .authenticationEntryPoint(new RestAuthenticationEntryPoint())
                    .accessDeniedHandler(new RestAccessDeniedHandler()))
            .addFilterBefore(new JwtAuthenticationFilter(jwtService, sessionRepository),
                    UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers(HttpMethod.POST, PUBLIC_POST).permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                    .requestMatchers("/error").permitAll()
                    .requestMatchers("/api/v1/auth/logout", "/api/v1/auth/me").authenticated()
                    .requestMatchers("/actuator/**").hasRole("ADMIN");
                if (enforce) {
                    auth.anyRequest().authenticated();
                } else {
                    auth.anyRequest().permitAll();
                }
            });

        return http.build();
    }
}
