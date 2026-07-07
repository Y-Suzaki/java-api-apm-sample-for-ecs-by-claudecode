package com.example.api.controller;

import com.example.api.exception.CompanyAlreadyExistsException;
import com.example.api.exception.CompanyNotFoundException;
import com.example.api.model.CompanyDetailResponse;
import com.example.api.model.CompanyResponse;
import com.example.api.service.CompanyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CompanyController.class)
class CompanyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CompanyService companyService;

    @Test
    void createCompany_withValidBody_returns201() throws Exception {
        CompanyResponse response = responseOf(1L, "Acme");
        when(companyService.create(any())).thenReturn(response);

        mockMvc.perform(post("/companies")
                        .contentType("application/json")
                        .content("""
                                {"name":"Acme"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Acme"));
    }

    @Test
    void createCompany_withBlankName_returns400() throws Exception {
        mockMvc.perform(post("/companies")
                        .contentType("application/json")
                        .content("""
                                {"name":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCompany_whenAlreadyExists_returns409() throws Exception {
        when(companyService.create(any())).thenThrow(new CompanyAlreadyExistsException("Acme"));

        mockMvc.perform(post("/companies")
                        .contentType("application/json")
                        .content("""
                                {"name":"Acme"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Company already exists: Acme"));
    }

    @Test
    void listCompanies_returns200() throws Exception {
        when(companyService.list(50)).thenReturn(List.of(responseOf(1L, "Acme")));

        mockMvc.perform(get("/companies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Acme"));
    }

    @Test
    void listCompanies_withLimitAboveMaximum_returns400() throws Exception {
        mockMvc.perform(get("/companies").param("limit", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getCompany_whenFound_returns200WithSubsidiaries() throws Exception {
        CompanyDetailResponse response = new CompanyDetailResponse(
                1L, "Acme", null, null, null, null, LocalDateTime.now(), LocalDateTime.now(), List.of());
        when(companyService.get(1L)).thenReturn(response);

        mockMvc.perform(get("/companies/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.subsidiaries").isArray());
    }

    @Test
    void getCompany_whenNotFound_returns404() throws Exception {
        when(companyService.get(99L)).thenThrow(new CompanyNotFoundException(99L));

        mockMvc.perform(get("/companies/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Company not found: 99"));
    }

    @Test
    void updateCompany_withValidBody_returns200() throws Exception {
        when(companyService.update(eq(1L), any())).thenReturn(responseOf(1L, "New Acme"));

        mockMvc.perform(put("/companies/1")
                        .contentType("application/json")
                        .content("""
                                {"name":"New Acme"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Acme"));
    }

    @Test
    void updateCompany_whenNotFound_returns404() throws Exception {
        when(companyService.update(eq(99L), any())).thenThrow(new CompanyNotFoundException(99L));

        mockMvc.perform(put("/companies/99")
                        .contentType("application/json")
                        .content("""
                                {"name":"New Acme"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteCompany_whenFound_returns204() throws Exception {
        doNothing().when(companyService).delete(1L);

        mockMvc.perform(delete("/companies/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteCompany_whenNotFound_returns404() throws Exception {
        doThrow(new CompanyNotFoundException(99L)).when(companyService).delete(99L);

        mockMvc.perform(delete("/companies/99"))
                .andExpect(status().isNotFound());
    }

    private static CompanyResponse responseOf(Long id, String name) {
        return new CompanyResponse(id, name, null, null, null, null, LocalDateTime.now(), LocalDateTime.now());
    }
}