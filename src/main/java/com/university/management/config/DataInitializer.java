package com.university.management.config;

import com.university.management.entity.User;
import com.university.management.entity.Role;

import com.university.management.repository.UserRepository;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.boot.CommandLineRunner;

import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:admin@university.edu}")
    private String adminEmail;

    @Value("${app.admin.password:Admin@123}")
    private String adminPassword;

    public DataInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {

        if (userRepository.existsByEmail(adminEmail)) {

            System.out.println("Initial Admin already exists.");

            return;
        }

        User admin = new User();

        admin.setName("System Administrator");
        admin.setEmail(adminEmail);

        admin.setPassword(
                passwordEncoder.encode(adminPassword)
        );

        admin.setRole(Role.ADMIN);

        admin.setDepartment("Administration");
        admin.setPhone("01700000000");

        userRepository.save(admin);

        System.out.println("Initial Admin created successfully!");
    }
}