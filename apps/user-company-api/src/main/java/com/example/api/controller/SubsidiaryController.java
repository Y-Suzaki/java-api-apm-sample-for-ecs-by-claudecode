package com.example.api.controller;

import com.example.api.model.SubsidiaryCreateRequest;
import com.example.api.model.SubsidiaryResponse;
import com.example.api.model.SubsidiaryUpdateRequest;
import com.example.api.service.SubsidiaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/companies/{companyId}/subsidiaries")
@RequiredArgsConstructor
public class SubsidiaryController {

    private final SubsidiaryService subsidiaryService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubsidiaryResponse createSubsidiary(
            @PathVariable Long companyId,
            @Valid @RequestBody SubsidiaryCreateRequest request) {
        return subsidiaryService.create(companyId, request);
    }

    @PutMapping("/{id}")
    public SubsidiaryResponse updateSubsidiary(
            @PathVariable Long companyId,
            @PathVariable Long id,
            @Valid @RequestBody SubsidiaryUpdateRequest request) {
        return subsidiaryService.update(companyId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSubsidiary(
            @PathVariable Long companyId,
            @PathVariable Long id) {
        subsidiaryService.delete(companyId, id);
    }
}
