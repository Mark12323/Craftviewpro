DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM cameras GROUP BY lower(trim(display_name)) HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Camera display-name duplicates must be resolved before V3 migration';
    END IF;
    IF EXISTS (SELECT 1 FROM cameras GROUP BY trim(rtsp_url) HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Camera stream duplicates must be resolved before V3 migration';
    END IF;
END $$;

UPDATE cameras
SET display_name_key = lower(trim(display_name))
WHERE display_name_key IS NULL;

UPDATE cameras
SET rtsp_url_key = trim(rtsp_url)
WHERE rtsp_url_key IS NULL;

ALTER TABLE cameras ALTER COLUMN display_name_key SET NOT NULL;
ALTER TABLE cameras ALTER COLUMN rtsp_url_key SET NOT NULL;

ALTER TABLE cameras ADD COLUMN stream_validated_at TIMESTAMPTZ;
ALTER TABLE cameras ADD COLUMN stream_validation_method VARCHAR(32);

CREATE TABLE camera_runtime_health (
    camera_id UUID PRIMARY KEY REFERENCES cameras(id) ON DELETE CASCADE,
    operational_state VARCHAR(32) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    state_changed_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ,
    online_since TIMESTAMPTZ,
    accumulated_uptime_ms BIGINT NOT NULL DEFAULT 0,
    reconnect_attempts BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE camera_health_events (
    id UUID PRIMARY KEY,
    camera_id UUID NOT NULL,
    operational_state VARCHAR(32) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    persisted_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_camera_health_events_camera_time
    ON camera_health_events(camera_id, occurred_at DESC);

CREATE TABLE camera_configuration_audit (
    id UUID PRIMARY KEY,
    camera_id UUID NOT NULL,
    action VARCHAR(32) NOT NULL,
    previous_version BIGINT,
    resulting_version BIGINT,
    actor_type VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_camera_configuration_audit_camera_time
    ON camera_configuration_audit(camera_id, occurred_at DESC);
