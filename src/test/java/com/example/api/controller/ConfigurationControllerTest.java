package com.example.api.controller;

import com.example.api.client.IpifyClient;
import com.example.api.config.AppProperties;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConfigurationController.class)
class ConfigurationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IpifyClient ipifyClient;

    @MockitoBean
    private AppProperties appProperties;

    @Test
    void getConfiguration_whenIpifySucceeds_returns200() throws Exception {
        when(appProperties.getEnv()).thenReturn("test");
        when(ipifyClient.getPublicIp(anyString())).thenReturn(new IpifyClient.IpifyResponse("203.0.113.1"));

        mockMvc.perform(get("/configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.environment").value("test"))
                .andExpect(jsonPath("$.outboundIp").value("203.0.113.1"));
    }

    @Test
    void getConfiguration_whenIpifyFails_returns502() throws Exception {
        Request request = Request.create(
                Request.HttpMethod.GET, "http://api.ipify.org", Map.of(), (byte[]) null, StandardCharsets.UTF_8);
        when(ipifyClient.getPublicIp(anyString()))
                .thenThrow(new FeignException.ServiceUnavailable("boom", request, null, Map.of()));

        mockMvc.perform(get("/configuration"))
                .andExpect(status().isBadGateway());
    }
}
