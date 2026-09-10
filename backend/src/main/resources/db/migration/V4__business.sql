ALTER TABLE activities ADD COLUMN archived_at DATETIME NULL;
ALTER TABLE teams ADD COLUMN archived_at DATETIME NULL;
ALTER TABLE messages ADD COLUMN client_message_id VARCHAR(80) NULL,
    ADD UNIQUE KEY uk_team_message_client (sender_id, client_message_id),
    ADD KEY idx_team_message_cursor (team_id, id);
ALTER TABLE activity_chat_message ADD COLUMN client_message_id VARCHAR(80) NULL,
    ADD UNIQUE KEY uk_activity_message_client (sender_id, client_message_id),
    ADD KEY idx_activity_message_cursor (activity_id, id);
ALTER TABLE dm_message ADD COLUMN client_message_id VARCHAR(80) NULL,
    ADD UNIQUE KEY uk_dm_message_client (sender_id, client_message_id),
    ADD KEY idx_dm_message_cursor (sender_id, receiver_id, id);
ALTER TABLE notifications ADD COLUMN dedup_key VARCHAR(120) NULL,
    ADD UNIQUE KEY uk_notification_dedup (user_id, dedup_key),
    ADD KEY idx_notification_retention (created_at, id);
CREATE TABLE admin_audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    actor_id BIGINT NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id BIGINT NULL,
    before_value TEXT NULL,
    after_value TEXT NULL,
    reason VARCHAR(1000) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_audit_created (created_at, id),
    KEY idx_audit_target (target_type, target_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- A singleton guard serializes role/status edits, including the last-admin invariant.
CREATE TABLE admin_governance_lock (id TINYINT PRIMARY KEY) ENGINE=InnoDB;
INSERT INTO admin_governance_lock (id) VALUES (1);
CREATE TABLE team_tag (
    team_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (team_id,tag_id),
    CONSTRAINT fk_team_tag_team FOREIGN KEY(team_id) REFERENCES teams(id) ON DELETE CASCADE,
    CONSTRAINT fk_team_tag_tag FOREIGN KEY(tag_id) REFERENCES tags(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE activity_reminder_delivery (
    activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    start_time DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(activity_id,user_id,start_time),
    KEY idx_reminder_retention(created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_activity_discovery ON activities(status,audit_status,start_time,id);
CREATE INDEX idx_team_discovery ON teams(status,created_at,id);
CREATE INDEX idx_team_members_current ON team_member(team_id,status,user_id);
CREATE INDEX idx_behavior_retention ON user_behavior_log(event_time,id);
CREATE INDEX idx_event_retention ON event_log(event_time,id);
