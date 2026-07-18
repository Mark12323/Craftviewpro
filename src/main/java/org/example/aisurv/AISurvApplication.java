package org.example.aisurv;

import atlantafx.base.theme.PrimerLight;
import com.aegisvision.cctv.DashboardView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.bytedeco.ffmpeg.global.avutil;
import org.example.aisurv.app.ApplicationContext;
import org.example.aisurv.app.BackgroundTaskTracker;
import org.example.aisurv.camera.CameraApplicationService;
import org.example.aisurv.camera.LocalCameraApplicationService;
import org.example.aisurv.persistence.ApplicationPersistence;
import org.example.aisurv.persistence.DatabaseSettings;
import org.example.aisurv.stream.StreamManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

public class AISurvApplication extends Application {
    private static final Logger LOGGER = LoggerFactory.getLogger(AISurvApplication.class);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(8);
    private DashboardView dashboard;
    private StreamManager streamManager;
    private final BackgroundTaskTracker backgroundTasks = new BackgroundTaskTracker();
    private final ApplicationPersistence persistence = new ApplicationPersistence(DatabaseSettings.fromEnvironment());
    private final CameraApplicationService cameraService = new LocalCameraApplicationService(persistence);
    private final AtomicBoolean shutdownStarted = new AtomicBoolean();
    private final AtomicBoolean servicesStopped = new AtomicBoolean();

    @Override
    public void start(Stage stage) {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        avutil.av_log_set_level(avutil.AV_LOG_FATAL);

        dashboard = new DashboardView(new String[0], backgroundTasks, cameraService);

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
        dashboard.setApplicationStatus("Initializing services", false);
        Task<ApplicationContext> bootstrap = new Task<>() {
            @Override
            protected ApplicationContext call() {
                return new ApplicationContext(cameraService);
            }
        };
        bootstrap.setOnSucceeded(event -> startLiveStreams(bootstrap.getValue()));
        bootstrap.setOnFailed(event -> {
            Throwable failure = bootstrap.getException();
            LOGGER.error("Application bootstrap failed", failure);
            dashboard.setApplicationStatus("Initialization failed", true);
        });
        backgroundTasks.start(bootstrap, "application-bootstrap");
    }

    private void startLiveStreams(ApplicationContext context) {
        if (shutdownStarted.get()) {
            return;
        }
        dashboard.setConfiguredCameras(context.cameraNames());
        boolean degraded = context.databaseState() == ApplicationContext.DatabaseState.UNAVAILABLE;
        String source = switch (context.cameraSource()) {
            case DATABASE -> "database registry";
            case ENVIRONMENT -> "environment fallback";
            case NONE -> "no camera source";
        };
        if (context.cameras().isEmpty()) {
            dashboard.setApplicationStatus(
                    degraded ? "Degraded: database unavailable and no cameras configured" : "Ready: no cameras configured",
                    degraded);
            LOGGER.warn("No cameras configured");
            return;
        }

        StreamManager manager = new StreamManager(dashboard);
        streamManager = manager;
        if (shutdownStarted.get()) {
            manager.stopAll();
            return;
        }
        manager.startAll(context.cameras());
        dashboard.setApplicationStatus(
                (degraded ? "Degraded" : "Ready") + ": cameras loaded from " + source,
                degraded);
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
        try {
            backgroundTasks.requestStop();
            if (streamManager != null) streamManager.stopAllUntil(deadlineNanos);
            backgroundTasks.awaitUntil(deadlineNanos);
        } finally {
            persistence.close();
        }
    }
}
