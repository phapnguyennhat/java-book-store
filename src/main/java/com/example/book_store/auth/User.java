package com.example.book_store.auth;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "users")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String fullName;
    private String email;
    private String passwordHash;
    private LocalDate dateOfBirth;
    @Enumerated(EnumType.STRING)
    private Sex sex;
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Role role = Role.USER;
    private Instant emailVerifiedAt;
    @Column(insertable = false, updatable = false) // DB default
    private Instant createdAt;

    void markEmailVerified() {
        if (emailVerifiedAt == null) emailVerifiedAt = Instant.now();
    }
}
