package com.aegisvision.cctv;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Popup;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.awt.image.BufferedImage;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.example.aisurv.app.BackgroundTaskTracker;
import org.example.aisurv.edgeclient.EdgeApiClient;
import org.example.aisurv.edgeclient.EdgeConnectionSnapshot;
import org.example.aisurv.contract.v1.CameraSummaryV1;
import org.example.aisurv.gui.CameraManagementPage;

public final class DashboardView extends BorderPane {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private final ObservableList<EventItem> events = FXCollections.observableArrayList();
    private final ObservableList<CameraItem> cameras = FXCollections.observableArrayList();
    private final Map<String, ImageView> cameraViews = new HashMap<>();
    private final Map<String, Label> cameraStatusLabels = new HashMap<>();
    private final Map<String, Image> latestFrames = new HashMap<>();
    private final Map<String, String> cameraHealth = new HashMap<>();
    private final FrameCoalescer frameCoalescer = new FrameCoalescer(Platform::runLater, this::displayFrame);
    private final BackgroundTaskTracker backgroundTasks;
    private final EdgeApiClient edgeApiClient;
    private String edgeConnection = "Connecting";
    private String focusedCameraName;
    private ImageView focusedCameraView;
    private volatile boolean disposed;

    private final Popup navigationPopup = new Popup();
    private final Button menuButton = new Button();
    private final Label currentPage = new Label();
    private final Label applicationStatus = new Label("Initializing services");
    private final Label edgeConnectionStatus = new Label("Edge: connecting");
    private final Circle applicationStatusIndicator = new Circle(3);
    private final StackPane contentHost = new StackPane();
    private final Map<Page, Button> navigationButtons = new EnumMap<>(Page.class);
    private boolean navigationOpen;
    private Page activePage;

    public DashboardView(String[] cameraNames, BackgroundTaskTracker backgroundTasks,
                         EdgeApiClient edgeApiClient) {
        this.backgroundTasks = Objects.requireNonNull(backgroundTasks, "backgroundTasks");
        this.edgeApiClient = Objects.requireNonNull(edgeApiClient, "edgeApiClient");
        for (String cameraName : cameraNames) {
            cameras.add(runtimeCamera(cameraName));
            cameraHealth.put(cameraName, "Waiting");
        }
        getStyleClass().add("app-shell");
        configureNavigationPopup();
        setTop(createTopBar());
        setCenter(contentHost);
        setBottom(createStatusBar());
        showPage(Page.DASHBOARD);
    }

    private CameraItem runtimeCamera(String name) {
        return new CameraItem(name, "Host unavailable", "Zone unavailable", "Waiting");
    }

    public void setConfiguredCameras(String[] cameraNames) {
        applyConfiguredCameras(cameraNames, true);
    }

    public void updateConfiguredCameras(String[] cameraNames) {
        applyConfiguredCameras(cameraNames, false);
    }

    public void updateConfiguredCameras(List<CameraSummaryV1> configuredCameras) {
        runOnFxThread(() -> {
            String[] names = configuredCameras.stream().map(CameraSummaryV1::displayName).toArray(String[]::new);
            var configuredNames = new HashSet<>(List.of(names));
            cameras.clear();
            cameraHealth.keySet().retainAll(configuredNames);
            latestFrames.keySet().retainAll(configuredNames);
            for (CameraSummaryV1 camera : configuredCameras) {
                String health = camera.runtimeHealth() == null
                        ? "Unknown" : displayName(camera.runtimeHealth().state().name());
                cameras.add(new CameraItem(camera.displayName(),
                        camera.location() == null ? "Location unavailable" : camera.location(),
                        camera.zone() == null ? "Zone unavailable" : camera.zone(), health));
                cameraHealth.put(camera.displayName(), health);
                if ("Offline".equals(health) || "Frozen".equals(health)
                        || "Stopped".equals(health) || "Disabled".equals(health)) {
                    latestFrames.remove(camera.displayName());
                }
            }
            if (activePage == Page.DASHBOARD || activePage == Page.LIVE_MONITOR) showPage(activePage);
        });
    }

    private void applyConfiguredCameras(String[] cameraNames, boolean showDashboard) {
        runOnFxThread(() -> {
            var configuredNames = new HashSet<>(java.util.List.of(cameraNames));
            cameras.clear();
            cameraHealth.keySet().retainAll(configuredNames);
            latestFrames.keySet().retainAll(configuredNames);
            for (String cameraName : cameraNames) {
                cameras.add(runtimeCamera(cameraName));
                cameraHealth.putIfAbsent(cameraName, "Waiting");
            }
            if (showDashboard) showPage(Page.DASHBOARD);
            else if (activePage == Page.DASHBOARD || activePage == Page.LIVE_MONITOR) showPage(activePage);
        });
    }

    public void setApplicationStatus(String message, boolean degraded) {
        runOnFxThread(() -> {
            applicationStatus.setText(message + " | Cameras configured: " + cameras.size());
            applicationStatusIndicator.getStyleClass().removeAll("status-neutral", "status-healthy", "status-degraded");
            applicationStatusIndicator.getStyleClass().add(degraded ? "status-degraded" : "status-healthy");
        });
    }

    public void setEdgeConnection(EdgeConnectionSnapshot snapshot) {
        runOnFxThread(() -> {
            edgeConnection = snapshot.message();
            edgeConnectionStatus.setText("Edge: " + snapshot.state().name().toLowerCase());
            edgeConnectionStatus.setAccessibleText(snapshot.message());
        });
    }

    public void updateFrame(String cameraName, BufferedImage frame) {
        frameCoalescer.submit(cameraName, frame);
    }

    private void displayFrame(String cameraName, BufferedImage frame) {
        Image image = SwingFXUtils.toFXImage(frame, null);
        latestFrames.put(cameraName, image);
        ImageView view = cameraViews.get(cameraName);
        if (view != null) view.setImage(image);
        if (cameraName.equals(focusedCameraName) && focusedCameraView != null) {
            focusedCameraView.setImage(image);
        }
    }

    private void runOnFxThread(Runnable action) {
        if (disposed) return;
        Runnable guardedAction = () -> {
            if (!disposed) action.run();
        };
        if (Platform.isFxApplicationThread()) guardedAction.run();
        else Platform.runLater(guardedAction);
    }

    private String displayName(String enumName) {
        String normalized = enumName.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private void configureNavigationPopup() {
        ScrollPane scroll = new ScrollPane(createNavigation());
        scroll.setFitToWidth(true);
        scroll.setPrefViewportWidth(278);
        scroll.setPrefViewportHeight(500);
        scroll.getStyleClass().add("navigation-scroll");
        navigationPopup.getContent().add(scroll);
        navigationPopup.setAutoFix(true);
        navigationPopup.setAutoHide(true);
        navigationPopup.setHideOnEscape(true);
        navigationPopup.setOnHidden(event -> {
            navigationOpen = false;
            menuButton.setAccessibleText("Open navigation menu");
        });
    }

    private Node createNavigation() {
        ImageView mark = new ImageView(new Image(Objects.requireNonNull(DashboardView.class.getResource(
                "/com/aegisvision/cctv/icons/app/CraftView-AppIcon-48.png"),
                "Missing CraftView navigation logo").toExternalForm()));
        mark.setFitWidth(38);
        mark.setFitHeight(38);
        mark.setPreserveRatio(true);
        mark.setSmooth(true);
        mark.getStyleClass().add("app-mark");
        Label name = new Label("CraftView");
        name.getStyleClass().add("brand-name");
        Label subtitle = new Label("ADVANCED AI SURVEILLANCE SYSTEM");
        subtitle.getStyleClass().add("brand-edition");
        HBox brand = new HBox(10, mark, new VBox(0, name, subtitle));
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.getStyleClass().add("brand");

        VBox operations = navGroup("OPERATIONS",
                Page.DASHBOARD, Page.LIVE_MONITOR, Page.EVENT_LOG, Page.INCIDENT_REVIEW);
        VBox configuration = navGroup("CONFIGURATION",
                Page.CAMERA_MANAGEMENT, Page.ZONE_MANAGEMENT, Page.AI_SETTINGS,
                Page.NOTIFICATION_SETTINGS, Page.FACE_REGISTRY);
        VBox administration = navGroup("ADMINISTRATION",
                Page.REPORTS_ANALYTICS, Page.AUDIT_LOG, Page.USERS_ROLES, Page.SYSTEM_HEALTH);

        VBox navigation = new VBox(14, brand, operations, configuration, administration);
        navigation.getStyleClass().add("navigation");
        return navigation;
    }

    private VBox navGroup(String title, Page... pages) {
        Label heading = new Label(title);
        heading.getStyleClass().add("nav-section-label");
        VBox group = new VBox(3, heading);
        for (Page page : pages) {
            group.getChildren().add(navButton(page));
        }
        return group;
    }

    private Button navButton(Page page) {
        StackPane icon = new StackPane(SvgIcon.load(page.icon, 16));
        icon.getStyleClass().add("nav-icon");
        Label text = new Label(page.title);
        HBox graphic = new HBox(11, icon, text);
        graphic.setAlignment(Pos.CENTER_LEFT);
        Button button = new Button();
        button.setGraphic(graphic);
        button.setMaxWidth(Double.MAX_VALUE);
        button.getStyleClass().add("nav-button");
        button.setAccessibleText(page.title);
        button.setOnAction(event -> {
            showPage(page);
            setNavigationOpen(false);
        });
        navigationButtons.put(page, button);
        return button;
    }

    private Node createTopBar() {
        menuButton.setGraphic(SvgIcon.load("menu", 19));
        menuButton.setTooltip(new Tooltip("Open navigation menu"));
        menuButton.setAccessibleText("Open navigation menu");
        menuButton.getStyleClass().add("menu-button");
        menuButton.setOnAction(event -> setNavigationOpen(!navigationOpen));

        Label appName = new Label("CraftView");
        appName.getStyleClass().add("top-brand-name");
        Label divider = new Label("/");
        divider.getStyleClass().add("top-divider");
        currentPage.getStyleClass().add("top-page-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label scope = new Label("Live system");
        scope.getStyleClass().add("prototype-label");
        HBox top = new HBox(12, menuButton, appName, divider, currentPage, spacer, scope);
        top.setAlignment(Pos.CENTER_LEFT);
        top.getStyleClass().add("top-bar");
        return top;
    }

    private Node createStatusBar() {
        applicationStatusIndicator.getStyleClass().add("status-neutral");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label product = new Label("CraftView desktop control centre");
        HBox bar = new HBox(8, applicationStatusIndicator, applicationStatus, spacer, edgeConnectionStatus, product);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("status-bar");
        return bar;
    }

    private void showPage(Page page) {
        activePage = page;
        currentPage.setText(page.title);
        navigationButtons.values().forEach(button -> button.getStyleClass().remove("selected"));
        Button selected = navigationButtons.get(page);
        if (selected != null) selected.getStyleClass().add("selected");
        Node content = switch (page) {
            case DASHBOARD -> dashboardPage();
            case LIVE_MONITOR -> liveMonitorPage();
            case EVENT_LOG -> eventLogPage();
            case INCIDENT_REVIEW -> incidentReviewPage();
            case CAMERA_MANAGEMENT -> new CameraManagementPage(backgroundTasks, edgeApiClient).root();
            case ZONE_MANAGEMENT -> zoneManagementPage();
            case AI_SETTINGS -> aiSettingsPage();
            case NOTIFICATION_SETTINGS -> notificationSettingsPage();
            case FACE_REGISTRY -> faceRegistryPage();
            case REPORTS_ANALYTICS -> reportsAnalyticsPage();
            case AUDIT_LOG -> auditLogPage();
            case USERS_ROLES -> usersRolesPage();
            case SYSTEM_HEALTH -> systemHealthPage();
        };
        contentHost.getChildren().setAll(content);
    }

    private Node dashboardPage() {
        FlowPane summary = flow(
                metricCard("LIVE EVENTS", Integer.toString(events.size()), "Current event feed", "accent"),
                metricCard("CAMERAS", Integer.toString(cameras.size()), "Configured and enabled", "success"),
                metricCard("INCIDENT TRACKING", "Unavailable", "Persistence and acknowledgement are scheduled for M2", "warning")
        );

        FlowPane severity = flow(
                severityCard("LOW", Long.toString(severityCount("LOW")), "low"),
                severityCard("MEDIUM", Long.toString(severityCount("MEDIUM")), "medium"),
                severityCard("HIGH", Long.toString(severityCount("HIGH")), "high"),
                severityCard("CRITICAL", Long.toString(severityCount("CRITICAL")), "critical")
        );

        TableView<EventItem> recent = eventTable(events);
        recent.setPrefHeight(260);

        FlowPane operational = flow(
                detailCard("PROCESSING LOAD", "Not measured", "Metrics are scheduled for M5"),
                detailCard("NOTIFICATIONS", "Not configured", "Notification delivery is scheduled for M4"),
                detailCard("CAMERA HEALTH", cameraHealthSummary(), "Current in-memory worker state")
        );
        return page("Dashboard", "Live values are shown where implemented; unavailable capabilities are labelled explicitly.",
                summary, section("Severity distribution", severity), section("Recent event feed", recent), operational);
    }

    private long severityCount(String severity) {
        String label = displayName(severity);
        return events.stream().filter(event -> label.equals(event.severity)).count();
    }

    private String cameraHealthSummary() {
        long online = cameraHealth.values().stream().filter("Online"::equals).count();
        long impaired = cameraHealth.values().stream()
                .filter(state -> "Impaired".equals(state) || "Reconnecting".equals(state))
                .count();
        return online + " online / " + impaired + " impaired";
    }

    private Node liveMonitorPage() {
        cameraViews.clear();
        cameraStatusLabels.clear();
        String initialCamera = cameras.isEmpty() ? "No cameras configured" : cameras.getFirst().name;
        Label focusedName = new Label(initialCamera);
        focusedName.getStyleClass().add("focused-camera-name");
        Label focusedMeta = muted(cameras.isEmpty()
                ? "Register a camera in Camera Management"
                : "Waiting for live stream");
        Label previewText = new Label("SELECTED HIGH-RESOLUTION FEED");
        previewText.getStyleClass().add("preview-label");
        ImageView focusedView = feedImageView();
        if (!cameras.isEmpty()) focusedView.setImage(latestFrames.get(initialCamera));
        focusedCameraName = initialCamera;
        focusedCameraView = focusedView;
        StackPane focusedFeed = new StackPane(previewText, focusedView);
        focusedFeed.getStyleClass().add("focused-feed");
        focusedFeed.setMinWidth(0);
        focusedFeed.setMinHeight(0);
        focusedFeed.setPrefWidth(800);
        focusedFeed.setMaxWidth(900);
        focusedFeed.prefHeightProperty().bind(focusedFeed.widthProperty().multiply(0.8));
        VBox focus = section("Focused live feed", focusedName, focusedMeta, focusedFeed);

        ComboBox<String> group = new ComboBox<>(FXCollections.observableArrayList("All cameras"));
        group.getSelectionModel().selectFirst();
        Label paging = muted("Page 1 of 1");
        HBox controls = new HBox(8, new Label("Camera group"), group, new Region(), paging);
        HBox.setHgrow(controls.getChildren().get(2), Priority.ALWAYS);
        controls.setAlignment(Pos.CENTER_LEFT);

        TilePane cameraGrid = new TilePane(10, 10);
        cameraGrid.setPrefColumns(3);
        cameraGrid.setPrefTileWidth(250);
        cameraGrid.setPrefTileHeight(200);
        cameraGrid.setTileAlignment(Pos.CENTER_LEFT);
        cameraGrid.getStyleClass().add("camera-grid");
        for (CameraItem camera : cameras) {
            Button tile = cameraThumbnail(camera);
            tile.setOnAction(event -> {
                focusedName.setText(camera.name);
                String health = cameraHealth.getOrDefault(camera.name, "Waiting");
                focusedMeta.setText(camera.host + "  |  " + camera.zone + "  |  " + health);
                focusedView.setImage(latestFrames.get(camera.name));
                focusedCameraName = camera.name;
                previewText.setText("SELECTED HIGH-RESOLUTION FEED - " + health.toUpperCase());
            });
            cameraGrid.getChildren().add(tile);
        }
        return page("Live Monitor", "Selected feed and reduced-rate camera thumbnails.",
                focus, section("Camera grid", controls, cameraGrid));
    }

    private Node eventLogPage() {
        TableView<EventItem> table = eventTable(events);
        table.setPrefHeight(520);
        return page("Event Log", "Structured surveillance events from the current session.",
                section("Event records", table),
                governed("Persistent history, selection-based incident review, and acknowledgement are scheduled for M2."));
    }

    private Node incidentReviewPage() {
        return unavailablePage("Incident Review", "Review an incident, its context and approved evidence.",
                "Persistent incidents and acknowledgement are scheduled for M2; evidence is scheduled for M3.");
    }

    private Node zoneManagementPage() {
        return unavailablePage("Zone Management", "Manage location hierarchy and zone-aware policies.",
                "Zone persistence and policy editing are scheduled for M2.");
    }

    private Node aiSettingsPage() {
        return unavailablePage("AI Settings", "Configure models, thresholds, and governed intelligence features.",
                "The current motion and person thresholds are prototype constants. Configurable settings are scheduled for M2 and M5.");
    }

    private Node notificationSettingsPage() {
        return unavailablePage("Notification Settings", "Rules, subscriptions, devices, and delivery status.",
                "Notifications and secure QR device pairing are scheduled for M4.");
    }

    private Node faceRegistryPage() {
        Label disabled = governed("Face intelligence is disabled and no zones are approved for this capability.");
        TableView<String[]> profiles = stringTable(List.of(), "Profile reference", "Approved use case", "Status");
        profiles.setPlaceholder(new Label("Registry unavailable while capability is disabled or not authorised"));
        Button change = new Button("Modify biometric profile");
        change.setGraphic(SvgIcon.load("face-registry", 16));
        change.getStyleClass().add("action-button");
        change.setDisable(true);
        Label outcomes = muted("Recognition outcomes remain cautious: Known match, Unknown, Watchlist candidate, Insufficient confidence, Not visible, Not required, Not authorised.");
        return page("Face Registry", "Governed biometric registry access and capability state.",
                disabled, section("Registry profiles", profiles, change), outcomes,
                governed("Future profile access and biometric profile changes will require permissions and auditing."));
    }

    private Node reportsAnalyticsPage() {
        FlowPane analytics = flow(
                metricCard("SESSION EVENTS", Integer.toString(events.size()), "Current in-memory session", "accent"),
                metricCard("CONFIGURED CAMERAS", Integer.toString(cameras.size()), "Current runtime configuration", "success"),
                metricCard("PERSISTED REPORTS", "Unavailable", "Scheduled for M5", "warning")
        );
        return page("Reports and Analytics", "Only current-session values are available during M0.", analytics,
                governed("Persistent analytics, PDF/CSV generation, and historical reports are scheduled for M5."));
    }

    private Node auditLogPage() {
        return unavailablePage("Audit Log", "Read-only records of security-sensitive actions.",
                "Persistent audit logging is scheduled for M3.");
    }

    private Node usersRolesPage() {
        return unavailablePage("Users and Roles", "Users, roles, permissions, and session control.",
                "Authentication and role-based access control are scheduled for M3.");
    }

    private Node systemHealthPage() {
        FlowPane metrics = flow(
                detailCard("CONFIGURED CAMERAS", Integer.toString(cameras.size()), "Current runtime configuration"),
                detailCard("CAMERA HEALTH", cameraHealthSummary(), "Current in-memory worker state"),
                detailCard("DATABASE / STARTUP", applicationStatus.getText(), "Current bootstrap status"),
                detailCard("EDGE API", edgeConnection, "Versioned loopback service boundary"),
                detailCard("PROCESSING METRICS", "Not measured", "Instrumentation is scheduled for M5"),
                detailCard("REDIS", "Not integrated", "Integration is scheduled for M1"),
                detailCard("EVIDENCE STORAGE", "Not integrated", "Integration is scheduled for M3")
        );
        return page("System Health", "Real current state is shown where available.", metrics);
    }

    private Node unavailablePage(String title, String subtitle, String detail) {
        return page(title, subtitle, governed("Not available in M0. " + detail));
    }

    private TableView<EventItem> eventTable(ObservableList<EventItem> items) {
        TableView<EventItem> table = new TableView<>(items);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().setAll(List.of(
                column("Time", item -> item.time, 65), column("Event type", item -> item.type, 170),
                column("Severity", item -> item.severity, 80), column("Camera", item -> item.camera, 90),
                column("Message", item -> item.message, 260)
        ));
        return table;
    }

    private <T> TableColumn<T, String> column(String title, Function<T, String> value, double width) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new ReadOnlyStringWrapper(value.apply(data.getValue())));
        column.setPrefWidth(width);
        column.setReorderable(false);
        return column;
    }

    private TableView<String[]> stringTable(List<String[]> rows, String... headings) {
        ObservableList<String[]> items = FXCollections.observableArrayList(rows);
        TableView<String[]> table = new TableView<>(items);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        for (int index = 0; index < headings.length; index++) {
            int valueIndex = index;
            table.getColumns().add(column(headings[index], row -> row[valueIndex], 130));
        }
        table.setPrefHeight(Math.max(150, Math.min(340, rows.size() * 42 + 48)));
        return table;
    }

    private ScrollPane page(String title, String subtitle, Node... nodes) {
        Label pageTitle = new Label(title);
        pageTitle.getStyleClass().add("page-title");
        Label pageSubtitle = muted(subtitle);
        pageSubtitle.getStyleClass().add("page-subtitle");
        VBox body = new VBox(17, new VBox(3, pageTitle, pageSubtitle));
        body.getChildren().addAll(nodes);
        body.getStyleClass().add("content");
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setPannable(true);
        scroll.getStyleClass().add("content-scroll");
        return scroll;
    }

    private VBox section(String title, Node... nodes) {
        Label heading = new Label(title);
        heading.getStyleClass().add("section-title");
        VBox section = new VBox(11, heading);
        section.getChildren().addAll(nodes);
        section.getStyleClass().add("section-card");
        return section;
    }

    private FlowPane flow(Node... nodes) {
        FlowPane pane = new FlowPane(12, 12, nodes);
        pane.setPrefWrapLength(950);
        return pane;
    }

    private VBox metricCard(String label, String value, String detail, String tone) {
        Label heading = new Label(label);
        heading.getStyleClass().add("metric-label");
        Label number = new Label(value);
        number.getStyleClass().add("metric-value");
        Label context = muted(detail);
        context.setWrapText(true);
        VBox card = new VBox(4, heading, number, context);
        card.setPrefWidth(270);
        card.getStyleClass().add("metric-card");
        return card;
    }

    private VBox severityCard(String label, String value, String tone) {
        Label heading = new Label(label);
        heading.getStyleClass().add("metric-label");
        Label number = new Label(value);
        number.getStyleClass().add("severity-value");
        VBox card = new VBox(5, heading, number);
        card.setPrefWidth(180);
        card.getStyleClass().add("severity-card");
        return card;
    }

    private VBox detailCard(String title, String value, String detail) {
        Label heading = new Label(title);
        heading.getStyleClass().add("metric-label");
        Label main = new Label(value);
        main.getStyleClass().add("detail-value");
        Label supporting = muted(detail);
        supporting.setWrapText(true);
        VBox card = new VBox(5, heading, main, supporting);
        card.setPrefWidth(285);
        card.getStyleClass().add("detail-card");
        return card;
    }

    private Button cameraThumbnail(CameraItem camera) {
        Label name = new Label(camera.name);
        name.getStyleClass().add("feed-name");
        Label metadata = muted(camera.host + "  |  " + camera.zone);
        String health = cameraHealth.getOrDefault(camera.name, camera.health);
        Label status = new Label(health.toUpperCase());
        status.getStyleClass().addAll("status-chip", health.toLowerCase());
        cameraStatusLabels.put(camera.name, status);
        String stateIcon = switch (health) {
            case "Impaired", "Reconnecting", "Connecting" -> "camera-impaired";
            case "Offline", "Frozen", "Stopped", "Disabled", "Unknown" -> "camera-offline";
            default -> "camera-online";
        };
        HBox state = new HBox(6, SvgIcon.load(stateIcon, 15), status);
        state.setAlignment(Pos.CENTER_LEFT);
        ImageView imageView = feedImageView();
        imageView.setFitWidth(225);
        imageView.setFitHeight(105);
        imageView.setImage(latestFrames.get(camera.name));
        cameraViews.put(camera.name, imageView);
        StackPane preview = new StackPane(imageView);
        preview.getStyleClass().add("camera-preview");
        VBox graphic = new VBox(8, preview, state, name, metadata);
        Button tile = new Button();
        tile.setGraphic(graphic);
        tile.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        tile.setAlignment(Pos.BOTTOM_LEFT);
        tile.getStyleClass().add("camera-tile");
        return tile;
    }

    private ImageView feedImageView() {
        ImageView view = new ImageView();
        view.setPreserveRatio(true);
        view.setSmooth(true);
        view.setFitWidth(720);
        view.setFitHeight(480);
        return view;
    }

    private Label governed(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add("governance-notice");
        return label;
    }

    private Label muted(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("muted");
        return label;
    }

    private void setNavigationOpen(boolean open) {
        if (!open) {
            navigationPopup.hide();
            navigationOpen = false;
            menuButton.setAccessibleText("Open navigation menu");
            return;
        }
        Bounds anchor = menuButton.localToScreen(menuButton.getBoundsInLocal());
        if (anchor == null) return;
        navigationOpen = true;
        menuButton.setAccessibleText("Close navigation menu");
        navigationPopup.show(menuButton, anchor.getMinX(), anchor.getMaxY() + 7);
    }

    public void dispose() {
        disposed = true;
        frameCoalescer.dispose();
        navigationPopup.hide();
    }

    static final class FrameCoalescer {
        private final Object lock = new Object();
        private final Consumer<Runnable> scheduler;
        private final BiConsumer<String, BufferedImage> frameConsumer;
        private final Map<String, BufferedImage> pendingFrames = new HashMap<>();
        private boolean drainScheduled;
        private volatile boolean disposed;

        FrameCoalescer(Consumer<Runnable> scheduler, BiConsumer<String, BufferedImage> frameConsumer) {
            this.scheduler = scheduler;
            this.frameConsumer = frameConsumer;
        }

        void submit(String cameraName, BufferedImage frame) {
            if (frame == null) return;

            boolean scheduleDrain = false;
            synchronized (lock) {
                if (disposed) return;
                pendingFrames.put(cameraName, frame);
                if (!drainScheduled) {
                    drainScheduled = true;
                    scheduleDrain = true;
                }
            }
            if (scheduleDrain) scheduleDrain();
        }

        void dispose() {
            synchronized (lock) {
                disposed = true;
                pendingFrames.clear();
                drainScheduled = false;
            }
        }

        private void drain() {
            Map<String, BufferedImage> frames;
            synchronized (lock) {
                if (disposed) {
                    drainScheduled = false;
                    return;
                }
                frames = new HashMap<>(pendingFrames);
                pendingFrames.clear();
            }

            try {
                for (Map.Entry<String, BufferedImage> entry : frames.entrySet()) {
                    if (disposed) break;
                    frameConsumer.accept(entry.getKey(), entry.getValue());
                }
            } finally {
                boolean scheduleDrain = false;
                synchronized (lock) {
                    if (disposed) {
                        pendingFrames.clear();
                        drainScheduled = false;
                    } else {
                        scheduleDrain = !pendingFrames.isEmpty();
                        if (!scheduleDrain) drainScheduled = false;
                    }
                }
                if (scheduleDrain) scheduleDrain();
            }
        }

        private void scheduleDrain() {
            try {
                scheduler.accept(this::drain);
            } catch (RuntimeException | Error failure) {
                synchronized (lock) {
                    pendingFrames.clear();
                    drainScheduled = false;
                }
                throw failure;
            }
        }
    }

    private enum Page {
        DASHBOARD("dashboard", "Dashboard"),
        LIVE_MONITOR("live-monitor", "Live Monitor"),
        EVENT_LOG("event-log", "Event Log"),
        INCIDENT_REVIEW("incident-review", "Incident Review"),
        CAMERA_MANAGEMENT("camera-management", "Camera Management"),
        ZONE_MANAGEMENT("zone-management", "Zone Management"),
        AI_SETTINGS("ai-settings", "AI Settings"),
        NOTIFICATION_SETTINGS("notification-settings", "Notification Settings"),
        FACE_REGISTRY("face-registry", "Face Registry"),
        REPORTS_ANALYTICS("reports-analytics", "Reports and Analytics"),
        AUDIT_LOG("audit-log", "Audit Log"),
        USERS_ROLES("users-roles", "Users and Roles"),
        SYSTEM_HEALTH("system-health", "System Health");

        private final String icon;
        private final String title;

        Page(String icon, String title) {
            this.icon = icon;
            this.title = title;
        }
    }

    private static final class EventItem {
        private final String type;
        private final String severity;
        private final String time;
        private final String camera;
        private final String message;

        private EventItem(String type, String severity, String time, String camera, String message) {
            this.type = type;
            this.severity = severity;
            this.time = time;
            this.camera = camera;
            this.message = message;
        }
    }

    private record CameraItem(String name, String host, String zone, String health) {
    }

}
