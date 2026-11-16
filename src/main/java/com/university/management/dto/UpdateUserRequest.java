package com.university.management.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UpdateUserRequest(

        @NotBlank(message = "Name is required")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email address")
        String email,

        // Optional: leave null or blank to keep the old password
        String password,

        String department,

        String phone

) {}