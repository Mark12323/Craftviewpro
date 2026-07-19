package org.example.aisurv.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.example.aisurv.camera.CameraRuntimeHealth;
import org.example.aisurv.event.CameraHealthState;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "camera_runtime_health")
public class CameraRuntimeHealthEntity {
    @Id
    @Column(name = "camera_id")
    private UUID cameraId;
    @Enumerated(EnumType.STRING)
    @Column(name = "operational_state", nullable = false)
    private CameraHealthState state;
    @Column(name = "reason_code", nullable = false, length = 64)
    private String reasonCode;
    @Column(name = "state_changed_at", nullable = false)
    private Instant stateChangedAt;
    @Column(name = "last_seen_at")
    private Instant lastSeenAt;
    @Column(name = "online_since")
    private Instant onlineSince;
    @Column(name = "accumulated_uptime_ms", nullable = false)
    private long accumulatedUptimeMillis;
    @Column(name = "reconnect_attempts", nullable = false)
    private long reconnectAttempts;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CameraRuntimeHealthEntity() {
    }

    public static CameraRuntimeHealthEntity initial(UUID cameraId, CameraHealthState state,
                                                    String reasonCode, Instant occurredAt) {
        CameraRuntimeHealthEntity entity = new CameraRuntimeHealthEntity();
        entity.cameraId = cameraId;
        entity.state = state;
        entity.reasonCode = reasonCode;
        entity.stateChangedAt = occurredAt;
        entity.updatedAt = occurredAt;
        if (connected(state)) entity.onlineSince = occurredAt;
        if (state == CameraHealthState.RECONNECTING) entity.reconnectAttempts = 1;
        return entity;
    }

    public boolean transition(CameraHealthState next, String reason, Instant occurredAt) {
        if (state == next && reasonCode.equals(reason)) {
            updatedAt = occurredAt;
            return false;
        }
        if (connected(state) && !connected(next) && onlineSince != null) {
            accumulatedUptimeMillis += Math.max(0, Duration.between(onlineSince, occurredAt).toMillis());
            onlineSince = null;
        } else if (!connected(state) && connected(next)) {
            onlineSince = occurredAt;
        }
        if (next == CameraHealthState.RECONNECTING) reconnectAttempts++;
        state = next;
        reasonCode = reason;
        stateChangedAt = occurredAt;
        updatedAt = occurredAt;
        return true;
    }

    public void seen(Instant observedAt) {
        if (lastSeenAt == null || observedAt.isAfter(lastSeenAt)) lastSeenAt = observedAt;
        updatedAt = observedAt;
    }

    public CameraHealthState state() {
        return state;
    }

    public CameraRuntimeHealth toRecord(Instant observedAt) {
        long uptime = accumulatedUptimeMillis;
        if (connected(state) && onlineSince != null) {
            uptime += Math.max(0, Duration.between(onlineSince, observedAt).toMillis());
        }
        return new CameraRuntimeHealth(cameraId, state, reasonCode, stateChangedAt, lastSeenAt,
                onlineSince, uptime, reconnectAttempts, updatedAt);
    }

    private static boolean connected(CameraHealthState state) {
        return state == CameraHealthState.ONLINE || state == CameraHealthState.IMPAIRED;
    }
}
