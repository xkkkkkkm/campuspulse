CREATE TABLE direct_message_guard (
    low_id BIGINT NOT NULL,
    high_id BIGINT NOT NULL,
    PRIMARY KEY(low_id,high_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE group_read_state (
    channel VARCHAR(16) NOT NULL,
    target_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    last_read_id BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY(channel,target_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
ALTER TABLE messages ADD COLUMN content_type VARCHAR(16) NOT NULL DEFAULT 'TEXT', ADD COLUMN image_url VARCHAR(512) NULL;
ALTER TABLE activity_chat_message ADD COLUMN content_type VARCHAR(16) NOT NULL DEFAULT 'TEXT', ADD COLUMN image_url VARCHAR(512) NULL;
ALTER TABLE dm_message ADD COLUMN content_type VARCHAR(16) NOT NULL DEFAULT 'TEXT', ADD COLUMN image_url VARCHAR(512) NULL;

INSERT IGNORE INTO app_initialization(name) VALUES('account-identifiers');
