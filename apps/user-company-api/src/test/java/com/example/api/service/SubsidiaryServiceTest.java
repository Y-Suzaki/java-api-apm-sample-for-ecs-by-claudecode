package com.example.api.service;

import com.example.api.exception.CompanyNotFoundException;
import com.example.api.exception.SubsidiaryNotFoundException;
import com.example.api.model.CompanyEntity;
import com.example.api.model.SubsidiaryCreateRequest;
import com.example.api.model.SubsidiaryEntity;
import com.example.api.model.SubsidiaryResponse;
import com.example.api.model.SubsidiaryUpdateRequest;
import com.example.api.repository.CompanyRepository;
import com.example.api.repository.SubsidiaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.CONFLICT;

@ExtendWith(MockitoExtension.class)
class SubsidiaryServiceTest {

    @Mock
    private SubsidiaryRepository subsidiaryRepository;

    @Mock
    private CompanyRepository companyRepository;

    private SubsidiaryService subsidiaryService;

    @BeforeEach
    void setUp() {
        subsidiaryService = new SubsidiaryService(subsidiaryRepository, companyRepository);
    }

    @Test
    void create_whenCompanyExistsAndNameNotDuplicate_savesAndReturnsResponse() {
        CompanyEntity company = companyOf(1L, "Acme");
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        when(subsidiaryRepository.existsByCompanyIdAndName(1L, "Acme Sub")).thenReturn(false);
        when(subsidiaryRepository.save(any(SubsidiaryEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SubsidiaryCreateRequest request = new SubsidiaryCreateRequest(
                "Acme Sub", "IT", "sub@example.com", "03-0000-0000", "Osaka");
        SubsidiaryResponse response = subsidiaryService.create(1L, request);

        assertThat(response.companyId()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Acme Sub");
    }

    @Test
    void create_whenCompanyNotFound_throwsCompanyNotFoundException() {
        when(companyRepository.findById(99L)).thenReturn(Optional.empty());

        SubsidiaryCreateRequest request = new SubsidiaryCreateRequest("Sub", null, null, null, null);

        assertThatThrownBy(() -> subsidiaryService.create(99L, request))
                .isInstanceOf(CompanyNotFoundException.class);
    }

    @Test
    void create_whenNameDuplicate_throwsConflictResponseStatusException() {
        CompanyEntity company = companyOf(1L, "Acme");
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        when(subsidiaryRepository.existsByCompanyIdAndName(1L, "Acme Sub")).thenReturn(true);

        SubsidiaryCreateRequest request = new SubsidiaryCreateRequest("Acme Sub", null, null, null, null);

        assertThatThrownBy(() -> subsidiaryService.create(1L, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(CONFLICT));
    }

    @Test
    void update_appliesOnlyNonNullFields() {
        CompanyEntity company = companyOf(1L, "Acme");
        SubsidiaryEntity entity = subsidiaryOf(10L, company, "Acme Sub");
        entity.setIndustry("IT");
        when(companyRepository.existsById(1L)).thenReturn(true);
        when(subsidiaryRepository.findByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(entity));
        when(subsidiaryRepository.save(any(SubsidiaryEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SubsidiaryUpdateRequest request = new SubsidiaryUpdateRequest(null, "Finance", null, null, null);
        SubsidiaryResponse response = subsidiaryService.update(1L, 10L, request);

        assertThat(response.name()).isEqualTo("Acme Sub");
        assertThat(response.industry()).isEqualTo("Finance");
    }

    @Test
    void update_whenCompanyNotFound_throwsCompanyNotFoundException() {
        when(companyRepository.existsById(99L)).thenReturn(false);

        SubsidiaryUpdateRequest request = new SubsidiaryUpdateRequest("X", null, null, null, null);

        assertThatThrownBy(() -> subsidiaryService.update(99L, 10L, request))
                .isInstanceOf(CompanyNotFoundException.class);
    }

    @Test
    void update_whenSubsidiaryNotFound_throwsSubsidiaryNotFoundException() {
        when(companyRepository.existsById(1L)).thenReturn(true);
        when(subsidiaryRepository.findByIdAndCompanyId(99L, 1L)).thenReturn(Optional.empty());

        SubsidiaryUpdateRequest request = new SubsidiaryUpdateRequest("X", null, null, null, null);

        assertThatThrownBy(() -> subsidiaryService.update(1L, 99L, request))
                .isInstanceOf(SubsidiaryNotFoundException.class);
    }

    @Test
    void update_whenNameChangedAndDuplicate_throwsConflictResponseStatusException() {
        CompanyEntity company = companyOf(1L, "Acme");
        SubsidiaryEntity entity = subsidiaryOf(10L, company, "Acme Sub");
        when(companyRepository.existsById(1L)).thenReturn(true);
        when(subsidiaryRepository.findByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(entity));
        when(subsidiaryRepository.existsByCompanyIdAndNameAndIdNot(1L, "Other Sub", 10L)).thenReturn(true);

        SubsidiaryUpdateRequest request = new SubsidiaryUpdateRequest("Other Sub", null, null, null, null);

        assertThatThrownBy(() -> subsidiaryService.update(1L, 10L, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(CONFLICT));
    }

    @Test
    void delete_whenFound_deletesEntity() {
        CompanyEntity company = companyOf(1L, "Acme");
        SubsidiaryEntity entity = subsidiaryOf(10L, company, "Acme Sub");
        when(companyRepository.existsById(1L)).thenReturn(true);
        when(subsidiaryRepository.findByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(entity));

        subsidiaryService.delete(1L, 10L);

        verify(subsidiaryRepository).delete(entity);
    }

    @Test
    void delete_whenCompanyNotFound_throwsCompanyNotFoundExceptionAndDoesNotDelete() {
        when(companyRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> subsidiaryService.delete(99L, 10L))
                .isInstanceOf(CompanyNotFoundException.class);
        verify(subsidiaryRepository, never()).delete(any());
    }

    @Test
    void delete_whenSubsidiaryNotFound_throwsSubsidiaryNotFoundException() {
        when(companyRepository.existsById(1L)).thenReturn(true);
        when(subsidiaryRepository.findByIdAndCompanyId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subsidiaryService.delete(1L, 99L))
                .isInstanceOf(SubsidiaryNotFoundException.class);
    }

    private static CompanyEntity companyOf(Long id, String name) {
        CompanyEntity company = new CompanyEntity();
        company.setId(id);
        company.setName(name);
        return company;
    }

    private static SubsidiaryEntity subsidiaryOf(Long id, CompanyEntity company, String name) {
        SubsidiaryEntity entity = new SubsidiaryEntity();
        entity.setId(id);
        entity.setCompany(company);
        entity.setName(name);
        return entity;
    }
}