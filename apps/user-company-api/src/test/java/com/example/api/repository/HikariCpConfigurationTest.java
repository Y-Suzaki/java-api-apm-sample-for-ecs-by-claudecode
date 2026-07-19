package com.example.api.repository;

import com.example.api.support.MySqlContainerSupport;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * HikariCP 7 へのアップグレード後も application.yml の spring.datasource.hikari.* が
 * 正しくバインドされていることを確認する（プロパティ名・型の解釈が壊れていないかの回帰確認）。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class HikariCpConfigurationTest implements MySqlContainerSupport {

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL_CONTAINER::getUsername);
        registry.add("spring.datasource.password", MYSQL_CONTAINER::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private HikariDataSource hikariDataSource;

    @Test
    void hikariPoolSettings_matchApplicationYml() {
        assertThat(hikariDataSource.getMaximumPoolSize()).isEqualTo(10);
        assertThat(hikariDataSource.getMinimumIdle()).isEqualTo(2);
        assertThat(hikariDataSource.getConnectionTimeout()).isEqualTo(30000);
        assertThat(hikariDataSource.getIdleTimeout()).isEqualTo(600000);
    }
}