package com.example.api.repository;

import com.example.api.model.CompanyEntity;
import com.example.api.model.SubsidiaryEntity;
import com.example.api.support.MySqlContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * CompanyRepository を実 MySQL(Testcontainers)に対して検証するテスト。
 *
 * <p>H2 ではなく実 MySQL を使うのは、mysql/init/01_init.sql の DATETIME(6) や
 * UNIQUE 制約など MySQL 固有のスキーマ挙動を H2 の互換モードでは正確に再現できないため。
 * Hibernate の ddl-auto は本番同様 validate にし、エンティティとスキーマの不整合も検出する。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class CompanyRepositoryTest implements MySqlContainerSupport {

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL_CONTAINER::getUsername);
        registry.add("spring.datasource.password", MYSQL_CONTAINER::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void existsByName_whenNameExists_returnsTrue() {
        entityManager.persistAndFlush(companyOf("Acme"));

        assertThat(companyRepository.existsByName("Acme")).isTrue();
        assertThat(companyRepository.existsByName("Globex")).isFalse();
    }

    @Test
    void existsByNameAndIdNot_excludesGivenId() {
        CompanyEntity acme = entityManager.persistAndFlush(companyOf("Acme"));
        entityManager.persistAndFlush(companyOf("Globex"));

        assertThat(companyRepository.existsByNameAndIdNot("Acme", acme.getId())).isFalse();
        assertThat(companyRepository.existsByNameAndIdNot("Globex", acme.getId())).isTrue();
    }

    @Test
    void findAllBy_respectsPageableLimit() {
        entityManager.persistAndFlush(companyOf("Acme"));
        entityManager.persistAndFlush(companyOf("Globex"));
        entityManager.persistAndFlush(companyOf("Initech"));

        List<CompanyEntity> result = companyRepository.findAllBy(PageRequest.of(0, 2));

        assertThat(result).hasSize(2);
    }

    @Test
    void findByIdWithSubsidiaries_loadsSubsidiariesEagerly() {
        CompanyEntity company = entityManager.persistAndFlush(companyOf("Acme"));
        SubsidiaryEntity subsidiary = new SubsidiaryEntity();
        subsidiary.setCompany(company);
        subsidiary.setName("Acme Sub");
        entityManager.persistAndFlush(subsidiary);
        entityManager.clear();

        Optional<CompanyEntity> found = companyRepository.findByIdWithSubsidiaries(company.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getSubsidiaries()).hasSize(1);
        assertThat(found.get().getSubsidiaries().get(0).getName()).isEqualTo("Acme Sub");
    }

    @Test
    void findByIdWithSubsidiaries_whenNotFound_returnsEmpty() {
        assertThat(companyRepository.findByIdWithSubsidiaries(-1L)).isEmpty();
    }

    @Test
    void save_populatesCreationAndUpdateTimestamps() {
        CompanyEntity saved = companyRepository.saveAndFlush(companyOf("Acme"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    private static CompanyEntity companyOf(String name) {
        CompanyEntity entity = new CompanyEntity();
        entity.setName(name);
        return entity;
    }
}