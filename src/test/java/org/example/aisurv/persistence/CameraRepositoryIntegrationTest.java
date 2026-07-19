package org.example.aisurv.persistence;

import org.example.aisurv.camera.CameraPriority;
import org.example.aisurv.camera.CameraRegistrationRequest;
import org.example.aisurv.persistence.entities.CameraEntity;
import org.example.aisurv.persistence.repositories.CameraRepository;
import org.example.aisurv.persistence.repositories.CameraHealthRepository;
import org.example.aisurv.event.CameraHealthState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfEnvironmentVariable(named = "AISURV_RUN_CONTAINER_TESTS", matches = "(?i:true)")
class CameraRepositoryIntegrationTest {
    @Test
    void migrationAndRepositoryPersistAndFilterCameras() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            DatabaseSettings settings = new DatabaseSettings(
                    postgres.getJdbcUrl(),
                    postgres.getUsername(),
                    postgres.getPassword()
            );
            new DatabaseMigrator(settings).migrate();

            try (HibernateSessionFactory hibernate = new HibernateSessionFactory(settings)) {
                CameraRepository repository = new CameraRepository(hibernate.sessionFactory());
                CameraEntity zeta = repository.save(camera("Zeta", true));
                repository.save(camera("Alpha", true));
                repository.save(camera("Disabled", false));

                assertEquals(List.of("Alpha", "Zeta"), repository.findEnabled().stream()
                        .map(CameraEntity::displayName)
                        .toList());
                assertEquals(3, repository.findAll().size());

                CameraHealthRepository health = new CameraHealthRepository(hibernate.sessionFactory());
                Instant now = Instant.now();
                health.recordState(zeta.id(), CameraHealthState.ONLINE, "STREAM_CONNECTED", now);
                health.recordFrame(zeta.id(), now.plusSeconds(1));
                health.recordState(zeta.id(), CameraHealthState.OFFLINE, "STREAM_UNAVAILABLE", now.plusSeconds(2));
                assertEquals(CameraHealthState.OFFLINE, health.findAll().get(zeta.id()).state());

                try (var session = hibernate.sessionFactory().openSession()) {
                    assertEquals(3L, session.createQuery(
                            "select count(a) from CameraConfigurationAuditEntity a", Long.class).getSingleResult());
                    assertEquals(2L, session.createQuery(
                            "select count(e) from CameraHealthEventEntity e", Long.class).getSingleResult());
                }
            }
        }
    }

    private static CameraEntity camera(String name, boolean enabled) {
        return CameraEntity.from(new CameraRegistrationRequest(
                name,
                "rtsp://camera/" + name.toLowerCase(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                CameraPriority.NORMAL,
                enabled,
                null
        ));
    }
}
