package com.example.api.model;

import java.time.LocalDateTime;

public record SubsidiaryResponse(
        Long id,
        Long companyId,
        String name,
        String industry,
        String email,
        String phone,
        String address,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static SubsidiaryResponse from(SubsidiaryEntity entity) {
        return new SubsidiaryResponse(
                entity.getId(),
                entity.getCompany().getId(),
                entity.getName(),
                entity.getIndustry(),
                entity.getEmail(),
                entity.getPhone(),
                entity.getAddress(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}