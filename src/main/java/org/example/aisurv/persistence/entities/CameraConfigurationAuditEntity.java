package org.example.aisurv.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.example.aisurv.camera.CameraAuditAction;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "camera_configuration_audit")
public class CameraConfigurationAuditEntity {
    @Id
    private UUID id;
    @Column(name = "camera_id", nullable = false)
    private UUID cameraId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CameraAuditAction action;
    @Column(name = "previous_version")
    private Long previousVersion;
    @Column(name = "resulting_version")
    private Long resultingVersion;
    @Column(name = "actor_type", nullable = false)
    private String actorType;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected CameraConfigurationAuditEntity() {
    }

    public static CameraConfigurationAuditEntity of(UUID cameraId, CameraAuditAction action,
                                                     Long previousVersion, Long resultingVersion) {
        CameraConfigurationAuditEntity entity = new CameraConfigurationAuditEntity();
        entity.id = UUID.randomUUID();
        entity.cameraId = cameraId;
        entity.action = action;
        entity.previousVersion = previousVersion;
        entity.resultingVersion = resultingVersion;
        entity.actorType = "LOCAL_DESKTOP";
        entity.occurredAt = Instant.now();
        return entity;
    }
}
