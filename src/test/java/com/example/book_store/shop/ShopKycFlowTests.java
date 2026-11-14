package com.example.book_store.shop;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Needs LocalStack (docker compose) for the presigned S3 uploads. */
@SpringBootTest
@AutoConfigureMockMvc
class ShopKycFlowTests {
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};
    private static final String APPROVE = "{\"decision\":\"APPROVED\",\"reason\":\"ok\"}";
    private static final String STEP_2 = """
            {"sellerType":"INDIVIDUAL","legalName":"Nguyễn Văn An","idNumber":"001090012345","idIssueDate":"2021-05-01",
             "idIssuePlace":"Cục CSQLHC về TTXH","dateOfBirth":"1990-01-01","permanentAddress":"Hà Nội"}""";
    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void individualSellerIsReviewedByAdmin() throws Exception {
        Cookie seller = login(signup());
        String slug = "shop-" + UUID.randomUUID().toString().substring(0, 8);

        mvc.perform(get("/api/my-shop").cookie(seller)).andExpect(status().isNotFound());
        json(post("/api/my-shop/kyc/upload-urls"), seller, upload("ID_FRONT", "image/jpeg")).andExpect(status().isNotFound());

        String shopId = JsonPath.read(json(post("/api/my-shop"), seller, shop(slug)).andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/my-shop"))
                .andExpect(jsonPath("$.kycStatus").value("NOT_SUBMITTED"))
                .andReturn().getResponse().getContentAsString(), "$.id").toString();
        json(post("/api/my-shop"), seller, shop("other-" + slug)).andExpect(status().isConflict());
        json(post("/api/my-shop"), login(signup()), shop(slug)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This slug is already taken"))
                .andExpect(jsonPath("$.code").value("shop.slug-taken"));
        mvc.perform(get("/api/shops/" + slug)).andExpect(status().isNotFound()); // not approved yet

        // uploads: type/content-type pairing is enforced, and the signed size must match what is sent
        json(post("/api/my-shop/kyc/upload-urls"), seller, upload("ID_FRONT", "application/pdf"))
                .andExpect(status().isBadRequest());
        String front = uploadFile(seller, "ID_FRONT");
        String back = uploadFile(seller, "ID_BACK");
        String selfie = uploadFile(seller, "SELFIE");
        Map<String, Object> url = JsonPath.read(json(post("/api/my-shop/kyc/upload-urls"), seller,
                upload("SELFIE", "image/jpeg")).andReturn().getResponse().getContentAsString(), "$");
        assertEquals(403, s3Put((String) url.get("uploadUrl"), "image/jpeg", new byte[JPEG.length + 1]));

        // wizard: each step saves the whole draft so far; missing fields are fine until submission
        String kycUrl = "/api/my-shop/kyc", submit = "/api/my-shop/kyc/submission";
        json(put(kycUrl), seller, STEP_2).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT")).andExpect(jsonPath("$.documents").isEmpty());
        mvc.perform(get("/api/my-shop").cookie(seller)).andExpect(jsonPath("$.kycStatus").value("DRAFT"));
        mvc.perform(post(submit).cookie(seller)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.idFrontKey").value("must not be blank"))
                .andExpect(jsonPath("$.errors.bank").exists());
        json(put(kycUrl), seller, STEP_2.replace("001090012345", "123")) // present fields must still be well-formed
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.idNumber").exists());
        String foreign = front.replaceFirst("kyc/\\d+/", "kyc/999999999/");
        json(put(kycUrl), seller, kyc("INDIVIDUAL", foreign, back, selfie, "NGUYEN VAN AN", null))
                .andExpect(status().isBadRequest());

        // the payout holder is only checked on submission: steps can be edited in any order
        json(put(kycUrl), seller, kyc("INDIVIDUAL", front, back, selfie, "TRAN THI B", null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.documents.SELFIE").exists());
        mvc.perform(post(submit).cookie(seller)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Bank account holder must match the legal name or business name"));
        // back to step 2 (address) and step 4 (holder), then submit
        json(put(kycUrl), seller, kyc("INDIVIDUAL", front, back, selfie, "NGUYEN VAN AN", null).replace("Hà Nội", "Đà Nẵng"))
                .andExpect(jsonPath("$.permanentAddress").value("Đà Nẵng")).andExpect(jsonPath("$.status").value("DRAFT"));
        mvc.perform(post(submit).cookie(seller)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.submittedAt").exists());
        json(put(kycUrl), seller, STEP_2).andExpect(status().isConflict()); // no edits while waiting for review
        mvc.perform(post(submit).cookie(seller)).andExpect(status().isConflict());

        // admin area is ADMIN only
        mvc.perform(get("/api/admin/kyc").cookie(seller)).andExpect(status().isForbidden());
        Cookie admin = admin();
        mvc.perform(get("/api/admin/kyc?size=2000&sort=nope").cookie(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(100)) // capped
                .andExpect(jsonPath("$.content[*].status", everyItem(is("PENDING"))));
        mvc.perform(get("/api/admin/kyc/" + shopId).cookie(admin)).andExpect(jsonPath("$.legalName").value("Nguyễn Văn An"));
        String docUrl = JsonPath.read(mvc.perform(get("/api/admin/kyc/" + shopId).cookie(admin)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.documents.ID_FRONT");
        assertArrayEquals(JPEG, http.send(HttpRequest.newBuilder(URI.create(docUrl)).build(),
                HttpResponse.BodyHandlers.ofByteArray()).body());

        // reject -> seller sees why -> resubmit -> approve -> public
        String review = "/api/admin/kyc/" + shopId + "/review";
        json(post(review), admin, "{\"decision\":\"REJECTED\",\"reason\":\" \"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.reason").exists());
        json(post(review), admin, "{\"decision\":\"PENDING\"}").andExpect(status().isBadRequest());
        json(post(review), admin, "{\"decision\":\"REJECTED\",\"reason\":\"Ảnh CCCD bị mờ\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        json(post(review), admin, APPROVE).andExpect(status().isConflict());
        mvc.perform(get("/api/my-shop").cookie(seller))
                .andExpect(jsonPath("$.kycStatus").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Ảnh CCCD bị mờ"));
        // editing a rejected KYC reopens it as a draft; the reason stays visible until it is resubmitted
        json(put(kycUrl), seller, kyc("INDIVIDUAL", front, back, selfie, "NGUYEN VAN AN", null))
                .andExpect(jsonPath("$.status").value("DRAFT")).andExpect(jsonPath("$.rejectionReason").value("Ảnh CCCD bị mờ"));
        mvc.perform(post(submit).cookie(seller))
                .andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.rejectionReason").doesNotExist());
        json(post(review), admin, APPROVE) // its "ok" reason is dropped: only rejections keep one
                .andExpect(status().isOk()).andExpect(jsonPath("$.rejectionReason").doesNotExist()).andExpect(jsonPath("$.status").value("APPROVED"));
        json(put(kycUrl), seller, STEP_2).andExpect(status().isConflict()); // verified data is frozen
        mvc.perform(get("/api/shops/" + slug)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nhà sách An"))
                .andExpect(jsonPath("$.pickupAddress").doesNotExist());
    }

    @Test
    void companyNeedsBusinessInfoAndPaysOutToBusinessAccount() throws Exception {
        Cookie seller = login(signup());
        json(post("/api/my-shop"), seller, shop("co-" + UUID.randomUUID().toString().substring(0, 8)))
                .andExpect(status().isCreated());
        String front = uploadFile(seller, "ID_FRONT"), back = uploadFile(seller, "ID_BACK"), selfie = uploadFile(seller, "SELFIE");
        String license = uploadFile(seller, "BUSINESS_LICENSE");

        json(put("/api/my-shop/kyc"), seller, kyc("COMPANY", front, back, selfie, "CONG TY TNHH SACH AN", null))
                .andExpect(status().isOk());
        mvc.perform(post("/api/my-shop/kyc/submission").cookie(seller))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.business").exists());
        // switching to INDIVIDUAL in the wizard: stale business info is dropped, not an error
        json(put("/api/my-shop/kyc"), seller, kyc("INDIVIDUAL", front, back, selfie, "NGUYEN VAN AN", business(license)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.business").doesNotExist())
                .andExpect(jsonPath("$.documents.BUSINESS_LICENSE").doesNotExist());
        String kyc = kyc("COMPANY", front, back, selfie, "CONG TY TNHH SACH AN", business(license));
        json(put("/api/my-shop/kyc").header("Accept-Language", "vi"), seller, kyc.replace("0123456789", "12ab"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['bank.accountNumber']").value("phải gồm 6 đến 20 chữ số"));
        json(put("/api/my-shop/kyc"), seller, kyc).andExpect(status().isOk());
        mvc.perform(post("/api/my-shop/kyc/submission").cookie(seller)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.business.taxCode").value("0101234567"))
                .andExpect(jsonPath("$.documents.BUSINESS_LICENSE").exists());
    }

    // --- helpers ---

    private String uploadFile(Cookie seller, String type) throws Exception {
        Map<String, Object> r = JsonPath.read(json(post("/api/my-shop/kyc/upload-urls"), seller, upload(type, "image/jpeg"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$");
        assertEquals(200, s3Put((String) r.get("uploadUrl"), "image/jpeg", JPEG));
        return (String) r.get("key");
    }

    private int s3Put(String url, String contentType, byte[] body) throws Exception {
        // HttpClient sets Content-Length from the body; a different length than signed must be refused
        return http.send(HttpRequest.newBuilder(URI.create(url)).header("Content-Type", contentType)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private ResultActions json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req, Cookie c,
                               String body) throws Exception {
        return mvc.perform(req.cookie(c).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String upload(String type, String contentType) {
        return "{\"type\":\"%s\",\"contentType\":\"%s\",\"size\":%d}".formatted(type, contentType, JPEG.length);
    }

    private static String shop(String slug) {
        return """
                {"name":"Nhà sách An","slug":"%s","description":"Sách cũ","phone":"0901234567",
                 "email":"shop@test.com","pickupAddress":"1 Lê Lợi, Q1, TP.HCM"}""".formatted(slug);
    }

    private static String business(String licenseKey) {
        return """
                {"businessName":"Công ty TNHH Sách An","registrationNumber":"0101234567","registrationDate":"2020-01-01",
                 "taxCode":"0101234567","registeredAddress":"1 Lê Lợi, Q1, TP.HCM","licenseKey":"%s"}""".formatted(licenseKey);
    }

    private static String kyc(String type, String front, String back, String selfie, String holder, String business) {
        return """
                {"sellerType":"%s","legalName":"Nguyễn Văn An","idNumber":"001090012345","idIssueDate":"2021-05-01",
                 "idIssuePlace":"Cục CSQLHC về TTXH","dateOfBirth":"1990-01-01","permanentAddress":"Hà Nội",
                 "idFrontKey":"%s","idBackKey":"%s","selfieKey":"%s","business":%s,
                 "bank":{"bankCode":"VCB","accountNumber":"0123456789","accountHolder":"%s"}}"""
                .formatted(type, front, back, selfie, business, holder);
    }

    private String signup() throws Exception {
        String email = UUID.randomUUID() + "@test.com";
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"Seller","email":"%s","password":"s3cret-pass","dateOfBirth":"1990-01-01","sex":"MALE"}"""
                .formatted(email))).andExpect(status().isCreated());
        jdbc.update("UPDATE users SET email_verified_at = now() WHERE email = ?", email); // login requires it
        return email;
    }

    private Cookie login(String email) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"s3cret-pass\"}".formatted(email)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("access_token");
    }

    private Cookie admin() throws Exception {
        String email = signup();
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = ?", email);
        return login(email); // role is read into the token at login
    }
}
