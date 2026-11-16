package com.university.management.controller;

import com.university.management.dto.CreateUserRequest;
import com.university.management.dto.UpdateUserRequest;
import com.university.management.dto.UserResponse;

import com.university.management.entity.Role;

import com.university.management.service.UserService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admins")
public class AdminController {

    private final UserService userService;

    public AdminController(UserService userService) {
        this.userService = userService;
    }

    // CREATE ADMIN

    @PostMapping
    public ResponseEntity<UserResponse> createAdmin(
            @Valid @RequestBody CreateUserRequest request
    ) {

        UserResponse response = userService.createUser(
                request,
                Role.ADMIN
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }


    // GET ALL ADMINS

    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllAdmins() {

        return ResponseEntity.ok(
                userService.getUsersByRole(Role.ADMIN)
        );
    }


    // GET ADMIN BY ID

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getAdminById(
            @PathVariable Long id
    ) {

        return ResponseEntity.ok(
                userService.getUserById(id, Role.ADMIN)
        );
    }


    // UPDATE ADMIN

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateAdmin(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request
    ) {

        return ResponseEntity.ok(
                userService.updateUser(
                        id,
                        Role.ADMIN,
                        request
                )
        );
    }


    // DELETE ADMIN

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAdmin(
            @PathVariable Long id
    ) {

        userService.deleteUser(id, Role.ADMIN);

        return ResponseEntity.noContent().build();
    }
}