package org.example.aisurv.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.example.aisurv.app.CameraDefinition;
import org.example.aisurv.camera.CameraPriority;
import org.example.aisurv.camera.CameraRegistrationRequest;
import org.example.aisurv.camera.CameraStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cameras")
public class CameraEntity {
    @Id
    private UUID id;

    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;

    @Column(name = "rtsp_url", nullable = false)
    private String rtspUrl;

    @Column(name = "onvif_service_url")
    private String onvifServiceUrl;

    private String manufacturer;
    private String model;
    private String host;
    private String location;
    private String building;
    private String floor;
    private String zone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CameraPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CameraStatus status;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "credential_reference")
    private String credentialReference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CameraEntity() {
    }

    public static CameraEntity from(CameraRegistrationRequest request) {
        Instant now = Instant.now();
        CameraDefinition definition = new CameraDefinition(
                required(request.displayName(), "display name"),
                request.rtspUrl()
        );
        CameraEntity entity = new CameraEntity();
        entity.id = UUID.randomUUID();
        entity.displayName = definition.name();
        entity.rtspUrl = definition.rtspUrl();
        entity.onvifServiceUrl = blankToNull(request.onvifServiceUrl());
        entity.manufacturer = blankToNull(request.manufacturer());
        entity.model = blankToNull(request.model());
        entity.host = blankToNull(request.host());
        entity.location = blankToNull(request.location());
        entity.building = blankToNull(request.building());
        entity.floor = blankToNull(request.floor());
        entity.zone = blankToNull(request.zone());
        entity.priority = request.priority() == null ? CameraPriority.NORMAL : request.priority();
        entity.status = request.enabled() ? CameraStatus.REGISTERED : CameraStatus.DISABLED;
        entity.enabled = request.enabled();
        entity.credentialReference = blankToNull(request.credentialReference());
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public CameraDefinition toCameraDefinition() {
        return new CameraDefinition(displayName, rtspUrl);
    }

    public UUID id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public boolean enabled() {
        return enabled;
    }

    public String location() {
        return location;
    }

    public String building() {
        return building;
    }

    public String floor() {
        return floor;
    }

    public String zone() {
        return zone;
    }

    public CameraPriority priority() {
        return priority;
    }

    public CameraStatus status() {
        return status;
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Camera " + label + " is required");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
