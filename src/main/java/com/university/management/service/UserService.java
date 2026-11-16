package com.university.management.service;

import com.university.management.dto.CreateUserRequest;
import com.university.management.dto.UpdateUserRequest;
import com.university.management.dto.UserResponse;

import com.university.management.entity.User;
import com.university.management.entity.Role;

import com.university.management.exception.ResourceNotFoundException;
import com.university.management.exception.DuplicateEmailException;

import com.university.management.repository.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // CREATE USER

    public UserResponse createUser(
            CreateUserRequest request,
            Role role
    ) {

        // Check duplicate email
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException(
                    "Email already exists: " + request.email()
            );
        }

        User user = new User();

        user.setName(request.name());
        user.setEmail(request.email());

        // Hash password before saving
        user.setPassword(
                passwordEncoder.encode(request.password())
        );

        // Assign role from the controller, NOT the request
        user.setRole(role);

        user.setDepartment(request.department());
        user.setPhone(request.phone());

        User savedUser = userRepository.save(user);

        return convertToResponse(savedUser);
    }


    // GET ALL USERS OF A PARTICULAR ROLE

    @Transactional(readOnly = true)
    public List<UserResponse> getUsersByRole(Role role) {

        return userRepository.findByRole(role)
                .stream()
                .map(this::convertToResponse)
                .toList();
    }


    // GET USER BY ID AND ROLE

    @Transactional(readOnly = true)
    public UserResponse getUserById(
            Long id,
            Role role
    ) {

        User user = userRepository.findByIdAndRole(id, role)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                role + " not found with ID: " + id
                        )
                );

        return convertToResponse(user);
    }


    // UPDATE USER

    public UserResponse updateUser(
            Long id,
            Role role,
            UpdateUserRequest request
    ) {

        User user = userRepository.findByIdAndRole(id, role)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                role + " not found with ID: " + id
                        )
                );

        // Check whether the new email belongs to another user
        userRepository.findByEmail(request.email())
                .ifPresent(existingUser -> {

                    if (!existingUser.getId().equals(id)) {
                        throw new DuplicateEmailException(
                                "Email already exists: " + request.email()
                        );
                    }
                });

        user.setName(request.name());
        user.setEmail(request.email());

        // Update password only if a new one was supplied
        if (request.password() != null &&
                !request.password().isBlank()) {

            if (request.password().length() < 8) {
                throw new IllegalArgumentException(
                        "Password must be at least 8 characters"
                );
            }

            user.setPassword(
                    passwordEncoder.encode(request.password())
            );
        }

        user.setDepartment(request.department());
        user.setPhone(request.phone());

        User updatedUser = userRepository.save(user);

        return convertToResponse(updatedUser);
    }


    // DELETE USER

    public void deleteUser(
            Long id,
            Role role
    ) {

        User user = userRepository.findByIdAndRole(id, role)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                role + " not found with ID: " + id
                        )
                );

        userRepository.delete(user);
    }


    // CONVERT ENTITY TO RESPONSE DTO

    private UserResponse convertToResponse(User user) {

        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getDepartment(),
                user.getPhone(),
                user.getCreatedAt()
        );
    }
}