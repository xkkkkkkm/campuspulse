-- Existing V1 installations upgrade without rewriting the baseline.
ALTER TABLE users ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0;
-- A legacy, unverified address must never reserve another person's identity.
UPDATE users SET email = NULL WHERE email_verified = 0;

CREATE TABLE email_code_guard (
    email VARCHAR(128) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    last_sent_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (email, purpose)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE uploaded_file (
    id CHAR(32) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    kind VARCHAR(16) NOT NULL,
    url VARCHAR(512) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_upload_url (url),
    KEY idx_upload_owner (user_id, created_at),
    CONSTRAINT fk_upload_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE support_ticket (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    subject VARCHAR(500) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_support_owner (user_id, id),
    KEY idx_support_status (status, updated_at),
    CONSTRAINT fk_support_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT ck_support_status CHECK (status IN ('OPEN','IN_PROGRESS','CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE support_reply (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ticket_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    content VARCHAR(4000) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_support_reply (ticket_id, id),
    CONSTRAINT fk_support_reply_ticket FOREIGN KEY (ticket_id) REFERENCES support_ticket(id),
    CONSTRAINT fk_support_reply_author FOREIGN KEY (author_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE support_dify_budget (
    budget_date DATE PRIMARY KEY,
    request_count INT NOT NULL DEFAULT 0
) ENGINE=InnoDB;

CREATE TABLE auth_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NULL,
    action VARCHAR(32) NOT NULL,
    result VARCHAR(16) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_auth_audit_time (created_at),
    KEY idx_auth_audit_user (user_id, created_at)
) ENGINE=InnoDB;
