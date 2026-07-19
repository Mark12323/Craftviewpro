package org.example.aisurv.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.example.aisurv.event.CameraHealthState;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "camera_health_events")
public class CameraHealthEventEntity {
    @Id
    private UUID id;
    @Column(name = "camera_id", nullable = false)
    private UUID cameraId;
    @Enumerated(EnumType.STRING)
    @Column(name = "operational_state", nullable = false)
    private CameraHealthState state;
    @Column(name = "reason_code", nullable = false, length = 64)
    private String reasonCode;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
    @Column(name = "persisted_at", nullable = false)
    private Instant persistedAt;

    protected CameraHealthEventEntity() {
    }

    public static CameraHealthEventEntity of(UUID cameraId, CameraHealthState state,
                                             String reasonCode, Instant occurredAt) {
        CameraHealthEventEntity entity = new CameraHealthEventEntity();
        entity.id = UUID.randomUUID();
        entity.cameraId = cameraId;
        entity.state = state;
        entity.reasonCode = reasonCode;
        entity.occurredAt = occurredAt;
        entity.persistedAt = Instant.now();
        return entity;
    }
}
