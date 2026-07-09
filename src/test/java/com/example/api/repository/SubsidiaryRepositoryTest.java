package com.example.api.repository;

import com.example.api.model.CompanyEntity;
import com.example.api.model.SubsidiaryEntity;
import com.example.api.support.MySqlContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class SubsidiaryRepositoryTest implements MySqlContainerSupport {

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL_CONTAINER::getUsername);
        registry.add("spring.datasource.password", MYSQL_CONTAINER::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private SubsidiaryRepository subsidiaryRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void existsByCompanyIdAndName_scopesUniquenessPerCompany() {
        CompanyEntity acme = entityManager.persistAndFlush(companyOf("Acme"));
        CompanyEntity globex = entityManager.persistAndFlush(companyOf("Globex"));
        entityManager.persistAndFlush(subsidiaryOf(acme, "Sub A"));

        assertThat(subsidiaryRepository.existsByCompanyIdAndName(acme.getId(), "Sub A")).isTrue();
        // 同名でも別会社配下なら重複扱いにならない
        assertThat(subsidiaryRepository.existsByCompanyIdAndName(globex.getId(), "Sub A")).isFalse();
    }

    @Test
    void existsByCompanyIdAndName_isCaseInsensitive_perCollation() {
        // Spring Data JPA 4.0 で derived クエリの生成方式が JPQL 文字列生成に変わったため、
        // 複数条件(AND)を伴うクエリでも utf8mb4_unicode_ci の大文字小文字非依存比較が
        // 維持されているかを確認する。
        CompanyEntity acme = entityManager.persistAndFlush(companyOf("Acme"));
        entityManager.persistAndFlush(subsidiaryOf(acme, "Sub A"));

        assertThat(subsidiaryRepository.existsByCompanyIdAndName(acme.getId(), "sub a")).isTrue();
        assertThat(subsidiaryRepository.existsByCompanyIdAndName(acme.getId(), "SUB A")).isTrue();
    }

    @Test
    void existsByCompanyIdAndNameAndIdNot_excludesGivenId() {
        CompanyEntity acme = entityManager.persistAndFlush(companyOf("Acme"));
        SubsidiaryEntity subA = entityManager.persistAndFlush(subsidiaryOf(acme, "Sub A"));

        assertThat(subsidiaryRepository.existsByCompanyIdAndNameAndIdNot(acme.getId(), "Sub A", subA.getId()))
                .isFalse();

        SubsidiaryEntity subB = entityManager.persistAndFlush(subsidiaryOf(acme, "Sub B"));
        assertThat(subsidiaryRepository.existsByCompanyIdAndNameAndIdNot(acme.getId(), "Sub A", subB.getId()))
                .isTrue();
    }

    @Test
    void findByIdAndCompanyId_whenBelongsToCompany_returnsEntity() {
        CompanyEntity acme = entityManager.persistAndFlush(companyOf("Acme"));
        SubsidiaryEntity subA = entityManager.persistAndFlush(subsidiaryOf(acme, "Sub A"));

        Optional<SubsidiaryEntity> found = subsidiaryRepository.findByIdAndCompanyId(subA.getId(), acme.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Sub A");
    }

    @Test
    void findByIdAndCompanyId_whenBelongsToDifferentCompany_returnsEmpty() {
        CompanyEntity acme = entityManager.persistAndFlush(companyOf("Acme"));
        CompanyEntity globex = entityManager.persistAndFlush(companyOf("Globex"));
        SubsidiaryEntity subA = entityManager.persistAndFlush(subsidiaryOf(acme, "Sub A"));

        Optional<SubsidiaryEntity> found = subsidiaryRepository.findByIdAndCompanyId(subA.getId(), globex.getId());

        assertThat(found).isEmpty();
    }

    @Test
    void deletingCompany_cascadesToSubsidiariesViaDatabaseForeignKey() {
        CompanyEntity acme = entityManager.persistAndFlush(companyOf("Acme"));
        SubsidiaryEntity subA = entityManager.persistAndFlush(subsidiaryOf(acme, "Sub A"));
        Long companyId = acme.getId();
        Long subsidiaryId = subA.getId();
        // CompanyService.delete() は companyRepository.deleteById() のみを呼び、
        // subsidiaries コレクションを介した Hibernate 側のカスケードには依存しない。
        // 実際の削除は mysql/init/01_init.sql の ON DELETE CASCADE 制約に委ねているため、
        // 永続化コンテキストをクリアしてから同じ経路で検証する。
        entityManager.clear();

        companyRepository.deleteById(companyId);
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(SubsidiaryEntity.class, subsidiaryId)).isNull();
    }

    private static CompanyEntity companyOf(String name) {
        CompanyEntity entity = new CompanyEntity();
        entity.setName(name);
        return entity;
    }

    private static SubsidiaryEntity subsidiaryOf(CompanyEntity company, String name) {
        SubsidiaryEntity entity = new SubsidiaryEntity();
        entity.setCompany(company);
        entity.setName(name);
        return entity;
    }
}