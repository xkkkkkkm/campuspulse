-- Old telemetry cannot be retrospectively treated as trusted conversions.
ALTER TABLE user_behavior_log ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'SERVER';
UPDATE user_behavior_log SET source='LEGACY';
ALTER TABLE user_behavior_log ADD KEY idx_behavior_training(source,event_time,id);
-- Keep multiple immutable score releases; one atomic pointer controls serving.
ALTER TABLE recommend_activity_score DROP PRIMARY KEY,
    ADD PRIMARY KEY(user_id,activity_id,model_version);
ALTER TABLE recommend_team_score DROP PRIMARY KEY,
    ADD PRIMARY KEY(user_id,team_id,model_version);
CREATE TABLE recommendation_release (
    version VARCHAR(64) PRIMARY KEY,
    metrics_json MEDIUMTEXT NOT NULL,
    artifact_sha256 VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE recommendation_active (
    id TINYINT PRIMARY KEY,
    version VARCHAR(64) NULL,
    CONSTRAINT fk_active_release FOREIGN KEY(version) REFERENCES recommendation_release(version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO recommendation_active(id) VALUES(1);
-- A migration, not a trainer, owns the schema. Old demo metrics are explicitly synthetic.
UPDATE recommend_model_version SET metrics_json='{"synthetic":true,"evaluation":"not measured"}',active_flag=0
    WHERE version LIKE 'demo-%' OR version='synthetic-demo-v1';
