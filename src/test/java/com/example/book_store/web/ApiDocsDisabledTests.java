package com.example.book_store.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Production default: without API_DOCS_ENABLED=true the API description is not served. */
@SpringBootTest(properties = "API_DOCS_ENABLED=false")
@AutoConfigureMockMvc
class ApiDocsDisabledTests {
    @Autowired
    MockMvc mvc;

    @Test
    void docsAreOffUnlessEnabled() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
    }
}
