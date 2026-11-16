package com.university.management.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.AuthenticationException;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // RESOURCE NOT FOUND

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<?> handleNotFound(
            ResourceNotFoundException ex
    ) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                        "status", 404,
                        "message", ex.getMessage(),
                        "timestamp", LocalDateTime.now()
                ));
    }


    // DUPLICATE EMAIL

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<?> handleDuplicateEmail(
            DuplicateEmailException ex
    ) {

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "status", 409,
                        "message", ex.getMessage(),
                        "timestamp", LocalDateTime.now()
                ));
    }


    // VALIDATION ERRORS

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(
            MethodArgumentNotValidException ex
    ) {

        Map<String, String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        error -> error.getField(),
                        error -> error.getDefaultMessage(),
                        (first, second) -> first
                ));

        return ResponseEntity.badRequest()
                .body(Map.of(
                        "status", 400,
                        "message", "Validation failed",
                        "errors", errors,
                        "timestamp", LocalDateTime.now()
                ));
    }


    // INVALID PASSWORD DURING LOGIN

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<?> handleAuthentication(
            AuthenticationException ex
    ) {

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                        "status", 401,
                        "message", "Invalid email or password",
                        "timestamp", LocalDateTime.now()
                ));
    }


    // INVALID ARGUMENT

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgument(
            IllegalArgumentException ex
    ) {

        return ResponseEntity.badRequest()
                .body(Map.of(
                        "status", 400,
                        "message", ex.getMessage(),
                        "timestamp", LocalDateTime.now()
                ));
    }
}