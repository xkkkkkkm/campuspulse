-- Preserve historical usage counters while replacing the provider-specific integration.
RENAME TABLE support_dify_budget TO support_ai_budget;

CREATE TABLE support_conversation (
    id CHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_token CHAR(36) NULL,
    busy_until DATETIME NULL,
    INDEX idx_support_conversation_owner (user_id, updated_at),
    CONSTRAINT fk_support_conversation_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE support_chat_turn (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id CHAR(36) NOT NULL,
    message VARCHAR(500) NOT NULL,
    answer VARCHAR(4000) NOT NULL,
    source VARCHAR(32) NOT NULL,
    citations_json TEXT NOT NULL,
    suggest_escalation BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_support_turn_conversation (conversation_id, id),
    CONSTRAINT fk_support_turn_conversation FOREIGN KEY (conversation_id) REFERENCES support_conversation(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
