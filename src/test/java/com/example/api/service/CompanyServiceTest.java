package com.example.api.service;

import com.example.api.exception.CompanyAlreadyExistsException;
import com.example.api.exception.CompanyNotFoundException;
import com.example.api.model.CompanyCreateRequest;
import com.example.api.model.CompanyDetailResponse;
import com.example.api.model.CompanyEntity;
import com.example.api.model.CompanyResponse;
import com.example.api.model.CompanyUpdateRequest;
import com.example.api.repository.CompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    @Mock
    private CompanyRepository companyRepository;

    private CompanyService companyService;

    @BeforeEach
    void setUp() {
        companyService = new CompanyService(companyRepository);
    }

    @Test
    void create_whenNameNotDuplicate_savesAndReturnsResponse() {
        CompanyCreateRequest request = new CompanyCreateRequest(
                "Acme", "IT", "acme@example.com", "03-1234-5678", "Tokyo");
        when(companyRepository.existsByName("Acme")).thenReturn(false);
        when(companyRepository.save(any(CompanyEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CompanyResponse response = companyService.create(request);

        assertThat(response.name()).isEqualTo("Acme");
        assertThat(response.industry()).isEqualTo("IT");
        assertThat(response.email()).isEqualTo("acme@example.com");
    }

    @Test
    void create_whenNameDuplicate_throwsCompanyAlreadyExistsException() {
        CompanyCreateRequest request = new CompanyCreateRequest(
                "Acme", null, null, null, null);
        when(companyRepository.existsByName("Acme")).thenReturn(true);

        assertThatThrownBy(() -> companyService.create(request))
                .isInstanceOf(CompanyAlreadyExistsException.class)
                .hasMessageContaining("Acme");
    }

    @Test
    void list_returnsMappedResponses() {
        CompanyEntity entity = entityOf(1L, "Acme");
        when(companyRepository.findAllBy(PageRequest.of(0, 10))).thenReturn(List.of(entity));

        List<CompanyResponse> result = companyService.list(10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(result.get(0).name()).isEqualTo("Acme");
    }

    @Test
    void get_whenFound_returnsDetailResponseWithSubsidiaries() {
        CompanyEntity entity = entityOf(1L, "Acme");
        when(companyRepository.findByIdWithSubsidiaries(1L)).thenReturn(Optional.of(entity));

        CompanyDetailResponse response = companyService.get(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.subsidiaries()).isEmpty();
    }

    @Test
    void get_whenNotFound_throwsCompanyNotFoundException() {
        when(companyRepository.findByIdWithSubsidiaries(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> companyService.get(99L))
                .isInstanceOf(CompanyNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void update_appliesOnlyNonNullFields() {
        CompanyEntity entity = entityOf(1L, "Acme");
        entity.setIndustry("IT");
        entity.setEmail("old@example.com");
        when(companyRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(companyRepository.save(any(CompanyEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CompanyUpdateRequest request = new CompanyUpdateRequest(null, null, "new@example.com", null, null);
        CompanyResponse response = companyService.update(1L, request);

        assertThat(response.name()).isEqualTo("Acme");
        assertThat(response.industry()).isEqualTo("IT");
        assertThat(response.email()).isEqualTo("new@example.com");
    }

    @Test
    void update_whenNameChangedAndDuplicate_throwsCompanyAlreadyExistsException() {
        CompanyEntity entity = entityOf(1L, "Acme");
        when(companyRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(companyRepository.existsByNameAndIdNot("Globex", 1L)).thenReturn(true);

        CompanyUpdateRequest request = new CompanyUpdateRequest("Globex", null, null, null, null);

        assertThatThrownBy(() -> companyService.update(1L, request))
                .isInstanceOf(CompanyAlreadyExistsException.class)
                .hasMessageContaining("Globex");
    }

    @Test
    void update_whenNotFound_throwsCompanyNotFoundException() {
        when(companyRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> companyService.update(99L, new CompanyUpdateRequest("X", null, null, null, null)))
                .isInstanceOf(CompanyNotFoundException.class);
    }

    @Test
    void delete_whenExists_deletesById() {
        when(companyRepository.existsById(1L)).thenReturn(true);

        companyService.delete(1L);

        verify(companyRepository).deleteById(1L);
    }

    @Test
    void delete_whenNotFound_throwsCompanyNotFoundExceptionAndDoesNotDelete() {
        when(companyRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> companyService.delete(99L))
                .isInstanceOf(CompanyNotFoundException.class);
        verify(companyRepository, never()).deleteById(any());
    }

    private static CompanyEntity entityOf(Long id, String name) {
        CompanyEntity entity = new CompanyEntity();
        entity.setId(id);
        entity.setName(name);
        return entity;
    }
}