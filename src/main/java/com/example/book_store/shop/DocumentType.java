package com.example.book_store.shop;

import java.util.Set;

public enum DocumentType {
    ID_FRONT(Set.of("image/jpeg", "image/png")),
    ID_BACK(Set.of("image/jpeg", "image/png")),
    SELFIE(Set.of("image/jpeg", "image/png")),
    BUSINESS_LICENSE(Set.of("image/jpeg", "image/png", "application/pdf"));

    final Set<String> contentTypes;

    DocumentType(Set<String> contentTypes) {
        this.contentTypes = contentTypes;
    }
}
