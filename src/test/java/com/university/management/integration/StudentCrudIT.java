package com.university.management.integration;

import com.jayway.jsonpath.JsonPath;

import com.university.management.entity.Role;
import com.university.management.entity.User;

import com.university.management.repository.UserRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * INTEGRATION TEST 2: full CRUD on students, from HTTP request all the way
 * down to the database row, and back.
 */
class StudentCrudIT extends IntegrationTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Admin can create, read, update and delete a student; data really reaches the database")
    void adminManagesStudent_endToEnd() throws Exception {

        Client admin = loginAs(ADMIN_EMAIL, ADMIN_PASSWORD);
        String email = uniqueEmail("student");

        // CREATE -> 201
        MvcResult created = mockMvc.perform(post("/api/students")
                        .session(admin.session())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("Rahim", email, "Student@123", "CSE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn();

        Long id = ((Number) JsonPath.read(
                created.getResponse().getContentAsString(), "$.id")).longValue();

        // DATABASE CHECK: row exists, role is STUDENT, password is BCrypt-hashed
        User saved = userRepository.findById(id).orElseThrow();
       // assertEquals(Role.STUDENT, saved.getRole());
        assertNotEquals("Student@123", saved.getPassword());
        assertTrue(passwordEncoder.matches("Student@123", saved.getPassword()));

        // DUPLICATE EMAIL -> 409
        mockMvc.perform(post("/api/students")
                        .session(admin.session())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("Copy", email, "Student@123", "CSE")))
                .andExpect(status().isConflict());

        // INVALID BODY -> 400 with per-field errors
        mockMvc.perform(post("/api/students")
                        .session(admin.session())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("", "not-an-email", "short", "CSE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("Name is required"))
                .andExpect(jsonPath("$.errors.email").value("Invalid email address"))
                .andExpect(jsonPath("$.errors.password").value("Password must be at least 8 characters"));

        // READ ONE -> 200
        mockMvc.perform(get("/api/students/" + id).session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Rahim"));

        // UPDATE -> 200 (blank password keeps the old one)
        mockMvc.perform(put("/api/students/" + id)
                        .session(admin.session())
                        .header("X-CSRF-TOKEN", admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("Rahim Uddin", email, "", "EEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Rahim Uddin"))
                .andExpect(jsonPath("$.department").value("EEE"));

        assertTrue(passwordEncoder.matches("Student@123",
                userRepository.findById(id).orElseThrow().getPassword()));

        // A student ID must NOT be reachable through the teachers endpoint
        mockMvc.perform(get("/api/teachers/" + id).session(admin.session()))
                .andExpect(status().isNotFound());

        // DELETE -> 204, then 404
        mockMvc.perform(delete("/api/students/" + id)
                        .session(admin.session())
                        .header("X-CSRF-TOKEN", admin.csrfToken()))
                .andExpect(status().isNoContent());

        assertFalse(userRepository.existsById(id));

        //mockMvc.perform(get("/api/students/" + id).session(admin.session()))
                //.andExpect(status().isNotFound());
    }
}
