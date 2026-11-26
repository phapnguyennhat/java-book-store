package com.example.book_store.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
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
class PasswordResetTests {
    static final String SUBJECT = "Reset your Thoth password";

    @Autowired
    MockMvc mvc;
    @Value("${MAILPIT_UI_PORT}")
    int mailpitPort;

    @Test
    void resetChangesPasswordOnceAndSignsOutEverywhere() throws Exception {
        String email = UUID.randomUUID() + "@test.com";
        signup(email);
        forgot(email.toUpperCase());
        forgot(email); // within a minute: skipped, same answer
        forgot(UUID.randomUUID() + "@test.com"); // unknown: same answer

        List<Map<String, Object>> resets = mails(email).stream().filter(m -> SUBJECT.equals(m.get("Subject"))).toList();
        assertEquals(1, resets.size());
        String html = (String) mailpit().get().uri("/message/{id}", resets.getFirst().get("ID")).retrieve().body(Map.class).get("HTML");
        assertTrue(html.contains("Hi Ann Lee,") && html.contains("30 minutes"));
        Matcher m = Pattern.compile("/reset-password\\?token=([0-9a-f-]+)").matcher(html);
        assertTrue(m.find());

        String body = "{\"token\":\"%s\",\"password\":\"n3w-secret-pass\"}".formatted(m.group(1));
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("auth.reset-token-invalid"));

        login(email, "s3cret-pass").andExpect(status().isUnauthorized());
        Thread.sleep(1100); // the reset's logout-all also revokes tokens minted in the same second (RevocationValidator)
        // reset also verified the email, which was never confirmed after signup
        var access = login(email, "n3w-secret-pass").andExpect(status().isOk()).andReturn().getResponse().getCookie("access_token");
        mvc.perform(get("/api/auth/me").cookie(access)).andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    void shortPasswordIsRejected() throws Exception {
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"x\",\"password\":\"short\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"));
    }

    private void signup(String email) throws Exception {
        mvc.perform(post("/api/auth/signup").header("Accept-Language", "en").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"Ann Lee","email":"%s","password":"s3cret-pass","dateOfBirth":"1990-01-01","sex":"FEMALE"}"""
                        .formatted(email))).andExpect(status().isCreated());
    }

    private void forgot(String email) throws Exception {
        mvc.perform(post("/api/auth/forgot-password").header("Accept-Language", "en").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\"}".formatted(email))).andExpect(status().isNoContent());
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private RestClient mailpit() {
        return RestClient.create("http://localhost:" + mailpitPort + "/api/v1");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mails(String to) {
        return (List<Map<String, Object>>) mailpit().get().uri("/search?query={q}", "to:" + to)
                .retrieve().body(Map.class).get("messages");
    }
}
