package com.example.book_store.shop;

import com.example.book_store.web.ApiErrors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;

@Service
@RequiredArgsConstructor
class ShopService {
    private final ShopRepository shops;
    private final ShopKycRepository kycs;

    MyShopResponse create(Long ownerId, ShopRequest r) {
        if (shops.existsByOwnerId(ownerId)) throw alreadyExists();
        if (shops.existsBySlug(r.slug())) throw slugTaken();
        Shop shop = apply(Shop.builder().ownerId(ownerId).build(), r);
        try {
            return MyShopResponse.of(shops.saveAndFlush(shop), null);
        } catch (DataIntegrityViolationException e) { // a concurrent request won the unique owner/slug
            throw shops.existsByOwnerId(ownerId) ? alreadyExists() : slugTaken();
        }
    }

    @Transactional
    MyShopResponse update(Long ownerId, ShopRequest r) {
        Shop shop = owned(ownerId);
        if (shops.existsBySlugAndIdNot(r.slug(), shop.getId())) throw slugTaken();
        try {
            shops.saveAndFlush(apply(shop, r)); // UPDATE now, not at commit, so a unique violation is caught here
        } catch (DataIntegrityViolationException e) { // a concurrent request took the slug after our check
            throw slugTaken();
        }
        return MyShopResponse.of(shop, kycs.findById(shop.getId()).orElse(null));
    }

    @Transactional(readOnly = true)
    MyShopResponse mine(Long ownerId) {
        Shop shop = owned(ownerId);
        return MyShopResponse.of(shop, kycs.findById(shop.getId()).orElse(null));
    }

    /** Only shops whose KYC is approved are public; anything else looks like it does not exist. */
    @Transactional(readOnly = true)
    ShopResponse publicBySlug(String slug) {
        return shops.findBySlug(slug)
                .filter(s -> kycs.findById(s.getId()).map(k -> k.getStatus() == KycStatus.APPROVED).orElse(false))
                .map(ShopResponse::of).orElseThrow(ShopService::notFound);
    }

    Shop owned(Long ownerId) {
        return shops.findByOwnerId(ownerId).orElseThrow(ShopService::notFound);
    }

    private static Shop apply(Shop s, ShopRequest r) {
        s.setName(r.name().trim());
        s.setSlug(r.slug());
        s.setDescription(r.description());
        s.setPhone(r.phone());
        s.setEmail(r.email().trim().toLowerCase());
        s.setPickupAddress(r.pickupAddress().trim());
        return s;
    }

    static ErrorResponseException notFound() {
        return ApiErrors.of(HttpStatus.NOT_FOUND, "shop.not-found", "Shop not found");
    }

    private static ErrorResponseException alreadyExists() {
        return ApiErrors.of(HttpStatus.CONFLICT, "shop.already-exists", "You already have a shop");
    }

    private static ErrorResponseException slugTaken() {
        return ApiErrors.of(HttpStatus.CONFLICT, "shop.slug-taken", "Slug is already taken");
    }
}
