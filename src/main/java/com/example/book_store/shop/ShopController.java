package com.example.book_store.shop;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequiredArgsConstructor
class ShopController {
    private final ShopService shops;
    private final KycService kyc;

    @GetMapping("/api/shops/{slug}")
    ShopResponse bySlug(@PathVariable String slug) {
        return shops.publicBySlug(slug);
    }

    @PostMapping("/api/my-shop")
    ResponseEntity<MyShopResponse> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ShopRequest r) {
        return ResponseEntity.created(URI.create("/api/my-shop")).body(shops.create(userId(jwt), r));
    }

    @GetMapping("/api/my-shop")
    MyShopResponse mine(@AuthenticationPrincipal Jwt jwt) {
        return shops.mine(userId(jwt));
    }

    @PutMapping("/api/my-shop")
    MyShopResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ShopRequest r) {
        return shops.update(userId(jwt), r);
    }

    /** Step 1 of KYC documents: get a presigned URL, then PUT the file to it directly (not through this API). */
    @PostMapping("/api/my-shop/kyc/upload-urls")
    UploadUrlResponse uploadUrl(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UploadUrlRequest r) {
        return kyc.uploadUrl(userId(jwt), r);
    }

    /**
     * Step 2: save the KYC draft (status DRAFT). Send the whole form so far on every wizard step; missing fields are
     * fine, going back to an earlier step is just another PUT.
     */
    @PutMapping("/api/my-shop/kyc")
    KycResponse saveKyc(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody KycRequest r) {
        return kyc.saveDraft(userId(jwt), r);
    }

    /** Step 3: send the draft for review (status PENDING); 400 lists missing fields. */
    @PostMapping("/api/my-shop/kyc/submission")
    KycResponse submitKyc(@AuthenticationPrincipal Jwt jwt) throws BindException {
        return kyc.submit(userId(jwt));
    }

    @GetMapping("/api/my-shop/kyc")
    KycResponse myKyc(@AuthenticationPrincipal Jwt jwt) {
        return kyc.mine(userId(jwt));
    }

    static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
