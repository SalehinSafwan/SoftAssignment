package com.university.management.service;

import com.university.management.entity.Role;
import com.university.management.entity.User;

import com.university.management.repository.UserRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * UNIT TESTS for CustomUserDetailsService: the class Spring Security calls
 * during login to look a user up by email.
 */
@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;


    // UNIT TEST 5
    @Test
    @DisplayName("loadUserByUsername maps our User entity to a Spring Security user with ROLE_ prefix")
    void loadUserByUsername_existingUser_returnsUserDetailsWithRole() {

        User user = new User();
        user.setEmail("teacher@uni.edu");
        user.setPassword("HASHED");
        user.setRole(Role.TEACHER);

        when(userRepository.findByEmail("teacher@uni.edu")).thenReturn(Optional.of(user));

        UserDetails details = customUserDetailsService.loadUserByUsername("teacher@uni.edu");

        assertEquals("teacher@uni.edu", details.getUsername());
        assertEquals("HASHED", details.getPassword());

        // .roles("TEACHER") becomes the authority "ROLE_TEACHER",
        // which is what hasRole("TEACHER") in SecurityConfig checks
        assertEquals(1, details.getAuthorities().size());
        GrantedAuthority authority = details.getAuthorities().iterator().next();
        assertEquals("ROLE_TEACHER", authority.getAuthority());
    }


    // UNIT TEST 6
    @Test
    @DisplayName("loadUserByUsername throws UsernameNotFoundException for an unknown email")
    void loadUserByUsername_unknownEmail_throwsException() {

        when(userRepository.findByEmail("ghost@uni.edu")).thenReturn(Optional.empty());

        UsernameNotFoundException ex = assertThrows(
                UsernameNotFoundException.class,
                () -> customUserDetailsService.loadUserByUsername("ghost@uni.edu")
        );

        assertEquals("User not found: ghost@uni.edu", ex.getMessage());
    }
}
