package com.renterp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Permit all requests — real auth (OTP + JWT) implemented in AuthController phase
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            // Disable CSRF — REST APIs are stateless, CSRF only applies to browser form sessions
            .csrf(AbstractHttpConfigurer::disable)
            // Stateless — no HTTP session created
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }
}
