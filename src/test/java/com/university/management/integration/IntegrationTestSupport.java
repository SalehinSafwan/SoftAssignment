package com.university.management.integration;

import com.jayway.jsonpath.JsonPath;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import org.springframework.http.MediaType;

import org.springframework.mock.web.MockHttpSession;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared setup for all integration tests.
 *
 * @SpringBootTest        starts the WHOLE application (controllers, services,
 *                        security, JPA, DataInitializer), just like production.
 * @AutoConfigureMockMvc  gives us MockMvc, which sends HTTP requests through the
 *                        full Spring Security filter chain without opening a port.
 * @ActiveProfiles("test") loads application-test.properties (H2 instead of MySQL).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class IntegrationTestSupport {

    // Matches app.admin.* in src/test/resources/application-test.properties
    protected static final String ADMIN_EMAIL = "admin@test.edu";
    protected static final String ADMIN_PASSWORD = "AdminTest@123";

    @Autowired
    protected MockMvc mockMvc;


    /** A logged-in browser: its session cookie plus its CSRF token. */
    protected record Client(MockHttpSession session, String csrfToken) {}


    /** Same as the frontend's fetchCsrfToken(): GET /api/auth/csrf. */
    protected String fetchCsrfToken(MockHttpSession session) throws Exception {

        MvcResult result = mockMvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }


    /** Performs the real login flow (CSRF token, then POST /api/auth/login). */
    protected Client loginAs(String email, String password) throws Exception {

        MockHttpSession session = new MockHttpSession();
        String csrfToken = fetchCsrfToken(session);

        mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, password)))
                .andExpect(status().isOk());

        return new Client(session, csrfToken);
    }


    protected static String loginJson(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }


    protected static String userJson(String name, String email, String password, String department) {
        return """
                {"name": "%s", "email": "%s", "password": "%s", "department": "%s", "phone": "01900000000"}
                """.formatted(name, email, password, department);
    }


    /** Unique email per test run so tests never collide with each other. */
    protected static String uniqueEmail(String prefix) {
        return prefix + "." + System.nanoTime() + "@test.edu";
    }
}
