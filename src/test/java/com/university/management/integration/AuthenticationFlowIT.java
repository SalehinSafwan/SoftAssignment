package com.university.management.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * INTEGRATION TEST 1: the complete session-based login lifecycle.
 *
 * Exercises together: SecurityConfig + AuthController + AuthenticationManager +
 * CustomUserDetailsService + BCrypt + UserRepository + H2 + the admin seeded
 * by DataInitializer + GlobalExceptionHandler.
 */
class AuthenticationFlowIT extends IntegrationTestSupport {

    @Test
    @DisplayName("Login with wrong password fails, correct login creates a session, logout destroys it")
    void loginMeLogout_fullLifecycle() throws Exception {

        MockHttpSession session = new MockHttpSession();
        String csrfToken = fetchCsrfToken(session);

        // 1. Wrong password -> 401 (BadCredentialsException -> GlobalExceptionHandler)
        mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(ADMIN_EMAIL, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));

        // 2. Correct password for the seeded admin -> 200 with role ADMIN
        mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(ADMIN_EMAIL, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        // 3. The session now remembers who we are
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.password").doesNotExist()); // never leak the hash

        // 4. Logout invalidates the session
        mockMvc.perform(post("/api/auth/logout")
                        .session(session)
                        .header("X-CSRF-TOKEN", csrfToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logout successful"));

        // 5. The old session no longer works -> 401 JSON from SecurityConfig's entry point
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }
}
