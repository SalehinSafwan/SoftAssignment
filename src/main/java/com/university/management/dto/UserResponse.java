package com.university.management.dto;

import com.university.management.entity.Role;

import java.time.LocalDateTime;

public record UserResponse(

        Long id,
        String name,
        String email,
        Role role,
        String department,
        String phone,
        LocalDateTime createdAt

) {}