package com.university.management.service;

import com.university.management.dto.CreateUserRequest;
import com.university.management.dto.UpdateUserRequest;
import com.university.management.dto.UserResponse;

import com.university.management.entity.Role;
import com.university.management.entity.User;

import com.university.management.exception.DuplicateEmailException;

import com.university.management.repository.UserRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * UNIT TESTS for UserService.
 *
 * No Spring, no database: the repository and password encoder are Mockito
 * "mocks" (fake objects whose answers we script), so only the logic inside
 * UserService is tested.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    // Mockito builds a real UserService and passes the two mocks to its constructor
    @InjectMocks
    private UserService userService;


    // UNIT TEST 1
    @Test
    @DisplayName("createUser hashes the password and uses the role given by the controller")
    void createUser_hashesPasswordAndAssignsRole() {

        // ARRANGE
        CreateUserRequest request = new CreateUserRequest(
                "Alice", "alice@uni.edu", "plainPassword", "CSE", "01711111111"
        );

        when(userRepository.existsByEmail("alice@uni.edu")).thenReturn(false);
        when(passwordEncoder.encode("plainPassword")).thenReturn("HASHED_PASSWORD");

        // Pretend the database saved the user and gave it ID 1
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        // ACT
        UserResponse response = userService.createUser(request, Role.STUDENT);

        // ASSERT: the response
        assertEquals(1L, response.id());
        assertEquals("alice@uni.edu", response.email());
        assertEquals(Role.STUDENT, response.role());

        // ASSERT: what was actually given to the database
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        assertEquals("HASHED_PASSWORD", captor.getValue().getPassword(),
                "The password must be stored hashed, never as plain text");
    }


    // UNIT TEST 2
    @Test
    @DisplayName("createUser rejects an email that is already registered")
    void createUser_duplicateEmail_throwsException() {

        CreateUserRequest request = new CreateUserRequest(
                "Bob", "taken@uni.edu", "password123", null, null
        );

        when(userRepository.existsByEmail("taken@uni.edu")).thenReturn(true);

        DuplicateEmailException ex = assertThrows(
                DuplicateEmailException.class,
                () -> userService.createUser(request, Role.TEACHER)
        );

        assertEquals("Email already exists: taken@uni.edu", ex.getMessage());

        // Nothing may be saved when validation fails
        verify(userRepository, never()).save(any());
    }


    // UNIT TEST 3
    @Test
    @DisplayName("updateUser keeps the old password when the new password is blank")
    void updateUser_blankPassword_keepsOldPassword() {

        User existing = buildUser(5L, "old@uni.edu", "OLD_HASH", Role.TEACHER);

        when(userRepository.findByIdAndRole(5L, Role.TEACHER)).thenReturn(Optional.of(existing));
        when(userRepository.findByEmail("new@uni.edu")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateUserRequest request = new UpdateUserRequest(
                "New Name", "new@uni.edu", "", "EEE", "01822222222"
        );

        UserResponse response = userService.updateUser(5L, Role.TEACHER, request);

        assertEquals("New Name", response.name());
        assertEquals("new@uni.edu", response.email());
        assertEquals("EEE", response.department());

        // Password untouched, encoder never called
        assertEquals("OLD_HASH", existing.getPassword());
        verify(passwordEncoder, never()).encode(anyString());
    }


    // UNIT TEST 4
    @Test
    @DisplayName("updateUser rejects a new password shorter than 8 characters")
    void updateUser_shortPassword_throwsException() {

        User existing = buildUser(7L, "carol@uni.edu", "OLD_HASH", Role.STUDENT);

        when(userRepository.findByIdAndRole(7L, Role.STUDENT)).thenReturn(Optional.of(existing));
        when(userRepository.findByEmail("carol@uni.edu")).thenReturn(Optional.of(existing));

        UpdateUserRequest request = new UpdateUserRequest(
                "Carol", "carol@uni.edu", "short", null, null
        );

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> userService.updateUser(7L, Role.STUDENT, request)
        );

        assertEquals("Password must be at least 8 characters", ex.getMessage());
        verify(userRepository, never()).save(any());
    }


    // HELPER

    private User buildUser(Long id, String email, String passwordHash, Role role) {

        User user = new User();
        user.setId(id);
        user.setName("Existing User");
        user.setEmail(email);
        user.setPassword(passwordHash);
        user.setRole(role);
        return user;
    }
}
