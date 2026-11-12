package com.example.book_store.auth;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

record SignupRequest(
        @NotBlank @Size(max = 255) String fullName,
        @NotBlank @Email @Size(max = 255) String email,
        // max caps Argon2 work an attacker can force per request
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotNull @Past LocalDate dateOfBirth,
        @NotNull Sex sex) {
}

record LoginRequest(@NotBlank String email, @NotBlank String password) {
}

record UserResponse(Long id, String fullName, String email, LocalDate dateOfBirth, Sex sex) {
    static UserResponse of(User u) {
        return new UserResponse(u.getId(), u.getFullName(), u.getEmail(), u.getDateOfBirth(), u.getSex());
    }
}
