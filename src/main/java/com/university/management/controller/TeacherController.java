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
@RequestMapping("/api/teachers")
public class TeacherController {

    private final UserService userService;

    public TeacherController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> createTeacher(
            @Valid @RequestBody CreateUserRequest request
    ) {

        UserResponse response = userService.createUser(
                request,
                Role.TEACHER
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllTeachers() {

        return ResponseEntity.ok(
                userService.getUsersByRole(Role.TEACHER)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getTeacherById(
            @PathVariable Long id
    ) {

        return ResponseEntity.ok(
                userService.getUserById(id, Role.TEACHER)
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateTeacher(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request
    ) {

        return ResponseEntity.ok(
                userService.updateUser(
                        id,
                        Role.TEACHER,
                        request
                )
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTeacher(
            @PathVariable Long id
    ) {

        userService.deleteUser(id, Role.TEACHER);

        return ResponseEntity.noContent().build();
    }
}