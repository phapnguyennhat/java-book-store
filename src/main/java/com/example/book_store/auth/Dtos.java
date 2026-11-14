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

record VerifyEmailRequest(@NotBlank String token) {
}

record ResendVerificationRequest(@NotBlank @Email String email) {
}

record UserResponse(Long id, String fullName, String email, boolean emailVerified, LocalDate dateOfBirth, Sex sex, Role role) {
    static UserResponse of(User u) {
        return new UserResponse(u.getId(), u.getFullName(), u.getEmail(), u.getEmailVerifiedAt() != null,
                u.getDateOfBirth(), u.getSex(), u.getRole());
    }
}
