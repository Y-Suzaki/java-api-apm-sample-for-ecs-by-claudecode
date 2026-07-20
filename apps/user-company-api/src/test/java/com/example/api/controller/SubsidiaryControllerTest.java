package com.example.api.controller;

import com.example.api.client.LogApiClient;
import com.example.api.exception.CompanyNotFoundException;
import com.example.api.exception.SubsidiaryNotFoundException;
import com.example.api.model.SubsidiaryResponse;
import com.example.api.service.SubsidiaryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SubsidiaryController.class)
class SubsidiaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubsidiaryService subsidiaryService;

    // RequestLoggingFilter は全リクエストに適用される Filter Bean のため、このスライスにも
    // 読み込まれ、その依存先が必要になる。
    @MockitoBean
    private LogApiClient logApiClient;

    @MockitoBean(name = "logApiTaskExecutor")
    private ThreadPoolTaskExecutor logApiTaskExecutor;

    @Test
    void createSubsidiary_withValidBody_returns201() throws Exception {
        when(subsidiaryService.create(eq(1L), any())).thenReturn(responseOf(10L, 1L, "Acme Sub"));

        mockMvc.perform(post("/companies/1/subsidiaries")
                        .contentType("application/json")
                        .content("""
                                {"name":"Acme Sub"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.companyId").value(1));
    }

    @Test
    void createSubsidiary_withBlankName_returns400() throws Exception {
        mockMvc.perform(post("/companies/1/subsidiaries")
                        .contentType("application/json")
                        .content("""
                                {"name":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createSubsidiary_whenCompanyNotFound_returns404() throws Exception {
        when(subsidiaryService.create(eq(99L), any())).thenThrow(new CompanyNotFoundException(99L));

        mockMvc.perform(post("/companies/99/subsidiaries")
                        .contentType("application/json")
                        .content("""
                                {"name":"Acme Sub"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void createSubsidiary_whenAlreadyExists_returns409() throws Exception {
        when(subsidiaryService.create(eq(1L), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Subsidiary already exists: Acme Sub"));

        mockMvc.perform(post("/companies/1/subsidiaries")
                        .contentType("application/json")
                        .content("""
                                {"name":"Acme Sub"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void updateSubsidiary_withValidBody_returns200() throws Exception {
        when(subsidiaryService.update(eq(1L), eq(10L), any())).thenReturn(responseOf(10L, 1L, "New Name"));

        mockMvc.perform(put("/companies/1/subsidiaries/10")
                        .contentType("application/json")
                        .content("""
                                {"name":"New Name"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"));
    }

    @Test
    void updateSubsidiary_whenNotFound_returns404() throws Exception {
        when(subsidiaryService.update(eq(1L), eq(99L), any())).thenThrow(new SubsidiaryNotFoundException(99L));

        mockMvc.perform(put("/companies/1/subsidiaries/99")
                        .contentType("application/json")
                        .content("""
                                {"name":"New Name"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteSubsidiary_whenFound_returns204() throws Exception {
        doNothing().when(subsidiaryService).delete(1L, 10L);

        mockMvc.perform(delete("/companies/1/subsidiaries/10"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteSubsidiary_whenNotFound_returns404() throws Exception {
        doThrow(new SubsidiaryNotFoundException(99L)).when(subsidiaryService).delete(1L, 99L);

        mockMvc.perform(delete("/companies/1/subsidiaries/99"))
                .andExpect(status().isNotFound());
    }

    private static SubsidiaryResponse responseOf(Long id, Long companyId, String name) {
        return new SubsidiaryResponse(
                id, companyId, name, null, null, null, null, LocalDateTime.now(), LocalDateTime.now());
    }
}
