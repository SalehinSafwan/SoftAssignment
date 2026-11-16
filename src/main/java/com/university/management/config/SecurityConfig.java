package com.university.management.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextRepository;

import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfException;

@Configuration
public class SecurityConfig {

    // PASSWORD ENCODER

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }


    // AUTHENTICATION MANAGER

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration
    ) throws Exception {

        return configuration.getAuthenticationManager();
    }


    // SECURITY CONTEXT REPOSITORY

    @Bean
    public SecurityContextRepository securityContextRepository() {

        return new HttpSessionSecurityContextRepository();
    }


    // SECURITY FILTER CHAIN

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository
    ) throws Exception {

        http

                // SESSION-BASED SECURITY
                .securityContext(context -> context
                        .securityContextRepository(securityContextRepository)
                )

                // CSRF PROTECTION
                .csrf(csrf -> csrf

                        .csrfTokenRepository(
                                new HttpSessionCsrfTokenRepository()
                        )

                        .csrfTokenRequestHandler(
                                new CsrfTokenRequestAttributeHandler()
                        )
                )

                // AUTHORIZATION RULES
                .authorizeHttpRequests(auth -> auth

                        // FRONTEND (static login page + dashboard)
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/css/**",
                                "/js/**",
                                "/favicon.ico"
                        ).permitAll()

                        .requestMatchers(
                                "/api/auth/csrf",
                                "/api/auth/login",
                                "/actuator/health",
                                "/error"
                        ).permitAll()

                        // ADMIN PERMISSIONS
                        .requestMatchers("/api/admins/**")
                        .hasRole("ADMIN")

                        // TEACHER PERMISSIONS
                        .requestMatchers("/api/teachers/**")
                        .hasAnyRole("ADMIN", "TEACHER")

                        // STUDENT PERMISSIONS
                        .requestMatchers("/api/students/**")
                        .hasAnyRole("ADMIN", "STUDENT")

                        // LOGOUT REQUIRES AUTHENTICATION
                        .requestMatchers("/api/auth/logout")
                        .authenticated()

                        // OTHER REQUESTS
                        .anyRequest().authenticated()
                )

                // DISABLE DEFAULT LOGIN PAGES
                .formLogin(AbstractHttpConfigurer::disable)

                // DISABLE HTTP BASIC AUTHENTICATION
                .httpBasic(AbstractHttpConfigurer::disable)

                // WE WILL HANDLE LOGOUT OURSELVES
                .logout(AbstractHttpConfigurer::disable)

                // JSON RESPONSE FOR UNAUTHENTICATED REQUESTS
                .exceptionHandling(ex -> ex

                        .authenticationEntryPoint((request, response, exception) -> {

                            response.setStatus(401);
                            response.setContentType("application/json");

                            response.getWriter().write(
                                    """
                                    {
                                        "status": 401,
                                        "message": "Authentication required"
                                    }
                                    """
                            );
                        })

                        .accessDeniedHandler((request, response, exception) -> {

                            response.setStatus(403);
                            response.setContentType("application/json");

                            // Missing/invalid CSRF token is also reported as access denied
                            String message = exception instanceof CsrfException
                                    ? "Invalid or missing CSRF token. Call GET /api/auth/csrf and send the token in the X-CSRF-TOKEN header"
                                    : "You do not have permission to access this resource";

                            response.getWriter().write(
                                    """
                                    {
                                        "status": 403,
                                        "message": "%s"
                                    }
                                    """.formatted(message)
                            );
                        })
                );

        return http.build();
    }
}