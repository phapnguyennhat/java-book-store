package com.example.book_store.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Reads the sent mail back from Mailpit's API, so docker compose must be up. */
@SpringBootTest
@AutoConfigureMockMvc
class EmailVerificationTests {
    @Autowired
    MockMvc mvc;
    @Value("${MAILPIT_UI_PORT}")
    int mailpitPort;

    @Test
    void vietnameseSignup() throws Exception {
        String html = signupAndReadMail("vi-VN,vi;q=0.9", "Xác thực email của bạn trên Thoth");
        assertTrue(html.contains("Chào Ann Lee,") && html.contains("lang=\"vi\""));
    }

    @Test
    void englishSignupAndVerify() throws Exception {
        String email = UUID.randomUUID() + "@test.com";
        String html = signupAndReadMail(email, "en", "Verify your email for Thoth");
        assertTrue(html.contains("Hi Ann Lee,") && html.contains("didn&#39;t create") && html.contains("24 hours"));

        String creds = "{\"email\":\"%s\",\"password\":\"s3cret-pass\"}".formatted(email);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(creds))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("auth.email-not-verified"));

        Matcher m = Pattern.compile("/verify-email\\?token=([0-9a-f-]+)").matcher(html);
        assertTrue(m.find());
        String body = "{\"token\":\"" + m.group(1) + "\"}";
        mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("auth.verify-token-invalid"));

        var access = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(creds))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("access_token");
        mvc.perform(get("/api/auth/me").cookie(access)).andExpect(jsonPath("$.emailVerified").value(true));
        resend(email);
        assertEquals(1, mails(email).size()); // verified: nothing sent
    }

    @Test
    void resendIsRateLimitedAndRevealsNothing() throws Exception {
        String email = UUID.randomUUID() + "@test.com";
        signupAndReadMail(email, "en", "Verify your email for Thoth");
        resend(email.toUpperCase()); // sent
        resend(email); // within a minute: skipped, same answer
        resend(UUID.randomUUID() + "@test.com"); // unknown: same answer
        assertEquals(2, mails(email).size());
    }

    private void resend(String email) throws Exception {
        mvc.perform(post("/api/auth/resend-verification").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\"}".formatted(email))).andExpect(status().isNoContent());
    }

    private String signupAndReadMail(String lang, String subject) throws Exception {
        return signupAndReadMail(UUID.randomUUID() + "@test.com", lang, subject);
    }

    private RestClient mailpit() {
        return RestClient.create("http://localhost:" + mailpitPort + "/api/v1");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mails(String to) {
        return (List<Map<String, Object>>) mailpit().get().uri("/search?query={q}", "to:" + to)
                .retrieve().body(Map.class).get("messages");
    }

    private String signupAndReadMail(String email, String lang, String subject) throws Exception {
        mvc.perform(post("/api/auth/signup").header("Accept-Language", lang).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Ann Lee","email":"%s","password":"s3cret-pass","dateOfBirth":"1990-01-01","sex":"FEMALE"}"""
                                .formatted(email)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.emailVerified").value(false));

        var found = mails(email);
        assertEquals(1, found.size());
        assertEquals(subject, found.getFirst().get("Subject"));
        return (String) mailpit().get().uri("/message/{id}", found.getFirst().get("ID")).retrieve().body(Map.class).get("HTML");
    }
}
