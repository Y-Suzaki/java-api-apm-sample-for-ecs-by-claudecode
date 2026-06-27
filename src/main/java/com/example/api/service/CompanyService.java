package com.example.api.service;

import com.example.api.exception.CompanyAlreadyExistsException;
import com.example.api.exception.CompanyNotFoundException;
import com.example.api.model.CompanyCreateRequest;
import com.example.api.model.CompanyEntity;
import com.example.api.model.CompanyResponse;
import com.example.api.model.CompanyUpdateRequest;
import com.example.api.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;

    @Transactional
    public CompanyResponse create(CompanyCreateRequest request) {
        if (companyRepository.existsByName(request.name())) {
            throw new CompanyAlreadyExistsException(request.name());
        }

        CompanyEntity entity = new CompanyEntity();
        entity.setName(request.name());
        entity.setIndustry(request.industry());
        entity.setEmail(request.email());
        entity.setPhone(request.phone());
        entity.setAddress(request.address());

        return CompanyResponse.from(companyRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<CompanyResponse> list(int limit) {
        return companyRepository.findAllBy(PageRequest.of(0, limit)).stream()
                .map(CompanyResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CompanyResponse get(Long id) {
        return companyRepository.findById(id)
                .map(CompanyResponse::from)
                .orElseThrow(() -> new CompanyNotFoundException(id));
    }

    @Transactional
    public CompanyResponse update(Long id, CompanyUpdateRequest request) {
        CompanyEntity entity = companyRepository.findById(id)
                .orElseThrow(() -> new CompanyNotFoundException(id));

        if (request.name() != null && !request.name().equals(entity.getName())
                && companyRepository.existsByNameAndIdNot(request.name(), id)) {
            throw new CompanyAlreadyExistsException(request.name());
        }

        if (request.name() != null) entity.setName(request.name());
        if (request.industry() != null) entity.setIndustry(request.industry());
        if (request.email() != null) entity.setEmail(request.email());
        if (request.phone() != null) entity.setPhone(request.phone());
        if (request.address() != null) entity.setAddress(request.address());

        return CompanyResponse.from(companyRepository.save(entity));
    }

    @Transactional
    public void delete(Long id) {
        if (!companyRepository.existsById(id)) {
            throw new CompanyNotFoundException(id);
        }
        companyRepository.deleteById(id);
    }
}