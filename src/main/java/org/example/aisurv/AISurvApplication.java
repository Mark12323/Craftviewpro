package org.example.aisurv;

import atlantafx.base.theme.PrimerLight;
import com.aegisvision.cctv.DashboardView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.example.aisurv.app.BackgroundTaskTracker;
import org.example.aisurv.edgeclient.EdgeApiClient;
import org.example.aisurv.edgeclient.EdgeConnectionService;
import org.example.aisurv.edgeclient.EdgeConnectionSnapshot;
import org.example.aisurv.edgeclient.EdgeConnectionState;
import org.example.aisurv.edgeclient.HttpEdgeApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.example.aisurv.contract.v1.CameraSummaryV1;
import org.example.aisurv.contract.v1.CameraConfigurationStateV1;
import org.example.aisurv.contract.v1.EdgeCapabilityV1;

public class AISurvApplication extends Application {
    private static final Logger LOGGER = LoggerFactory.getLogger(AISurvApplication.class);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(8);
    private DashboardView dashboard;
    private final BackgroundTaskTracker backgroundTasks = new BackgroundTaskTracker();
    private final EdgeApiClient edgeApiClient = HttpEdgeApiClient.fromEnvironment();
    private final EdgeConnectionService edgeConnectionService = new EdgeConnectionService(edgeApiClient);
    private final AtomicBoolean shutdownStarted = new AtomicBoolean();
    private final AtomicBoolean servicesStopped = new AtomicBoolean();

    @Override
    public void start(Stage stage) {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        dashboard = new DashboardView(new String[0], backgroundTasks, edgeApiClient);

        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double width = Math.min(1200, screen.getWidth() * 0.88);
        double height = Math.min(780, screen.getHeight() * 0.88);
        Scene scene = new Scene(dashboard, width, height);
        scene.getStylesheets().add(Objects.requireNonNull(
                AISurvApplication.class.getResource("/com/aegisvision/cctv/dashboard.css"),
                "Missing dashboard.css resource").toExternalForm());
        stage.setTitle("CraftView | Advanced AI Surveillance System");
        stage.setMinWidth(Math.min(720, screen.getWidth()));
        stage.setMinHeight(Math.min(520, screen.getHeight()));
        for (int size : new int[]{16, 20, 24, 32, 40, 48, 64, 128, 256}) {
            stage.getIcons().add(new Image(Objects.requireNonNull(AISurvApplication.class.getResource(
                    "/com/aegisvision/cctv/icons/app/CraftView-AppIcon-" + size + ".png"),
                    "Missing CraftView application icon at " + size + "px").toExternalForm()));
        }
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> {
            event.consume();
            if (shutdownStarted.compareAndSet(false, true)) {
                beginShutdown(stage);
            }
        });
        stage.show();
        stage.centerOnScreen();

        bootstrapApplication();
    }

    private void bootstrapApplication() {
        dashboard.setApplicationStatus("Connecting to local edge", false);
        startEdgeMonitoring(List.of());
    }

    static boolean shouldUseEdgeMonitoring(EdgeConnectionSnapshot edge) {
        return edge != null && edge.state() == EdgeConnectionState.AVAILABLE
                && edge.health() != null
                && edge.health().capabilities().contains(EdgeCapabilityV1.EDGE_MONITORING);
    }

    private void startEdgeMonitoring(List<CameraSummaryV1> cameras) {
        if (shutdownStarted.get()) return;
        List<CameraSummaryV1> enabled = cameras.stream()
                .filter(camera -> camera.configurationState() == CameraConfigurationStateV1.ENABLED).toList();
        AtomicReference<List<CameraSummaryV1>> currentCameras = new AtomicReference<>(enabled);
        AtomicBoolean edgeAvailable = new AtomicBoolean(false);
        dashboard.setConfiguredCameras(enabled.stream().map(CameraSummaryV1::displayName).toArray(String[]::new));
        backgroundTasks.start(() -> edgeConnectionService.monitorCameraUpdates(connection -> {
            boolean monitoringAvailable = shouldUseEdgeMonitoring(connection);
            edgeAvailable.set(monitoringAvailable);
            dashboard.setEdgeConnection(connection);
            if (monitoringAvailable) {
                dashboard.setApplicationStatus("Ready: monitoring owned by local edge", false);
            } else if (connection.state() == EdgeConnectionState.AVAILABLE) {
                dashboard.setApplicationStatus("Edge does not support monitoring API v1.3", true);
            } else {
                dashboard.setApplicationStatus(connection.message(), true);
            }
        }, inventory -> {
            List<CameraSummaryV1> refreshed = inventory.cameras().stream()
                    .filter(camera -> camera.configurationState() == CameraConfigurationStateV1.ENABLED).toList();
            currentCameras.set(refreshed);
            dashboard.updateConfiguredCameras(refreshed);
            if (edgeAvailable.get()) {
                dashboard.setApplicationStatus("Ready: monitoring owned by local edge", false);
            }
        }), "edge-update-monitor");
        backgroundTasks.start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                if (edgeAvailable.get()) {
                    for (CameraSummaryV1 camera : currentCameras.get()) {
                        if (camera.runtimeHealth() == null
                                || (camera.runtimeHealth().state()
                                != org.example.aisurv.contract.v1.CameraOperationalStateV1.ONLINE
                                && camera.runtimeHealth().state()
                                != org.example.aisurv.contract.v1.CameraOperationalStateV1.IMPAIRED)) continue;
                        try {
                            byte[] jpeg = edgeApiClient.snapshot(camera.id());
                            if (jpeg.length > 0) {
                                var image = ImageIO.read(new ByteArrayInputStream(jpeg));
                                if (image != null) dashboard.updateFrame(camera.displayName(), image);
                            }
                        } catch (RuntimeException | java.io.IOException failure) {
                            LOGGER.debug("Edge snapshot unavailable for {}", camera.displayName());
                        }
                    }
                }
                try { Thread.sleep(500); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        }, "edge-snapshot-poller");
    }

    @Override
    public void stop() {
        shutdownStarted.set(true);
        stopServices();
        if (dashboard != null) dashboard.dispose();
    }

    private void beginShutdown(Stage stage) {
        dashboard.setApplicationStatus("Stopping services", false);
        Thread shutdownThread = new Thread(() -> {
            stopServices();
            Platform.runLater(() -> {
                if (dashboard != null) dashboard.dispose();
                stage.setOnCloseRequest(null);
                stage.close();
            });
        }, "application-shutdown");
        shutdownThread.setDaemon(false);
        shutdownThread.start();
    }

    private void stopServices() {
        if (!servicesStopped.compareAndSet(false, true)) {
            return;
        }
        long deadlineNanos = System.nanoTime() + SHUTDOWN_TIMEOUT.toNanos();
        edgeConnectionService.close();
        backgroundTasks.requestStop();
        backgroundTasks.awaitUntil(deadlineNanos);
    }
}
