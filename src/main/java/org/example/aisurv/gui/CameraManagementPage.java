package org.example.aisurv.gui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.example.aisurv.app.BackgroundTaskTracker;
import org.example.aisurv.camera.CameraApplicationService;
import org.example.aisurv.camera.CameraPriority;
import org.example.aisurv.camera.CameraRegistrationRequest;
import org.example.aisurv.camera.CameraRegistrationResult;
import org.example.aisurv.camera.CameraSummary;
import org.example.aisurv.camera.DiscoveredCamera;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class CameraManagementPage {
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraManagementPage.class);
    private final BorderPane root = new BorderPane();
    private final ObservableList<CameraSummary> registeredCameras = FXCollections.observableArrayList();
    private final ListView<CameraSummary> registeredList = new ListView<>(registeredCameras);
    private final ObservableList<DiscoveredCamera> discoveredCameras = FXCollections.observableArrayList();
    private final ListView<DiscoveredCamera> discoveredList = new ListView<>(discoveredCameras);
    private final Label status = new Label("Ready");
    private final TextField displayName = new TextField();
    private final TextField rtspUrl = new TextField();
    private final TextField onvifServiceUrl = new TextField();
    private final TextField host = new TextField();
    private final TextField location = new TextField();
    private final TextField building = new TextField();
    private final TextField floor = new TextField();
    private final TextField zone = new TextField();
    private final ComboBox<CameraPriority> priority = new ComboBox<>();
    private final BackgroundTaskTracker backgroundTasks;
    private final CameraApplicationService cameraService;

    public CameraManagementPage(BackgroundTaskTracker backgroundTasks, CameraApplicationService cameraService) {
        this.backgroundTasks = Objects.requireNonNull(backgroundTasks, "backgroundTasks");
        this.cameraService = Objects.requireNonNull(cameraService, "cameraService");
        buildUi();
        refreshRegisteredCameras();
    }

    public Node root() {
        return root;
    }

    private void buildUi() {
        root.setPadding(new Insets(24));
        root.getStyleClass().add("content");
        root.setTop(buildHeader());
        root.setCenter(buildContent());
    }

    private Node buildHeader() {
        Label title = new Label("Cameras & Zones");
        title.getStyleClass().add("page-title");

        Label caption = new Label("Discover ONVIF cameras, register approved RTSP streams, and store them in PostgreSQL.");
        caption.getStyleClass().addAll("muted", "page-subtitle");

        VBox header = new VBox(6, title, caption);
        header.setPadding(new Insets(0, 0, 16, 0));
        return header;
    }

    private Node buildContent() {
        VBox inventory = new VBox(16, buildRegisteredPanel(), buildDiscoveryPanel());
        inventory.setPrefWidth(470);
        HBox content = new HBox(16, inventory, buildRegistrationPanel());
        content.setAlignment(Pos.TOP_LEFT);
        return content;
    }

    private Node buildRegisteredPanel() {
        Button refreshButton = new Button("Refresh Registry");
        refreshButton.setOnAction(event -> refreshRegisteredCameras());

        registeredList.setPrefWidth(430);
        registeredList.setPrefHeight(210);
        registeredList.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(CameraSummary camera, boolean empty) {
                super.updateItem(camera, empty);
                if (empty || camera == null) {
                    setText(null);
                    return;
                }
                String state = camera.enabled() ? "Enabled" : "Disabled";
                setText(camera.displayName() + " | " + camera.priority() + " | " + state
                        + "\n" + cameraLocation(camera));
            }
        });

        return panel("Registered Cameras", refreshButton, registeredList);
    }

    private Node buildDiscoveryPanel() {
        Button discoverButton = new Button("Discover ONVIF Cameras");
        discoverButton.setOnAction(event -> discoverCameras());

        discoveredList.setPrefWidth(430);
        discoveredList.setPrefHeight(280);
        discoveredList.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(DiscoveredCamera camera, boolean empty) {
                super.updateItem(camera, empty);
                if (empty || camera == null) {
                    setText(null);
                    return;
                }
                setText(camera.host() + " | " + valueOrUnknown(camera.manufacturer()) + " "
                        + valueOrUnknown(camera.model()) + "\n" + camera.onvifServiceUrl());
            }
        });
        discoveredList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, camera) -> {
            if (camera != null) {
                applyDiscoveredCamera(camera);
            }
        });

        VBox panel = panel("Discovered Devices", discoverButton, discoveredList);
        panel.setPrefWidth(470);
        return panel;
    }

    private Node buildRegistrationPanel() {
        priority.setItems(FXCollections.observableArrayList(CameraPriority.values()));
        priority.setValue(CameraPriority.NORMAL);

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.addRow(0, formLabel("Display Name"), displayName);
        form.addRow(1, formLabel("RTSP URL"), rtspUrl);
        form.addRow(2, formLabel("ONVIF URL"), onvifServiceUrl);
        form.addRow(3, formLabel("Host/IP"), host);
        form.addRow(4, formLabel("Location"), location);
        form.addRow(5, formLabel("Building"), building);
        form.addRow(6, formLabel("Floor"), floor);
        form.addRow(7, formLabel("Zone"), zone);
        form.addRow(8, formLabel("Priority"), priority);

        for (Node child : List.of(displayName, rtspUrl, onvifServiceUrl, host, location, building, floor, zone, priority)) {
            GridPane.setHgrow(child, Priority.ALWAYS);
        }

        Button registerButton = new Button("Register & Enable Camera");
        registerButton.getStyleClass().add("action-button");
        registerButton.setOnAction(event -> registerCamera());

        status.getStyleClass().add("muted");

        VBox panel = panel("Register Camera", form, registerButton, status);
        panel.setPrefWidth(560);
        return panel;
    }

    private VBox panel(String title, Node... children) {
        Label heading = new Label(title);
        heading.getStyleClass().add("section-title");

        VBox panel = new VBox(12);
        panel.getChildren().add(heading);
        panel.getChildren().addAll(children);
        panel.setPadding(new Insets(14));
        panel.getStyleClass().add("section-card");
        return panel;
    }

    private Label formLabel(String text) {
        Label label = new Label(text);
        label.setMinWidth(110);
        label.getStyleClass().add("detail-key");
        return label;
    }

    private void discoverCameras() {
        setStatus("Discovering ONVIF cameras on the local network...", false);
        Task<List<DiscoveredCamera>> task = new Task<>() {
            @Override
            protected List<DiscoveredCamera> call() {
                return cameraService.discover(Duration.ofSeconds(5));
            }
        };
        task.setOnSucceeded(event -> {
            discoveredCameras.setAll(task.getValue());
            setStatus("Discovered " + task.getValue().size() + " camera candidate(s).", false);
        });
        task.setOnFailed(event -> {
            LOGGER.warn("ONVIF discovery failed", task.getException());
            setStatus("Discovery failed. Check network access and application logs.", true);
        });
        backgroundTasks.start(task, "camera-discovery");
    }

    private void refreshRegisteredCameras() {
        Task<List<CameraSummary>> task = new Task<>() {
            @Override
            protected List<CameraSummary> call() {
                return cameraService.listCameras();
            }
        };
        task.setOnSucceeded(event -> {
            registeredCameras.setAll(task.getValue());
            setStatus("Loaded " + task.getValue().size() + " registered camera(s).", false);
        });
        task.setOnFailed(event -> {
            LOGGER.warn("Camera registry refresh failed");
            LOGGER.debug("Camera registry refresh failure", task.getException());
            setStatus("Camera registry unavailable. Check the database connection.", true);
        });
        backgroundTasks.start(task, "camera-registry-refresh");
    }

    private void applyDiscoveredCamera(DiscoveredCamera camera) {
        displayName.setText(defaultName(camera));
        onvifServiceUrl.setText(camera.onvifServiceUrl());
        host.setText(camera.host());
        setStatus("Selected discovered camera. Validate or enter the RTSP URL before registration.", false);
    }

    private void registerCamera() {
        if (displayName.getText().isBlank() || rtspUrl.getText().isBlank()) {
            setStatus("Display name and RTSP URL are required.", true);
            return;
        }

        CameraRegistrationRequest request = new CameraRegistrationRequest(
                displayName.getText(),
                rtspUrl.getText(),
                onvifServiceUrl.getText(),
                null,
                null,
                host.getText(),
                location.getText(),
                building.getText(),
                floor.getText(),
                zone.getText(),
                priority.getValue(),
                true,
                null
        );

        setStatus("Registering camera in PostgreSQL...", false);
        Task<CameraRegistrationResult> task = new Task<>() {
            @Override
            protected CameraRegistrationResult call() {
                return cameraService.register(request);
            }
        };
        task.setOnSucceeded(event -> {
            setStatus("Registered and enabled camera: " + task.getValue().displayName()
                    + ". Restart the application to begin capture.", false);
            clearForm();
            refreshRegisteredCameras();
        });
        task.setOnFailed(event -> {
            LOGGER.warn("Camera registration failed");
            LOGGER.debug("Camera registration failure type: {}", task.getException().getClass().getName());
            setStatus("Registration failed. Check the database connection and camera details.", true);
        });
        backgroundTasks.start(task, "camera-registration");
    }

    private void clearForm() {
        displayName.clear();
        rtspUrl.clear();
        onvifServiceUrl.clear();
        host.clear();
        location.clear();
        building.clear();
        floor.clear();
        zone.clear();
        priority.setValue(CameraPriority.NORMAL);
    }

    private void setStatus(String message, boolean error) {
        Platform.runLater(() -> {
            status.setText(message);
            status.setStyle("-fx-text-fill: " + (error ? "#b42318" : "#667085") + ";");
        });
    }

    private String defaultName(DiscoveredCamera camera) {
        String model = valueOrUnknown(camera.model());
        return "unknown".equals(model) ? "Camera " + camera.host() : model + " " + camera.host();
    }

    private String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private String cameraLocation(CameraSummary camera) {
        String locationText = Stream.of(camera.location(), camera.building(), camera.floor(), camera.zone())
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .reduce((left, right) -> left + " / " + right)
                .orElse("Location unavailable");
        return locationText + " | ID " + camera.id();
    }
}
