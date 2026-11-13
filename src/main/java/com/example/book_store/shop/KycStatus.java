package com.example.book_store.shop;

public enum KycStatus {
    NOT_SUBMITTED, // response only: no shop_kycs row yet
    DRAFT, // being filled in by the seller, not visible for review
    PENDING, APPROVED, REJECTED
}
