package com.example.api.controller;

import com.example.api.exception.UserAlreadyExistsException;
import com.example.api.exception.UserNotFoundException;
import com.example.api.model.UserResponse;
import com.example.api.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @Test
    void createUser_withValidBody_returns201() throws Exception {
        UserResponse response = new UserResponse("taro@example.com", "Taro", Instant.now(), Instant.now());
        when(userService.create(any())).thenReturn(response);

        mockMvc.perform(post("/users")
                        .contentType("application/json")
                        .content("""
                                {"email":"taro@example.com","name":"Taro"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("taro@example.com"))
                .andExpect(jsonPath("$.name").value("Taro"));
    }

    @Test
    void createUser_withInvalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType("application/json")
                        .content("""
                                {"email":"not-an-email","name":"Taro"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void createUser_whenAlreadyExists_returns409() throws Exception {
        when(userService.create(any())).thenThrow(new UserAlreadyExistsException("taro@example.com"));

        mockMvc.perform(post("/users")
                        .contentType("application/json")
                        .content("""
                                {"email":"taro@example.com","name":"Taro"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("User already exists: taro@example.com"));
    }

    @Test
    void listUsers_withDefaultLimit_returns200() throws Exception {
        UserResponse response = new UserResponse("taro@example.com", "Taro", Instant.now(), Instant.now());
        when(userService.list(50)).thenReturn(List.of(response));

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("taro@example.com"));
    }

    @Test
    void listUsers_withLimitBelowMinimum_returns400() throws Exception {
        mockMvc.perform(get("/users").param("limit", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listUsers_withLimitAboveMaximum_returns400() throws Exception {
        mockMvc.perform(get("/users").param("limit", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUser_whenFound_returns200() throws Exception {
        UserResponse response = new UserResponse("taro@example.com", "Taro", Instant.now(), Instant.now());
        when(userService.get("taro@example.com")).thenReturn(response);

        mockMvc.perform(get("/users/taro@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Taro"));
    }

    @Test
    void getUser_whenNotFound_returns404() throws Exception {
        when(userService.get("missing@example.com")).thenThrow(new UserNotFoundException("missing@example.com"));

        mockMvc.perform(get("/users/missing@example.com"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("User not found: missing@example.com"));
    }

    @Test
    void updateUser_withValidBody_returns200() throws Exception {
        UserResponse response = new UserResponse("taro@example.com", "Jiro", Instant.now(), Instant.now());
        when(userService.update(eq("taro@example.com"), any())).thenReturn(response);

        mockMvc.perform(put("/users/taro@example.com")
                        .contentType("application/json")
                        .content("""
                                {"name":"Jiro"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Jiro"));
    }

    @Test
    void updateUser_withBlankName_returns400() throws Exception {
        mockMvc.perform(put("/users/taro@example.com")
                        .contentType("application/json")
                        .content("""
                                {"name":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateUser_whenNotFound_returns404() throws Exception {
        when(userService.update(eq("missing@example.com"), any()))
                .thenThrow(new UserNotFoundException("missing@example.com"));

        mockMvc.perform(put("/users/missing@example.com")
                        .contentType("application/json")
                        .content("""
                                {"name":"Jiro"}
                                """))
                .andExpect(status().isNotFound());
    }
}