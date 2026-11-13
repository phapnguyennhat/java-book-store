package com.example.book_store.shop;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "shop_kycs")
@Getter
@Setter
@NoArgsConstructor
class ShopKyc {
    @Id
    private Long shopId;
    @Enumerated(EnumType.STRING)
    private SellerType sellerType;
    @Enumerated(EnumType.STRING)
    private KycStatus status;

    private String legalName;
    @JdbcTypeCode(SqlTypes.CHAR) // CHAR(12) column
    private String idNumber;
    private LocalDate idIssueDate;
    private String idIssuePlace;
    private LocalDate dateOfBirth;
    private String permanentAddress;
    private String idFrontKey;
    private String idBackKey;
    private String selfieKey;

    private String businessName;
    private String registrationNumber;
    private LocalDate registrationDate;
    private String taxCode;
    private String registeredAddress;
    private String businessLicenseKey;

    private String bankCode;
    private String accountNumber;
    private String accountHolder;

    private Instant submittedAt;
    private Long reviewedBy;
    private Instant reviewedAt;
    private String rejectionReason;
    @Version // two admins reviewing at once: the second one gets a conflict instead of overwriting
    private long version;

    ShopKyc(Long shopId) {
        this.shopId = shopId;
    }
}
