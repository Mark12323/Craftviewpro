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
import org.example.aisurv.contract.v1.CameraDiscoveryRequestV1;
import org.example.aisurv.contract.v1.CameraPriorityV1;
import org.example.aisurv.contract.v1.CameraSummaryV1;
import org.example.aisurv.contract.v1.DiscoveredCameraV1;
import org.example.aisurv.contract.v1.RegisterCameraRequestV1;
import org.example.aisurv.contract.v1.RegisterCameraResponseV1;
import org.example.aisurv.contract.v1.CameraDetailV1;
import org.example.aisurv.contract.v1.UpdateCameraRequestV1;
import org.example.aisurv.contract.v1.SetCameraConfigurationStateRequestV1;
import org.example.aisurv.edgeclient.EdgeApiClient;
import org.example.aisurv.edgeclient.EdgeRequestException;
import org.example.aisurv.edgeclient.EdgeUnavailableException;
import org.example.aisurv.edgeclient.IncompatibleEdgeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

public class CameraManagementPage {
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraManagementPage.class);
    private final BorderPane root = new BorderPane();
    private final ObservableList<CameraSummaryV1> registeredCameras = FXCollections.observableArrayList();
    private final ListView<CameraSummaryV1> registeredList = new ListView<>(registeredCameras);
    private final ObservableList<DiscoveredCameraV1> discoveredCameras = FXCollections.observableArrayList();
    private final ListView<DiscoveredCameraV1> discoveredList = new ListView<>(discoveredCameras);
    private final Label status = new Label("Ready");
    private final TextField displayName = new TextField();
    private final TextField rtspUrl = new TextField();
    private final TextField onvifServiceUrl = new TextField();
    private final TextField host = new TextField();
    private final TextField location = new TextField();
    private final TextField building = new TextField();
    private final TextField floor = new TextField();
    private final TextField zone = new TextField();
    private final Button discoverButton = new Button("Discover ONVIF Cameras");
    private final Button saveButton = new Button("Save Changes");
    private final Button stateButton = new Button("Disable Camera");
    private final Button deleteButton = new Button("Delete Camera");
    private CameraDetailV1 selectedCamera;
    private long detailRequestSequence;
    private final ComboBox<CameraPriorityV1> priority = new ComboBox<>();
    private final BackgroundTaskTracker backgroundTasks;
    private final EdgeApiClient edgeApiClient;

    public CameraManagementPage(BackgroundTaskTracker backgroundTasks, EdgeApiClient edgeApiClient) {
        this.backgroundTasks = Objects.requireNonNull(backgroundTasks, "backgroundTasks");
        this.edgeApiClient = Objects.requireNonNull(edgeApiClient, "edgeApiClient");
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

        Label caption = new Label("Query the edge registry, discover ONVIF cameras, and register approved RTSP streams.");
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
            protected void updateItem(CameraSummaryV1 camera, boolean empty) {
                super.updateItem(camera, empty);
                if (empty || camera == null) {
                    setText(null);
                    return;
                }
                String state = camera.configurationState().name();
                setText(camera.displayName() + " | " + camera.priority() + " | " + state
                        + "\n" + cameraLocation(camera));
            }
        });
        registeredList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, camera) -> {
            if (camera != null) loadCamera(camera.id());
        });

        return panel("Registered Cameras", refreshButton, registeredList);
    }

    private Node buildDiscoveryPanel() {
        discoverButton.setOnAction(event -> discoverCameras());

        discoveredList.setPrefWidth(430);
        discoveredList.setPrefHeight(280);
        discoveredList.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(DiscoveredCameraV1 camera, boolean empty) {
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
        priority.setItems(FXCollections.observableArrayList(
                CameraPriorityV1.CRITICAL, CameraPriorityV1.HIGH, CameraPriorityV1.NORMAL, CameraPriorityV1.LOW));
        priority.setValue(CameraPriorityV1.NORMAL);

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

        saveButton.setDisable(true);
        stateButton.setDisable(true);
        deleteButton.setDisable(true);
        saveButton.setOnAction(event -> saveCamera());
        stateButton.setOnAction(event -> toggleCamera());
        deleteButton.setOnAction(event -> deleteCamera());
        HBox lifecycle = new HBox(8, saveButton, stateButton, deleteButton);
        VBox panel = panel("Register or Edit Camera", form, registerButton, lifecycle, status);
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
        if (discoverButton.isDisabled()) return;
        discoverButton.setDisable(true);
        setStatus("Discovering ONVIF cameras on the local network...", false);
        Task<List<DiscoveredCameraV1>> task = new Task<>() {
            @Override
            protected List<DiscoveredCameraV1> call() {
                return edgeApiClient.discoverCameras(new CameraDiscoveryRequestV1(5)).cameras();
            }
        };
        task.setOnSucceeded(event -> {
            discoverButton.setDisable(false);
            discoveredCameras.setAll(task.getValue());
            setStatus("Discovered " + task.getValue().size() + " camera candidate(s).", false);
        });
        task.setOnFailed(event -> {
            discoverButton.setDisable(false);
            LOGGER.warn("ONVIF discovery failed", task.getException());
            setStatus(edgeFailureMessage(task.getException(), "Discovery failed through the edge API."), true);
        });
        backgroundTasks.start(task, "camera-discovery");
    }

    private void refreshRegisteredCameras() {
        Task<List<CameraSummaryV1>> task = new Task<>() {
            @Override
            protected List<CameraSummaryV1> call() {
                return edgeApiClient.listCameras().cameras();
            }
        };
        task.setOnSucceeded(event -> {
            registeredCameras.setAll(task.getValue());
            setStatus("Loaded " + task.getValue().size() + " registered camera(s).", false);
        });
        task.setOnFailed(event -> {
            LOGGER.warn("Edge camera registry refresh failed");
            LOGGER.debug("Camera registry refresh failure", task.getException());
            Throwable failure = task.getException();
            if (failure instanceof IncompatibleEdgeException) {
                setStatus("Edge API is incompatible with this desktop version.", true);
            } else if (failure instanceof EdgeUnavailableException) {
                setStatus("Edge service unavailable. Start the local edge backend.", true);
            } else if (failure instanceof EdgeRequestException requestFailure
                    && requestFailure.statusCode() == 503) {
                setStatus("Edge is running, but the camera registry is unavailable.", true);
            } else {
                setStatus("Camera registry query failed through the edge API.", true);
            }
        });
        backgroundTasks.start(task, "camera-registry-refresh");
    }

    private void applyDiscoveredCamera(DiscoveredCameraV1 camera) {
        detailRequestSequence++;
        selectedCamera = null;
        registeredList.getSelectionModel().clearSelection();
        saveButton.setDisable(true);
        stateButton.setDisable(true);
        deleteButton.setDisable(true);
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

        RegisterCameraRequestV1 request = new RegisterCameraRequestV1(
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
        Task<RegisterCameraResponseV1> task = new Task<>() {
            @Override
            protected RegisterCameraResponseV1 call() {
                return edgeApiClient.registerCamera(request);
            }
        };
        task.setOnSucceeded(event -> {
            setStatus("Registered and enabled camera through the edge: " + task.getValue().displayName()
                    + ". Edge monitoring starts automatically.", false);
            clearForm();
            refreshRegisteredCameras();
        });
        task.setOnFailed(event -> {
            LOGGER.warn("Camera registration failed");
            LOGGER.debug("Camera registration failure type: {}", task.getException().getClass().getName());
            setStatus(edgeFailureMessage(task.getException(), "Camera registration failed through the edge API."), true);
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
        priority.setValue(CameraPriorityV1.NORMAL);
        selectedCamera = null;
        detailRequestSequence++;
        saveButton.setDisable(true);
        stateButton.setDisable(true);
        deleteButton.setDisable(true);
    }

    private void loadCamera(java.util.UUID id) {
        long requestSequence = ++detailRequestSequence;
        Task<CameraDetailV1> task = new Task<>() { @Override protected CameraDetailV1 call() { return edgeApiClient.getCamera(id); } };
        task.setOnSucceeded(event -> {
            CameraSummaryV1 selected = registeredList.getSelectionModel().getSelectedItem();
            if (requestSequence != detailRequestSequence || selected == null || !selected.id().equals(id)) return;
            selectedCamera = task.getValue();
            displayName.setText(selectedCamera.displayName());
            rtspUrl.clear();
            rtspUrl.setPromptText("Leave blank to preserve current stream URL");
            onvifServiceUrl.setText(valueOrEmpty(selectedCamera.onvifServiceUrl()));
            host.setText(valueOrEmpty(selectedCamera.host()));
            location.setText(valueOrEmpty(selectedCamera.location()));
            building.setText(valueOrEmpty(selectedCamera.building()));
            floor.setText(valueOrEmpty(selectedCamera.floor()));
            zone.setText(valueOrEmpty(selectedCamera.zone()));
            priority.setValue(selectedCamera.priority());
            saveButton.setDisable(false); stateButton.setDisable(false); deleteButton.setDisable(false);
            stateButton.setText(selectedCamera.configurationState() == org.example.aisurv.contract.v1.CameraConfigurationStateV1.ENABLED ? "Disable Camera" : "Enable Camera");
            setStatus("Editing " + selectedCamera.displayName() + ". Edge monitoring updates automatically.", false);
        });
        task.setOnFailed(event -> setStatus(edgeFailureMessage(task.getException(), "Camera details could not be loaded."), true));
        backgroundTasks.start(task, "camera-detail");
    }

    private void saveCamera() {
        CameraDetailV1 camera = selectedCamera;
        if (camera == null) return;
        UpdateCameraRequestV1 request = new UpdateCameraRequestV1(camera.version(), displayName.getText(),
                rtspUrl.getText().isBlank() ? null : rtspUrl.getText(), onvifServiceUrl.getText(),
                camera.manufacturer(), camera.model(), host.getText(), location.getText(), building.getText(),
                floor.getText(), zone.getText(), priority.getValue());
        Task<CameraDetailV1> task = new Task<>() { @Override protected CameraDetailV1 call() { return edgeApiClient.updateCamera(camera.id(), request); } };
        task.setOnSucceeded(event -> { selectedCamera = task.getValue(); refreshRegisteredCameras(); setStatus("Camera changes saved and applied to edge monitoring.", false); });
        task.setOnFailed(event -> setStatus(edgeFailureMessage(task.getException(), "Camera update failed."), true));
        backgroundTasks.start(task, "camera-update");
    }

    private void toggleCamera() {
        CameraDetailV1 camera = selectedCamera;
        if (camera == null) return;
        var next = camera.configurationState() == org.example.aisurv.contract.v1.CameraConfigurationStateV1.ENABLED
                ? org.example.aisurv.contract.v1.CameraConfigurationStateV1.DISABLED
                : org.example.aisurv.contract.v1.CameraConfigurationStateV1.ENABLED;
        Task<CameraSummaryV1> task = new Task<>() { @Override protected CameraSummaryV1 call() {
            return edgeApiClient.setCameraState(camera.id(), new SetCameraConfigurationStateRequestV1(camera.version(), next)); } };
        task.setOnSucceeded(event -> { refreshRegisteredCameras(); loadCamera(camera.id()); setStatus("Camera state applied to edge monitoring.", false); });
        task.setOnFailed(event -> setStatus(edgeFailureMessage(task.getException(), "Camera state update failed."), true));
        backgroundTasks.start(task, "camera-state-update");
    }

    private void deleteCamera() {
        CameraDetailV1 camera = selectedCamera;
        if (camera == null) return;
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION, "Delete " + camera.displayName() + "?", ButtonType.OK, ButtonType.CANCEL);
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        Task<Void> task = new Task<>() { @Override protected Void call() { edgeApiClient.deleteCamera(camera.id(), camera.version()); return null; } };
        task.setOnSucceeded(event -> { clearForm(); refreshRegisteredCameras(); setStatus("Camera deleted and removed from edge monitoring.", false); });
        task.setOnFailed(event -> setStatus(edgeFailureMessage(task.getException(), "Camera deletion failed."), true));
        backgroundTasks.start(task, "camera-delete");
    }

    private String valueOrEmpty(String value) { return value == null ? "" : value; }

    private void setStatus(String message, boolean error) {
        Platform.runLater(() -> {
            status.setText(message);
            status.setStyle("-fx-text-fill: " + (error ? "#b42318" : "#667085") + ";");
        });
    }

    private String defaultName(DiscoveredCameraV1 camera) {
        String model = valueOrUnknown(camera.model());
        return "unknown".equals(model) ? "Camera " + camera.host() : model + " " + camera.host();
    }

    private String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private String cameraLocation(CameraSummaryV1 camera) {
        String locationText = Stream.of(camera.location(), camera.building(), camera.floor(), camera.zone())
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .reduce((left, right) -> left + " / " + right)
                .orElse("Location unavailable");
        return locationText + " | ID " + camera.id();
    }

    private String edgeFailureMessage(Throwable failure, String fallback) {
        if (failure instanceof IncompatibleEdgeException) return "Edge API is incompatible with this desktop version.";
        if (failure instanceof EdgeUnavailableException) return "Edge service unavailable. Start the local edge backend.";
        if (failure instanceof EdgeRequestException requestFailure) {
            if (requestFailure.statusCode() == 503) return "Edge dependency unavailable. Check PostgreSQL and edge health.";
            return requestFailure.getMessage();
        }
        return fallback;
    }
}
