package com.example.book_store.shop;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "shops")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
class Shop {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long ownerId;
    private String name;
    private String slug;
    private String description;
    private String phone;
    private String email;
    private String pickupAddress;
    @Column(insertable = false, updatable = false) // DB default
    private Instant createdAt;
}
