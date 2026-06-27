package com.example.api.model;

import java.time.LocalDateTime;

public record CompanyResponse(
        Long id,
        String name,
        String industry,
        String email,
        String phone,
        String address,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CompanyResponse from(CompanyEntity entity) {
        return new CompanyResponse(
                entity.getId(),
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