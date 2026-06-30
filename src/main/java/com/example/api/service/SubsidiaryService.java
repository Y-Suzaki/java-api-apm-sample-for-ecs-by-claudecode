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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class SubsidiaryService {

    private final SubsidiaryRepository subsidiaryRepository;
    private final CompanyRepository companyRepository;

    @Transactional
    public SubsidiaryResponse create(Long companyId, SubsidiaryCreateRequest request) {
        CompanyEntity company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CompanyNotFoundException(companyId));

        if (subsidiaryRepository.existsByCompanyIdAndName(companyId, request.name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Subsidiary already exists: " + request.name());
        }

        SubsidiaryEntity entity = new SubsidiaryEntity();
        entity.setCompany(company);
        entity.setName(request.name());
        entity.setIndustry(request.industry());
        entity.setEmail(request.email());
        entity.setPhone(request.phone());
        entity.setAddress(request.address());

        return SubsidiaryResponse.from(subsidiaryRepository.save(entity));
    }

    @Transactional
    public SubsidiaryResponse update(Long companyId, Long id, SubsidiaryUpdateRequest request) {
        if (!companyRepository.existsById(companyId)) {
            throw new CompanyNotFoundException(companyId);
        }

        SubsidiaryEntity entity = subsidiaryRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new SubsidiaryNotFoundException(id));

        if (request.name() != null && !request.name().equals(entity.getName())
                && subsidiaryRepository.existsByCompanyIdAndNameAndIdNot(companyId, request.name(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Subsidiary already exists: " + request.name());
        }

        if (request.name() != null) entity.setName(request.name());
        if (request.industry() != null) entity.setIndustry(request.industry());
        if (request.email() != null) entity.setEmail(request.email());
        if (request.phone() != null) entity.setPhone(request.phone());
        if (request.address() != null) entity.setAddress(request.address());

        return SubsidiaryResponse.from(subsidiaryRepository.save(entity));
    }

    @Transactional
    public void delete(Long companyId, Long id) {
        if (!companyRepository.existsById(companyId)) {
            throw new CompanyNotFoundException(companyId);
        }

        SubsidiaryEntity entity = subsidiaryRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new SubsidiaryNotFoundException(id));

        subsidiaryRepository.delete(entity);
    }
}
