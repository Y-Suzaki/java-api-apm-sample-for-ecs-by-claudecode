package com.example.logapi.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LogController.class)
class LogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void receiveLog_withJsonObjectBody_returnsAccepted() throws Exception {
        mockMvc.perform(post("/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"level\":\"INFO\",\"message\":\"hello\"}"))
                .andExpect(status().isAccepted());
    }

    @Test
    void receiveLog_withJsonArrayBody_returnsAccepted() throws Exception {
        mockMvc.perform(post("/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"a\",\"b\"]"))
                .andExpect(status().isAccepted());
    }

    @Test
    void receiveLog_withPlainTextContentType_returnsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/logs")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hello world"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void receiveLog_withMalformedJson_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void receiveLog_withEmptyBody_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/logs")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}