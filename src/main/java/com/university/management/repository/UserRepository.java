package com.university.management.repository;

import com.university.management.entity.User;
import com.university.management.entity.Role;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    List<User> findByRole(Role role);

    Optional<User> findByIdAndRole(Long id, Role role);

    boolean existsByEmail(String email);
}