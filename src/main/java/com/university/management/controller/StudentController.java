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
@RequestMapping("/api/students")
public class StudentController {

    private final UserService userService;

    public StudentController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> createStudent(
            @Valid @RequestBody CreateUserRequest request
    ) {

        UserResponse response = userService.createUser(
                request,
                Role.STUDENT
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllStudents() {

        return ResponseEntity.ok(
                userService.getUsersByRole(Role.STUDENT)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getStudentById(
            @PathVariable Long id
    ) {

        return ResponseEntity.ok(
                userService.getUserById(id, Role.STUDENT)
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateStudent(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request
    ) {

        return ResponseEntity.ok(
                userService.updateUser(
                        id,
                        Role.STUDENT,
                        request
                )
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStudent(
            @PathVariable Long id
    ) {

        userService.deleteUser(id, Role.STUDENT);

        return ResponseEntity.noContent().build();
    }
}