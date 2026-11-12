package com.example.book_store.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTests {
    @Autowired
    MockMvc mvc;

    @Test
    void signupLoginMeRefreshRevoke() throws Exception {
        String email = UUID.randomUUID() + "@test.com";
        String signup = """
                {"fullName":"Ann Lee","email":"%s","password":"s3cret-pass","dateOfBirth":"1990-01-01","sex":"FEMALE"}""".formatted(email);
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(signup))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(signup))
                .andExpect(status().isConflict());

        String creds = "{\"email\":\"%s\",\"password\":\"%s\"}";
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(creds.formatted(email, "wrong-pass")))
                .andExpect(status().isUnauthorized());
        MvcResult login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(creds.formatted(email, "s3cret-pass"))).andExpect(status().isOk()).andReturn();
        Cookie access = login.getResponse().getCookie("access_token");
        Cookie refresh = login.getResponse().getCookie("refresh_token");
        assertTrue(access.isHttpOnly() && refresh.isHttpOnly());

        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").cookie(access)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));

        // alg=none token must be rejected (algorithm pinning)
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String none = b64.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + b64.encodeToString("{\"sub\":\"1\",\"iss\":\"book-store\",\"exp\":9999999999}".getBytes(StandardCharsets.UTF_8)) + ".";
        mvc.perform(get("/api/auth/me").cookie(new Cookie("access_token", none))).andExpect(status().isUnauthorized());

        // a refresh token is not an access token and vice versa
        mvc.perform(get("/api/auth/me").cookie(new Cookie("access_token", refresh.getValue()))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", access.getValue()))).andExpect(status().isUnauthorized());

        // one-time use: rotation issues a new pair and the old refresh token is dead; a bad access cookie doesn't matter here
        MvcResult rotated = mvc.perform(post("/api/auth/refresh").cookie(refresh, new Cookie("access_token", "garbage")))
                .andExpect(status().isOk()).andReturn();
        mvc.perform(post("/api/auth/refresh").cookie(refresh)).andExpect(status().isUnauthorized());
        Cookie access2 = rotated.getResponse().getCookie("access_token");
        Cookie refresh2 = rotated.getResponse().getCookie("refresh_token");

        // logout revokes both tokens (access by jti, refresh by deleting its key)
        mvc.perform(post("/api/auth/logout").cookie(access2, refresh2)).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").cookie(access2)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(refresh2)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout").cookie(new Cookie("access_token", "garbage"))).andExpect(status().isNoContent());

        // logout-all kills every token issued before it, on both kinds
        MvcResult l = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(creds.formatted(email, "s3cret-pass"))).andReturn();
        Cookie a3 = l.getResponse().getCookie("access_token"), r3 = l.getResponse().getCookie("refresh_token");
        mvc.perform(post("/api/auth/logout-all").cookie(a3)).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").cookie(a3)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(r3)).andExpect(status().isUnauthorized());
        Thread.sleep(1100); // the cutoff has 1s resolution; tokens from the same second count as revoked
        Cookie a4 = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(creds.formatted(email, "s3cret-pass"))).andReturn().getResponse().getCookie("access_token");
        mvc.perform(get("/api/auth/me").cookie(a4)).andExpect(status().isOk());
    }
}
