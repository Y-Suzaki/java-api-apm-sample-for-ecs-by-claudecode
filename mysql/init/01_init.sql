CREATE TABLE IF NOT EXISTS companies (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(200) NOT NULL,
    industry   VARCHAR(100),
    email      VARCHAR(255),
    phone      VARCHAR(50),
    address    VARCHAR(500),
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_companies_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS subsidiaries (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    company_id BIGINT       NOT NULL,
    name       VARCHAR(200) NOT NULL,
    industry   VARCHAR(100),
    email      VARCHAR(255),
    phone      VARCHAR(50),
    address    VARCHAR(500),
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_subsidiaries_company_name (company_id, name),
    CONSTRAINT fk_subsidiaries_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
