package com.university.management.controller;

import com.university.management.dto.LoginRequest;
import com.university.management.dto.UserResponse;

import com.university.management.entity.User;
import com.university.management.repository.UserRepository;

import com.university.management.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    public AuthController(
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            UserRepository userRepository,
            UserService userService
    ) {

        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.userRepository = userRepository;
        this.userService = userService;
    }


    // GET CSRF TOKEN

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken csrfToken) {

        return Map.of(
                "token", csrfToken.getToken()
        );
    }


    // LOGIN

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {

        Authentication authentication =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                request.email(),
                                request.password()
                        )
                );

        // Create authenticated security context
        SecurityContext context =
                SecurityContextHolder.createEmptyContext();

        context.setAuthentication(authentication);

        SecurityContextHolder.setContext(context);

        // Create and rotate the session ID
        httpRequest.getSession(true);
        httpRequest.changeSessionId();

        // Save authentication in the HTTP session
        securityContextRepository.saveContext(
                context,
                httpRequest,
                httpResponse
        );

        User user = userRepository.findByEmail(request.email())
                .orElseThrow();

        return ResponseEntity.ok(
                Map.of(
                        "message", "Login successful",
                        "email", user.getEmail(),
                        "role", user.getRole()
                )
        );
    }


    // CURRENT USER

    @GetMapping("/me")
    public ResponseEntity<UserResponse> currentUser(
            Authentication authentication
    ) {

        User user = userRepository.findByEmail(
                authentication.getName()
        ).orElseThrow();

        return ResponseEntity.ok(
                userService.getUserById(
                        user.getId(),
                        user.getRole()
                )
        );
    }


    // LOGOUT

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            HttpServletRequest request
    ) {

        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        SecurityContextHolder.clearContext();

        return ResponseEntity.ok(
                Map.of(
                        "message", "Logout successful"
                )
        );
    }
}