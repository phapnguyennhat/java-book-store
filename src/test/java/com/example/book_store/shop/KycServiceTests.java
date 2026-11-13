package com.example.book_store.shop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KycServiceTests {

    @Test
    void normalizeNameMatchesBankStyle() {
        assertEquals("NGUYEN VAN DUC", KycService.normalizeName("  Nguyễn  Văn Đức "));
        assertEquals("TRAN THI HOA", KycService.normalizeName("trần thị hoà"));
        assertEquals("CONG TY TNHH SACH AN", KycService.normalizeName("Công ty TNHH Sách An"));
    }
}
