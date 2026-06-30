package com.example.api.repository;

import com.example.api.model.SubsidiaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubsidiaryRepository extends JpaRepository<SubsidiaryEntity, Long> {

    boolean existsByCompanyIdAndName(Long companyId, String name);

    boolean existsByCompanyIdAndNameAndIdNot(Long companyId, String name, Long id);

    Optional<SubsidiaryEntity> findByIdAndCompanyId(Long id, Long companyId);
}
