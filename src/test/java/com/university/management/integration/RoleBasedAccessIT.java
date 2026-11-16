package com.university.management.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * INTEGRATION TEST 3: the security rules in SecurityConfig.
 *
 * Proves the three layers of protection really work in the running app:
 *   401 = not logged in, 403 = logged in but wrong role, 403 = missing CSRF token.
 */
class RoleBasedAccessIT extends IntegrationTestSupport {

    @Test
    @DisplayName("Anonymous gets 401, a student only reaches /api/students, and CSRF is enforced")
    void securityRules_areEnforced() throws Exception {

        // NOT LOGGED IN -> 401 on protected API, public endpoints still open
        mockMvc.perform(get("/api/students"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        // Admin creates a student account through the API
        Client admin = loginAs(ADMIN_EMAIL, ADMIN_PASSWORD);
        String studentEmail = uniqueEmail("rbac.student");

        mockMvc.perform(post("/api/students")
                        .session(admin.session())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("Karim", studentEmail, "Student@123", "CSE")))
                .andExpect(status().isCreated());

        // That student logs in
        Client student = loginAs(studentEmail, "Student@123");

        // ALLOWED: students may use /api/students
        mockMvc.perform(get("/api/students").session(student.session()))
                .andExpect(status().isOk());

        // FORBIDDEN: wrong role -> 403
        mockMvc.perform(get("/api/teachers").session(student.session()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not have permission to access this resource"));

        mockMvc.perform(get("/api/admins").session(student.session()))
                .andExpect(status().isForbidden());

        // CSRF: a POST without the X-CSRF-TOKEN header is rejected even for an admin
        mockMvc.perform(post("/api/teachers")
                        .session(admin.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("Teacher", uniqueEmail("t"), "Teacher@123", "CSE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(startsWith("Invalid or missing CSRF token")));
    }
}
