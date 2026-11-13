package com.example.book_store.shop;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.net.URL;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

record ShopRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*") String slug,
        @Size(max = 2000) String description,
        @NotBlank @Pattern(regexp = "\\+?\\d{9,15}") String phone,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 500) String pickupAddress) {
}

/** The owner's view: includes the KYC state so the seller knows what to do next. */
record MyShopResponse(Long id, String name, String slug, String description, String phone, String email,
                      String pickupAddress, KycStatus kycStatus, String rejectionReason) {
    static MyShopResponse of(Shop s, ShopKyc k) {
        return new MyShopResponse(s.getId(), s.getName(), s.getSlug(), s.getDescription(), s.getPhone(), s.getEmail(),
                s.getPickupAddress(), k == null ? KycStatus.NOT_SUBMITTED : k.getStatus(),
                k == null ? null : k.getRejectionReason());
    }
}

/** Public view: no owner, KYC or pickup address. */
record ShopResponse(Long id, String name, String slug, String description, String phone, String email) {
    static ShopResponse of(Shop s) {
        return new ShopResponse(s.getId(), s.getName(), s.getSlug(), s.getDescription(), s.getPhone(), s.getEmail());
    }
}

record UploadUrlRequest(
        @NotNull DocumentType type,
        @NotBlank String contentType,
        @Positive @Max(KycService.MAX_FILE_SIZE) long size) {
}

/** Client uploads with {@code PUT uploadUrl}, sending exactly these headers, then submits {@code key} in the KYC. */
record UploadUrlResponse(String key, URL uploadUrl, Map<String, String> headers, Instant expiresAt) {
}

/**
 * KYC draft, saved step by step: every field may be missing, but present ones must be well-formed (Default group).
 * Completeness is checked only on submission (group {@link Submit}).
 */
record KycRequest(
        @NotNull(groups = Submit.class) SellerType sellerType,
        // the seller (INDIVIDUAL) or the legal representative (business)
        @NotBlank(groups = Submit.class) @Size(max = 255) String legalName,
        @NotBlank(groups = Submit.class) @Pattern(regexp = "\\d{12}") String idNumber,
        @NotNull(groups = Submit.class) @PastOrPresent LocalDate idIssueDate,
        @NotBlank(groups = Submit.class) @Size(max = 255) String idIssuePlace,
        @NotNull(groups = Submit.class) @Past LocalDate dateOfBirth,
        @NotBlank(groups = Submit.class) @Size(max = 500) String permanentAddress,
        @NotBlank(groups = Submit.class) String idFrontKey,
        @NotBlank(groups = Submit.class) String idBackKey,
        @NotBlank(groups = Submit.class) String selfieKey,
        @Valid BusinessInfo business, // ignored for INDIVIDUAL, so switching type in the wizard needs no cleanup
        @NotNull(groups = Submit.class) @Valid BankAccount bank) {

    interface Submit {
    }

    /** Reported as field "business": business sellers must fill it in before submitting. */
    @AssertTrue(groups = Submit.class)
    boolean isBusiness() {
        return sellerType == null || sellerType == SellerType.INDIVIDUAL || business != null;
    }

    record BusinessInfo(
            @NotBlank(groups = Submit.class) @Size(max = 255) String businessName,
            @NotBlank(groups = Submit.class) @Size(max = 50) String registrationNumber,
            @NotNull(groups = Submit.class) @PastOrPresent LocalDate registrationDate,
            @NotBlank(groups = Submit.class) @Pattern(regexp = "\\d{10}(-\\d{3})?") String taxCode,
            @NotBlank(groups = Submit.class) @Size(max = 500) String registeredAddress,
            @NotBlank(groups = Submit.class) String licenseKey) {
    }

    record BankAccount(
            @NotBlank(groups = Submit.class) @Size(max = 20) String bankCode,
            @NotBlank(groups = Submit.class) @Pattern(regexp = "\\d{6,20}") String accountNumber,
            @NotBlank(groups = Submit.class) @Size(max = 255) String accountHolder) {
    }
}

/** Full KYC for the owner and admins; document links are short-lived presigned GET URLs. */
record KycResponse(Long shopId, SellerType sellerType, KycStatus status, String legalName, String idNumber,
                   LocalDate idIssueDate, String idIssuePlace, LocalDate dateOfBirth, String permanentAddress,
                   KycRequest.BusinessInfo business, KycRequest.BankAccount bank, Map<DocumentType, URL> documents,
                   Instant submittedAt, Instant reviewedAt, String rejectionReason) {
}

record KycSummary(Long shopId, String shopName, SellerType sellerType, String legalName, KycStatus status,
                  Instant submittedAt) {
}

record ReviewRequest(@NotNull Decision decision, @Size(max = 1000) String reason) {
    enum Decision {APPROVED, REJECTED}

    /** Reported as field "reason": the seller must be told why, so a rejection needs one. */
    @AssertTrue
    boolean isReason() {
        return decision != Decision.REJECTED || (reason != null && !reason.isBlank());
    }
}
