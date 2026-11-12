package com.example.book_store.web;

import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Every message must exist in every language, otherwise that language silently falls back to English. */
class MessagesTests {

    @Test
    void allLanguagesHaveSameKeys() throws Exception {
        assertEquals(load("messages.properties").stringPropertyNames(), load("messages_vi.properties").stringPropertyNames());
    }

    private static Properties load(String name) throws Exception {
        Properties p = new Properties();
        try (var in = MessagesTests.class.getClassLoader().getResourceAsStream(name)) {
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return p;
    }
}
