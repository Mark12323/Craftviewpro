package org.example.aisurv.app;

import org.example.aisurv.camera.CameraApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLTransientConnectionException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApplicationContext {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationContext.class);
    private static final Pattern NUMBERED_CAMERA_URL = Pattern.compile("CAM_(\\d+)_URL");
    private final LoadResult loadResult;
    private final List<CameraDefinition> cameras;

    public ApplicationContext(CameraApplicationService cameraService) {
        loadResult = loadCameras(Objects.requireNonNull(cameraService, "cameraService"));
        cameras = loadResult.cameras();
    }

    public enum DatabaseState {
        CONNECTED,
        UNAVAILABLE
    }

    public enum CameraSource {
        DATABASE,
        ENVIRONMENT,
        NONE
    }

    public String[] cameraNames() {
        return cameras.stream()
                .map(CameraDefinition::name)
                .toArray(String[]::new);
    }

    public List<CameraDefinition> cameras() {
        return cameras;
    }

    public DatabaseState databaseState() {
        return loadResult.databaseState();
    }

    public CameraSource cameraSource() {
        return loadResult.cameraSource();
    }

    private LoadResult loadCameras(CameraApplicationService cameraService) {
        DatabaseCameraResult database = loadRegisteredCameras(cameraService);
        Map<String, String> environment = System.getenv();
        boolean fallbackAllowed = environmentFallbackAllowed(environment);
        List<CameraDefinition> selected = selectCameras(
                database.cameras(), database.available(), environment, fallbackAllowed);
        if (database.available() && !selected.isEmpty()) {
            LOGGER.info("Loaded {} enabled cameras from database", selected.size());
            return new LoadResult(selected, DatabaseState.CONNECTED, CameraSource.DATABASE);
        }
        if (database.available()) {
            LOGGER.info("Database connected with no enabled cameras");
            return new LoadResult(List.of(), DatabaseState.CONNECTED, CameraSource.NONE);
        }
        if (!fallbackAllowed) {
            LOGGER.warn("Environment camera fallback is disabled");
            return new LoadResult(List.of(), DatabaseState.UNAVAILABLE, CameraSource.NONE);
        }
        return environmentResult(selected);
    }

    static List<CameraDefinition> selectCameras(List<CameraDefinition> databaseCameras, boolean databaseAvailable,
                                                Map<String, String> environment, boolean fallbackAllowed) {
        if (databaseAvailable) return List.copyOf(databaseCameras);
        if (!fallbackAllowed) return List.of();
        String cameraList = environment.get("AISURV_CAMERAS");
        return cameraList == null || cameraList.isBlank()
                ? parseNumberedCameraEnvironment(environment)
                : parseCameraList(cameraList);
    }

    private LoadResult environmentResult(List<CameraDefinition> environmentCameras) {
        CameraSource source = environmentCameras.isEmpty() ? CameraSource.NONE : CameraSource.ENVIRONMENT;
        if (source == CameraSource.ENVIRONMENT) {
            LOGGER.info("Loaded {} camera(s) from development environment configuration", environmentCameras.size());
        }
        return new LoadResult(environmentCameras, DatabaseState.UNAVAILABLE, source);
    }

    private DatabaseCameraResult loadRegisteredCameras(CameraApplicationService cameraService) {
        try {
            return new DatabaseCameraResult(cameraService.listEnabledCameras(), true);
        } catch (RuntimeException e) {
            if (!isDatabaseUnavailable(e)) {
                throw e;
            }
            LOGGER.warn("Database camera registry unavailable; using environment camera configuration");
            LOGGER.debug("Database camera registry startup failure", e);
            return new DatabaseCameraResult(List.of(), false);
        }
    }

    static boolean isDatabaseUnavailable(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLTransientConnectionException
                    || cause instanceof SQLNonTransientConnectionException
                    || cause instanceof ConnectException
                    || cause instanceof SocketTimeoutException
                    || cause instanceof UnknownHostException) {
                return true;
            }
            if (cause instanceof SQLException sqlException) {
                String sqlState = sqlException.getSQLState();
                if (sqlState != null && sqlState.startsWith("08")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<CameraDefinition> parseCameraList(String cameraList) {
        List<CameraDefinition> configuredCameras = new ArrayList<>();
        String[] entries = cameraList.split(";");

        for (int i = 0; i < entries.length; i++) {
            String entry = entries[i].trim();
            if (entry.isBlank()) {
                continue;
            }

            String[] parts = entry.split("=", 2);
            if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
                configuredCameras.add(new CameraDefinition(parts[0].trim(), parts[1].trim()));
                continue;
            }

            configuredCameras.add(new CameraDefinition("Camera " + (i + 1), entry));
        }

        return deduplicate(configuredCameras);
    }

    private static List<CameraDefinition> parseNumberedCameraEnvironment(Map<String, String> environment) {
        List<CameraDefinition> configuredCameras = environment.entrySet().stream()
                .map(entry -> toNumberedCamera(environment, entry))
                .filter(camera -> camera != null)
                .sorted(Comparator.comparingInt(NumberedCamera::index))
                .map(NumberedCamera::definition)
                .toList();
        return deduplicate(configuredCameras);
    }

    private static NumberedCamera toNumberedCamera(Map<String, String> environment, Map.Entry<String, String> entry) {
        Matcher matcher = NUMBERED_CAMERA_URL.matcher(entry.getKey());
        if (!matcher.matches() || entry.getValue() == null || entry.getValue().isBlank()) {
            return null;
        }

        int index = Integer.parseInt(matcher.group(1));
        String name = environment.getOrDefault("CAM_" + index + "_NAME", "Camera " + index);
        return new NumberedCamera(index, new CameraDefinition(name, entry.getValue()));
    }

    private static List<CameraDefinition> deduplicate(List<CameraDefinition> cameras) {
        Map<String, CameraDefinition> uniqueUrls = new LinkedHashMap<>();
        for (CameraDefinition camera : cameras) {
            CameraDefinition previous = uniqueUrls.putIfAbsent(camera.rtspUrl(), camera);
            if (previous != null) {
                LOGGER.warn("Ignoring duplicate camera stream configured for {}", camera.name());
            }
        }
        return List.copyOf(uniqueUrls.values());
    }

    private static boolean environmentFallbackAllowed(Map<String, String> environment) {
        return Boolean.parseBoolean(environment.getOrDefault("AISURV_ALLOW_ENV_CAMERA_FALLBACK", "true"));
    }

    private record NumberedCamera(int index, CameraDefinition definition) {
    }

    private record DatabaseCameraResult(List<CameraDefinition> cameras, boolean available) {
    }

    private record LoadResult(List<CameraDefinition> cameras, DatabaseState databaseState, CameraSource cameraSource) {
    }
}
