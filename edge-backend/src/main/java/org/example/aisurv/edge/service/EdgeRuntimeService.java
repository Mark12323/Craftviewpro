package org.example.aisurv.edge.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.example.aisurv.camera.CameraApplicationService;
import org.example.aisurv.camera.CameraPriority;
import org.example.aisurv.camera.CameraRegistrationRequest;
import org.example.aisurv.camera.CameraRegistrationResult;
import org.example.aisurv.camera.CameraSummary;
import org.example.aisurv.camera.DiscoveredCamera;
import org.example.aisurv.camera.CameraDetails;
import org.example.aisurv.camera.CameraUpdateRequest;
import org.example.aisurv.contract.v1.ApiVersionV1;
import org.example.aisurv.contract.v1.CameraConfigurationStateV1;
import org.example.aisurv.contract.v1.CameraDiscoveryRequestV1;
import org.example.aisurv.contract.v1.CameraDiscoveryResponseV1;
import org.example.aisurv.contract.v1.CameraListResponseV1;
import org.example.aisurv.contract.v1.CameraPriorityV1;
import org.example.aisurv.contract.v1.CameraSummaryV1;
import org.example.aisurv.contract.v1.ComponentHealthV1;
import org.example.aisurv.contract.v1.ComponentStatusV1;
import org.example.aisurv.contract.v1.EdgeCapabilityV1;
import org.example.aisurv.contract.v1.EdgeHealthResponseV1;
import org.example.aisurv.contract.v1.EdgeStatusV1;
import org.example.aisurv.contract.v1.DiscoveredCameraV1;
import org.example.aisurv.contract.v1.RegisterCameraRequestV1;
import org.example.aisurv.contract.v1.RegisterCameraResponseV1;
import org.example.aisurv.contract.v1.CameraDetailV1;
import org.example.aisurv.contract.v1.UpdateCameraRequestV1;
import org.example.aisurv.contract.v1.SetCameraConfigurationStateRequestV1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.example.aisurv.edge.runtime.CameraRuntimeSupervisor;
import org.example.aisurv.edge.runtime.CameraHealthService;
import org.example.aisurv.edge.update.CameraUpdateBroker;
import org.example.aisurv.contract.v1.CameraUpdateKindV1;
import org.example.aisurv.contract.v1.CameraRuntimeHealthV1;
import org.example.aisurv.contract.v1.CameraOperationalStateV1;
import org.example.aisurv.camera.CameraRuntimeHealth;

import java.time.Instant;
import java.time.Duration;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLTransientConnectionException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class EdgeRuntimeService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EdgeRuntimeService.class);
    private final CameraApplicationService cameraService;
    private final String edgeVersion;
    private final CameraRuntimeSupervisor runtimeSupervisor;
    private final CameraUpdateBroker cameraUpdates;
    private final CameraHealthService cameraHealth;
    private final AtomicReference<DatabaseHealth> databaseHealth =
            new AtomicReference<>(new DatabaseHealth(ComponentStatusV1.STARTING, "INITIALIZING"));
    private final AtomicReference<DatabaseHealth> cameraRuntimeHealth =
            new AtomicReference<>(new DatabaseHealth(ComponentStatusV1.STARTING, "INITIALIZING"));
    private final AtomicBoolean probeActive = new AtomicBoolean();
    private final AtomicBoolean discoveryActive = new AtomicBoolean();
    private final AtomicBoolean runtimeStarted = new AtomicBoolean();
    private final ExecutorService probeExecutor = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("edge-database-probe").factory());

    public EdgeRuntimeService(CameraApplicationService cameraService,
                              @Value("${aisurv.edge.version:1.0-SNAPSHOT}") String edgeVersion) {
        this(cameraService, edgeVersion, null, null, null);
    }

    public EdgeRuntimeService(CameraApplicationService cameraService,
                              @Value("${aisurv.edge.version:1.0-SNAPSHOT}") String edgeVersion,
                              CameraRuntimeSupervisor runtimeSupervisor) {
        this(cameraService, edgeVersion, runtimeSupervisor, null, null);
    }

    public EdgeRuntimeService(CameraApplicationService cameraService,
                              @Value("${aisurv.edge.version:1.0-SNAPSHOT}") String edgeVersion,
                              CameraRuntimeSupervisor runtimeSupervisor,
                              CameraUpdateBroker cameraUpdates) {
        this(cameraService, edgeVersion, runtimeSupervisor, cameraUpdates, null);
    }

    @Autowired
    public EdgeRuntimeService(CameraApplicationService cameraService,
                              @Value("${aisurv.edge.version:1.0-SNAPSHOT}") String edgeVersion,
                              CameraRuntimeSupervisor runtimeSupervisor,
                              CameraUpdateBroker cameraUpdates,
                              CameraHealthService cameraHealth) {
        this.cameraService = cameraService;
        this.edgeVersion = edgeVersion;
        this.runtimeSupervisor = runtimeSupervisor;
        this.cameraUpdates = cameraUpdates;
        this.cameraHealth = cameraHealth;
        if (runtimeSupervisor == null) {
            cameraRuntimeHealth.set(new DatabaseHealth(ComponentStatusV1.UP, "NOT_CONFIGURED"));
        }
    }

    @PostConstruct
    void start() {
        scheduleProbe();
    }

    @Scheduled(fixedDelayString = "${aisurv.edge.database-probe-delay-ms:10000}")
    void scheduleProbe() {
        if (probeActive.compareAndSet(false, true)) {
            probeExecutor.execute(() -> {
                try {
                    probeNow();
                } finally {
                    probeActive.set(false);
                }
            });
        }
    }

    void probeNow() {
        try {
            cameraService.listCameras();
            databaseHealth.set(new DatabaseHealth(ComponentStatusV1.UP, "AVAILABLE"));
            if (runtimeSupervisor != null && !runtimeStarted.get()) {
                try {
                    runtimeSupervisor.reconcile();
                    runtimeStarted.set(true);
                    cameraRuntimeHealth.set(new DatabaseHealth(ComponentStatusV1.UP, "MONITORING"));
                } catch (RuntimeException failure) {
                    runtimeStarted.set(false);
                    cameraRuntimeHealth.set(new DatabaseHealth(ComponentStatusV1.DOWN, "RUNTIME_START_FAILED"));
                    LOGGER.warn("Camera runtime startup failed");
                    LOGGER.debug("Camera runtime startup failure", failure);
                }
            }
        } catch (RuntimeException failure) {
            databaseHealth.set(new DatabaseHealth(ComponentStatusV1.DOWN, "DATABASE_UNAVAILABLE"));
            LOGGER.warn("Edge camera registry is unavailable");
            LOGGER.debug("Edge camera registry probe failure", failure);
        }
    }

    public EdgeHealthResponseV1 health() {
        DatabaseHealth database = databaseHealth.get();
        DatabaseHealth cameraRuntime = cameraRuntimeHealth.get();
        EdgeStatusV1 status = database.status() == ComponentStatusV1.STARTING
                || cameraRuntime.status() == ComponentStatusV1.STARTING ? EdgeStatusV1.STARTING
                : database.status() == ComponentStatusV1.UP && cameraRuntime.status() == ComponentStatusV1.UP
                ? EdgeStatusV1.READY : EdgeStatusV1.DEGRADED;
        return new EdgeHealthResponseV1(
                ApiVersionV1.CURRENT,
                edgeVersion,
                status,
                Set.of(EdgeCapabilityV1.CAMERA_QUERY, EdgeCapabilityV1.CAMERA_DISCOVERY,
                        EdgeCapabilityV1.CAMERA_REGISTRATION, EdgeCapabilityV1.CAMERA_LIFECYCLE,
                        EdgeCapabilityV1.EDGE_MONITORING, EdgeCapabilityV1.CAMERA_UPDATE_STREAM,
                        EdgeCapabilityV1.CAMERA_RUNTIME_HEALTH),
                Map.of("database", new ComponentHealthV1(database.status(), database.reasonCode()),
                        "cameraRuntime", new ComponentHealthV1(
                                cameraRuntime.status(), cameraRuntime.reasonCode())),
                Instant.now());
    }

    public CameraListResponseV1 cameras() {
        requireDatabase();
        try {
            Map<UUID, CameraRuntimeHealth> health = cameraHealth == null ? Map.of() : cameraHealth.health();
            List<CameraSummaryV1> cameras = cameraService.listCameras().stream()
                    .map(camera -> toContract(camera, health))
                    .sorted(Comparator.comparing(CameraSummaryV1::displayName, String.CASE_INSENSITIVE_ORDER)
                            .thenComparing(CameraSummaryV1::id))
                    .toList();
            return new CameraListResponseV1(cameras, Instant.now());
        } catch (RuntimeException failure) {
            databaseHealth.set(new DatabaseHealth(ComponentStatusV1.DOWN, "DATABASE_UNAVAILABLE"));
            throw new DependencyUnavailableException("Camera registry is unavailable");
        }
    }

    public CameraDiscoveryResponseV1 discover(CameraDiscoveryRequestV1 request) {
        if (request == null || request.timeoutSeconds() < 1 || request.timeoutSeconds() > 15) {
            throw new IllegalArgumentException("Discovery timeout must be between 1 and 15 seconds");
        }
        if (!discoveryActive.compareAndSet(false, true)) {
            throw new OperationInProgressException("Camera discovery is already in progress");
        }
        try {
            List<DiscoveredCameraV1> cameras = cameraService.discover(
                            Duration.ofSeconds(request.timeoutSeconds())).stream()
                    .map(EdgeRuntimeService::toContract)
                    .sorted(Comparator.comparing(DiscoveredCameraV1::host, String.CASE_INSENSITIVE_ORDER)
                            .thenComparing(DiscoveredCameraV1::deviceId))
                    .toList();
            return new CameraDiscoveryResponseV1(cameras, Instant.now());
        } catch (RuntimeException failure) {
            LOGGER.warn("ONVIF discovery failed");
            LOGGER.debug("ONVIF discovery failure", failure);
            throw new CameraDiscoveryFailedException("Camera discovery could not access the local network");
        } finally {
            discoveryActive.set(false);
        }
    }

    public RegisterCameraResponseV1 register(RegisterCameraRequestV1 request) {
        requireDatabase();
        if (request == null) {
            throw new IllegalArgumentException("Camera registration request is required");
        }
        if (request.priority() == CameraPriorityV1.UNKNOWN) {
            throw new IllegalArgumentException("Camera priority is invalid");
        }
        validateRegistration(request);
        CameraPriority priority = request.priority() == null
                ? CameraPriority.NORMAL : CameraPriority.valueOf(request.priority().name());
        try {
            CameraRegistrationResult result = cameraService.register(new CameraRegistrationRequest(
                    request.displayName(), request.rtspUrl(), request.onvifServiceUrl(), request.manufacturer(),
                    request.model(), request.host(), request.location(), request.building(), request.floor(),
                    request.zone(), priority, request.enabled(), request.credentialReference()));
            if (cameraHealth != null) cameraHealth.configurationChanged(result.id(), result.enabled(), Instant.now());
            publishCameraUpdate(CameraUpdateKindV1.UPSERTED, result.id(), result.version());
            reconcileRuntime();
            return new RegisterCameraResponseV1(
                    result.id(), result.displayName(), result.enabled()
                    ? CameraConfigurationStateV1.ENABLED : CameraConfigurationStateV1.DISABLED,
                    result.version());
        } catch (IllegalArgumentException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            if (isDatabaseUnavailable(failure)) {
                databaseHealth.set(new DatabaseHealth(ComponentStatusV1.DOWN, "DATABASE_UNAVAILABLE"));
                LOGGER.warn("Camera registration failed because the registry is unavailable");
                LOGGER.debug("Camera registration persistence failure", failure);
                throw new DependencyUnavailableException("Camera registry is unavailable");
            }
            throw failure;
        }
    }

    public CameraDetailV1 camera(UUID id) {
        requireDatabase();
        return toContract(cameraService.getCamera(id));
    }

    public CameraDetailV1 update(UUID id, UpdateCameraRequestV1 request) {
        requireDatabase();
        if (request == null || request.expectedVersion() == null || request.expectedVersion() < 0
                || request.priority() == CameraPriorityV1.UNKNOWN)
            throw new IllegalArgumentException("Camera update request is invalid");
        requireLength(request.displayName(), "Camera display name", 160);
        if (request.replacementRtspUrl() != null)
            requireLength(request.replacementRtspUrl(), "Camera RTSP URL", 2_048);
        optionalLength(request.onvifServiceUrl(), "Camera ONVIF URL", 2_048);
        optionalLength(request.manufacturer(), "Camera manufacturer", 160);
        optionalLength(request.model(), "Camera model", 160);
        optionalLength(request.host(), "Camera host", 255);
        optionalLength(request.location(), "Camera location", 255);
        optionalLength(request.building(), "Camera building", 160);
        optionalLength(request.floor(), "Camera floor", 80);
        optionalLength(request.zone(), "Camera zone", 160);
        CameraPriority priority = request.priority() == null ? CameraPriority.NORMAL
                : CameraPriority.valueOf(request.priority().name());
        CameraDetailV1 updated = toContract(cameraService.updateCamera(id, request.expectedVersion(), new CameraUpdateRequest(
                request.displayName(), request.replacementRtspUrl(), request.onvifServiceUrl(), request.manufacturer(),
                request.model(), request.host(), request.location(), request.building(), request.floor(),
                request.zone(), priority)));
        publishCameraUpdate(CameraUpdateKindV1.UPSERTED, updated.id(), updated.version());
        reconcileRuntime();
        return updated;
    }

    public CameraSummaryV1 setConfigurationState(UUID id, SetCameraConfigurationStateRequestV1 request) {
        requireDatabase();
        if (request == null || request.expectedVersion() == null || request.expectedVersion() < 0
                || request.configurationState() == null
                || request.configurationState() == CameraConfigurationStateV1.UNKNOWN)
            throw new IllegalArgumentException("Camera configuration state is invalid");
        CameraSummary updatedCamera = cameraService.setEnabled(id, request.expectedVersion(),
                request.configurationState() == CameraConfigurationStateV1.ENABLED);
        if (cameraHealth != null) cameraHealth.configurationChanged(id, updatedCamera.enabled(), Instant.now());
        Map<UUID, CameraRuntimeHealth> health = cameraHealth == null ? Map.of() : cameraHealth.health();
        CameraSummaryV1 updated = toContract(updatedCamera, health);
        publishCameraUpdate(CameraUpdateKindV1.UPSERTED, updated.id(), updated.version());
        reconcileRuntime();
        return updated;
    }

    public void delete(UUID id, long version) {
        requireDatabase();
        cameraService.deleteCamera(id, version);
        publishCameraUpdate(CameraUpdateKindV1.DELETED, id, version);
        reconcileRuntime();
    }

    private static CameraSummaryV1 toContract(CameraSummary camera, Map<UUID, CameraRuntimeHealth> healthById) {
        CameraRuntimeHealthV1 runtimeHealth = toContract(healthById.get(camera.id()), camera.enabled());
        return new CameraSummaryV1(
                camera.id(),
                camera.displayName(),
                camera.location(),
                camera.building(),
                camera.floor(),
                camera.zone(),
                CameraPriorityV1.valueOf(camera.priority().name()),
                camera.enabled() ? CameraConfigurationStateV1.ENABLED : CameraConfigurationStateV1.DISABLED,
                camera.version(), runtimeHealth);
    }

    private static CameraRuntimeHealthV1 toContract(CameraRuntimeHealth health, boolean enabled) {
        Instant observedAt = Instant.now();
        if (!enabled) {
            return new CameraRuntimeHealthV1(CameraOperationalStateV1.DISABLED, "CONFIGURATION_DISABLED",
                    health == null ? observedAt : health.stateChangedAt(),
                    health == null ? null : health.lastSeenAt(),
                    health == null ? 0 : health.accumulatedUptimeMillis(),
                    health == null ? 0 : health.reconnectAttempts(), observedAt);
        }
        if (health == null) {
            return new CameraRuntimeHealthV1(CameraOperationalStateV1.OFFLINE, "WORKER_NOT_OBSERVED",
                    observedAt, null, 0, 0, observedAt);
        }
        return new CameraRuntimeHealthV1(CameraOperationalStateV1.valueOf(health.state().name()),
                health.reasonCode(), health.stateChangedAt(), health.lastSeenAt(),
                health.accumulatedUptimeMillis(), health.reconnectAttempts(), observedAt);
    }

    private static CameraDetailV1 toContract(CameraDetails camera) {
        return new CameraDetailV1(camera.id(), camera.displayName(), camera.onvifServiceUrl(), camera.manufacturer(),
                camera.model(), camera.host(), camera.location(), camera.building(), camera.floor(), camera.zone(),
                CameraPriorityV1.valueOf(camera.priority().name()), camera.enabled()
                ? CameraConfigurationStateV1.ENABLED : CameraConfigurationStateV1.DISABLED, camera.version());
    }

    private static DiscoveredCameraV1 toContract(DiscoveredCamera camera) {
        return new DiscoveredCameraV1(
                camera.deviceId(), camera.manufacturer(), camera.model(), camera.host(),
                camera.onvifServiceUrl(), camera.requiresAuthentication(), camera.discoveredAt());
    }

    private void requireDatabase() {
        if (databaseHealth.get().status() != ComponentStatusV1.UP) {
            throw new DependencyUnavailableException("Camera registry is unavailable");
        }
    }

    private void reconcileRuntime() {
        if (runtimeSupervisor == null) return;
        try {
            runtimeSupervisor.reconcile();
            runtimeStarted.set(true);
            cameraRuntimeHealth.set(new DatabaseHealth(ComponentStatusV1.UP, "MONITORING"));
        }
        catch (RuntimeException failure) {
            runtimeStarted.set(false);
            cameraRuntimeHealth.set(new DatabaseHealth(ComponentStatusV1.DOWN, "RECONCILIATION_FAILED"));
            LOGGER.warn("Camera runtime reconciliation failed", failure);
        }
    }

    private void publishCameraUpdate(CameraUpdateKindV1 kind, UUID id, long version) {
        if (cameraUpdates != null) cameraUpdates.publish(kind, id, version);
    }

    private static void validateRegistration(RegisterCameraRequestV1 request) {
        requireLength(request.displayName(), "Camera display name", 160);
        requireLength(request.rtspUrl(), "Camera RTSP URL", 2_048);
        optionalLength(request.onvifServiceUrl(), "Camera ONVIF URL", 2_048);
        optionalLength(request.manufacturer(), "Camera manufacturer", 160);
        optionalLength(request.model(), "Camera model", 160);
        optionalLength(request.host(), "Camera host", 255);
        optionalLength(request.location(), "Camera location", 255);
        optionalLength(request.building(), "Camera building", 160);
        optionalLength(request.floor(), "Camera floor", 80);
        optionalLength(request.zone(), "Camera zone", 160);
        optionalLength(request.credentialReference(), "Camera credential reference", 255);
        if (request.credentialReference() != null && !request.credentialReference().isBlank()) {
            throw new IllegalArgumentException(
                    "Authenticated cameras are not enabled in this release");
        }
    }

    private static void requireLength(String value, String label, int maximum) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        optionalLength(value, label, maximum);
    }

    private static void optionalLength(String value, String label, int maximum) {
        if (value != null && value.trim().length() > maximum) {
            throw new IllegalArgumentException(label + " must not exceed " + maximum + " characters");
        }
    }

    private static boolean isDatabaseUnavailable(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLTransientConnectionException
                    || cause instanceof SQLNonTransientConnectionException
                    || cause instanceof ConnectException
                    || cause instanceof SocketTimeoutException
                    || cause instanceof UnknownHostException) return true;
            if (cause instanceof SQLException sql && sql.getSQLState() != null
                    && (sql.getSQLState().startsWith("08")
                    || "57P01".equals(sql.getSQLState())
                    || "57P02".equals(sql.getSQLState())
                    || "57P03".equals(sql.getSQLState()))) return true;
        }
        return false;
    }

    @PreDestroy
    void stop() {
        probeExecutor.shutdownNow();
        try {
            if (!probeExecutor.awaitTermination(8, TimeUnit.SECONDS)) {
                LOGGER.warn("Database probe did not stop within the edge shutdown deadline");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record DatabaseHealth(ComponentStatusV1 status, String reasonCode) {
    }
}
