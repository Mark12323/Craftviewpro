package org.example.aisurv.camera;

import org.example.aisurv.persistence.DatabaseMigrator;
import org.example.aisurv.persistence.DatabaseSettings;
import org.example.aisurv.persistence.HibernateSessionFactory;
import org.example.aisurv.persistence.repositories.CameraRepository;

import java.time.Duration;

public class CameraRegistryTool {
    public static void main(String[] args) {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            printUsage();
            return;
        }

        switch (args[0].toLowerCase()) {
            case "discover" -> discover();
            case "register" -> register(args);
            default -> printUsage();
        }
    }

    private static void discover() {
        CameraDiscoveryService discoveryService = new OnvifCameraDiscoveryService();
        var cameras = discoveryService.discover(Duration.ofSeconds(5));
        if (cameras.isEmpty()) {
            System.out.println("No ONVIF cameras discovered.");
            return;
        }

        for (DiscoveredCamera camera : cameras) {
            System.out.printf(
                    "Device: %s | Host: %s | Manufacturer: %s | Model: %s | ONVIF: %s%n",
                    camera.deviceId(),
                    camera.host(),
                    valueOrUnknown(camera.manufacturer()),
                    valueOrUnknown(camera.model()),
                    camera.onvifServiceUrl()
            );
        }
    }

    private static void register(String[] args) {
        if (args.length < 3) {
            printUsage();
            return;
        }

        String displayName = args[1];
        String rtspUrl = args[2];
        CameraPriority priority = args.length >= 4 ? CameraPriority.valueOf(args[3].toUpperCase()) : CameraPriority.NORMAL;

        DatabaseSettings settings = DatabaseSettings.fromEnvironment();
        new DatabaseMigrator(settings).migrate();
        try (HibernateSessionFactory hibernate = new HibernateSessionFactory(settings)) {
            CameraRepository repository = new CameraRepository(hibernate.sessionFactory());
            CameraRegistrationService registrationService = new CameraRegistrationService(repository);
            var camera = registrationService.register(new CameraRegistrationRequest(
                    displayName,
                    rtspUrl,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    priority,
                    true,
                    null
            ));
            System.out.printf("Registered camera %s with id %s%n", camera.displayName(), camera.id());
        }
    }

    private static String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private static void printUsage() {
        System.out.println("AISurv camera registry tool");
        System.out.println("Usage:");
        System.out.println("  discover");
        System.out.println("  register <displayName> <rtspUrl> [CRITICAL|HIGH|NORMAL|LOW]");
    }
}
