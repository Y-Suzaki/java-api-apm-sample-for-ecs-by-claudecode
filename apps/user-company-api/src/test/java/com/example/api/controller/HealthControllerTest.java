package com.example.api.controller;

import com.example.api.client.LogApiClient;
import com.example.api.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AppProperties appProperties;

    // RequestLoggingFilter は全リクエストに適用される Filter Bean のため、
    // @WebMvcTest(HealthController.class) のスライスにも読み込まれ、その依存先が必要になる。
    @MockitoBean
    private LogApiClient logApiClient;

    @MockitoBean(name = "logApiTaskExecutor")
    private ThreadPoolTaskExecutor logApiTaskExecutor;

    @Test
    void health_returnsOkStatusWithEnv() throws Exception {
        when(appProperties.getEnv()).thenReturn("test");

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.env").value("test"));
    }
}
