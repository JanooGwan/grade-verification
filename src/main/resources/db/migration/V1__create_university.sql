CREATE TABLE university (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_university PRIMARY KEY (id),
    CONSTRAINT uk_university_code UNIQUE (code)
);

CREATE INDEX idx_university_name ON university (name);
