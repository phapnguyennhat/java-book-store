package com.example.book_store.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ApiExceptionHandlerTests {
    @Autowired
    MockMvc mvc;

    @Test
    void validationErrorListsEachBadField() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"a@b.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid request content."))
                .andExpect(jsonPath("$.errors.password").value("must not be blank"))
                .andExpect(jsonPath("$.errors.email").doesNotExist());
    }

    @Test
    void messagesFollowAcceptLanguage() throws Exception {
        mvc.perform(post("/api/auth/signup").header(HttpHeaders.ACCEPT_LANGUAGE, "vi")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Yêu cầu không hợp lệ"))
                .andExpect(jsonPath("$.detail").value("Dữ liệu gửi lên không hợp lệ."))
                .andExpect(jsonPath("$.errors.fullName").value("không được để trống"))
                .andExpect(jsonPath("$.errors.password").value("độ dài phải từ 8 đến 128 ký tự"));

        mvc.perform(post("/api/auth/signup").header(HttpHeaders.ACCEPT_LANGUAGE, "vi")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"sex\":\"ALIEN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Không đọc được dữ liệu gửi lên: JSON sai cú pháp hoặc sai kiểu dữ liệu."));
        mvc.perform(get("/api/auth/login").header(HttpHeaders.ACCEPT_LANGUAGE, "vi"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.title").value("Phương thức không được hỗ trợ"))
                .andExpect(jsonPath("$.detail").value("Phương thức 'GET' không được hỗ trợ."));

        String badLogin ="{\"email\":\"nobody@test.com\",\"password\":\"wrong-pass\"}";
        mvc.perform(post("/api/auth/login").header(HttpHeaders.ACCEPT_LANGUAGE, "vi-VN,vi;q=0.9")
                        .contentType(MediaType.APPLICATION_JSON).content(badLogin))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Email hoặc mật khẩu không đúng"));
        // unsupported language falls back to English
        mvc.perform(post("/api/auth/login").header(HttpHeaders.ACCEPT_LANGUAGE, "fr")
                        .contentType(MediaType.APPLICATION_JSON).content(badLogin))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Invalid credentials"));
    }
}
