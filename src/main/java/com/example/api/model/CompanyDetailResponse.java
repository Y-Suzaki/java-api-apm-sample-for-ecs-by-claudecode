package com.example.api.model;

import java.time.LocalDateTime;
import java.util.List;

public record CompanyDetailResponse(
        Long id,
        String name,
        String industry,
        String email,
        String phone,
        String address,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<SubsidiaryResponse> subsidiaries
) {
    public static CompanyDetailResponse from(CompanyEntity entity) {
        List<SubsidiaryResponse> subs = entity.getSubsidiaries().stream()
                .map(SubsidiaryResponse::from)
                .toList();
        return new CompanyDetailResponse(
                entity.getId(),
                entity.getName(),
                entity.getIndustry(),
                entity.getEmail(),
                entity.getPhone(),
                entity.getAddress(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                subs
        );
    }
}