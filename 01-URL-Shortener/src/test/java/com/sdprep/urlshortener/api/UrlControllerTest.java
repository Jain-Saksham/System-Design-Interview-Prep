package com.sdprep.urlshortener.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end through HTTP: JSON in, status codes and redirects out. */
@SpringBootTest
@AutoConfigureMockMvc
class UrlControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void createThenRedirectThenReadClickCount() throws Exception {
        mvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"longUrl": "https://example.com/docs", "customAlias": "docs"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost:8080/docs"))
                .andExpect(jsonPath("$.code").value("docs"));

        mvc.perform(get("/docs"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/docs"));

        mvc.perform(get("/api/urls/docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clicks").value(1));
    }

    @Test
    void errorsMapToHttpStatusCodes() throws Exception {
        mvc.perform(get("/does-not-exist")).andExpect(status().isNotFound());

        mvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longUrl\": \"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest());
    }
}
