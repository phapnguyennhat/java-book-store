package com.example.book_store.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiConfigTests {
    @Autowired
    MockMvc mvc;

    @Test
    void everyOperationHasLanguageHeader() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.parameters[?(@.name=='Accept-Language')].in").value("header"))
                .andExpect(jsonPath("$.paths['/api/auth/me'].get.parameters[?(@.name=='Accept-Language')].schema.enum[*]")
                        .value(hasItems("en", "vi")))
                // cookies are sent by the browser, not typed in: no cookie inputs in the docs
                .andExpect(jsonPath("$.paths['/api/auth/refresh'].post.parameters[?(@.in=='cookie')]").isEmpty())
                .andExpect(jsonPath("$.paths['/api/auth/logout'].post.parameters[?(@.in=='cookie')]").isEmpty());
    }
}
