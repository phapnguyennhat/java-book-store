package com.example.book_store.shop;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** ADMIN only (SecurityConfig: /api/admin/**). */
@RestController
@RequestMapping("/api/admin/kyc")
@RequiredArgsConstructor
class AdminKycController {
    private final KycService kyc;

    /** Review queue, always oldest submission first (any client sort is ignored); page size capped at 100. */
    @GetMapping
    PagedModel<KycSummary> list(@RequestParam(defaultValue = "PENDING") KycStatus status, Pageable pageable) {
        return kyc.list(status, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("submittedAt", "shopId")));
    }

    @GetMapping("/{shopId}")
    KycResponse detail(@PathVariable Long shopId) {
        return kyc.detail(shopId);
    }

    /** Approve or reject a PENDING KYC: {"decision":"APPROVED"} or {"decision":"REJECTED","reason":"..."}. */
    @PostMapping("/{shopId}/review")
    KycResponse review(@PathVariable Long shopId, @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ReviewRequest r) {
        return kyc.review(shopId, ShopController.userId(jwt), r);
    }
}
