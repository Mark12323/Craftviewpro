package org.example.aisurv.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.example.aisurv.app.CameraDefinition;
import org.example.aisurv.camera.CameraPriority;
import org.example.aisurv.camera.CameraRegistrationRequest;
import org.example.aisurv.camera.CameraStatus;
import org.example.aisurv.camera.CameraUpdateRequest;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cameras")
public class CameraEntity {
    @Id
    private UUID id;

    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;
    @Column(name = "display_name_key", length = 160)
    private String displayNameKey;

    @Column(name = "rtsp_url", nullable = false)
    private String rtspUrl;
    @Column(name = "rtsp_url_key")
    private String rtspUrlKey;

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
    @Column(name = "stream_validated_at")
    private Instant streamValidatedAt;
    @Column(name = "stream_validation_method", length = 32)
    private String streamValidationMethod;

    @Version
    @Column(nullable = false)
    private long version;

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
        entity.displayNameKey = definition.name().toLowerCase(java.util.Locale.ROOT);
        entity.rtspUrl = definition.rtspUrl();
        entity.rtspUrlKey = definition.rtspUrl();
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
        entity.streamValidatedAt = now;
        entity.streamValidationMethod = "RTSP_DESCRIBE";
        return entity;
    }

    public CameraDefinition toCameraDefinition() {
        return new CameraDefinition(id, displayName, rtspUrl);
    }

    public void applyUpdate(CameraUpdateRequest request) {
        String stream = request.replacementRtspUrl() == null ? rtspUrl : request.replacementRtspUrl();
        CameraDefinition definition = new CameraDefinition(required(request.displayName(), "display name"), stream);
        displayName = definition.name();
        displayNameKey = definition.name().toLowerCase(java.util.Locale.ROOT);
        rtspUrl = definition.rtspUrl();
        rtspUrlKey = definition.rtspUrl();
        if (request.replacementRtspUrl() != null) {
            streamValidatedAt = Instant.now();
            streamValidationMethod = "RTSP_DESCRIBE";
        }
        onvifServiceUrl = blankToNull(request.onvifServiceUrl());
        manufacturer = blankToNull(request.manufacturer());
        model = blankToNull(request.model());
        host = blankToNull(request.host());
        location = blankToNull(request.location());
        building = blankToNull(request.building());
        floor = blankToNull(request.floor());
        zone = blankToNull(request.zone());
        priority = request.priority() == null ? CameraPriority.NORMAL : request.priority();
        updatedAt = Instant.now();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        status = enabled ? CameraStatus.REGISTERED : CameraStatus.DISABLED;
        updatedAt = Instant.now();
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

    public String rtspUrl() { return rtspUrl; }
    public String onvifServiceUrl() { return onvifServiceUrl; }
    public String manufacturer() { return manufacturer; }
    public String model() { return model; }
    public String host() { return host; }
    public long version() { return version; }

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
