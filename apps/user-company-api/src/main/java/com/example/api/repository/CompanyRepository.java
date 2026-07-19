package com.example.api.repository;

import com.example.api.model.CompanyEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<CompanyEntity, Long> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    List<CompanyEntity> findAllBy(Pageable pageable);

    @Query("SELECT c FROM CompanyEntity c LEFT JOIN FETCH c.subsidiaries WHERE c.id = :id")
    Optional<CompanyEntity> findByIdWithSubsidiaries(@Param("id") Long id);
}