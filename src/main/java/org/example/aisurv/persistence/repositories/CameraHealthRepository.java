package org.example.aisurv.persistence.repositories;

import org.example.aisurv.camera.CameraRuntimeHealth;
import org.example.aisurv.event.CameraHealthState;
import org.example.aisurv.persistence.entities.CameraHealthEventEntity;
import org.example.aisurv.persistence.entities.CameraRuntimeHealthEntity;
import org.hibernate.SessionFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class CameraHealthRepository {
    private final SessionFactory sessionFactory;

    public CameraHealthRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public CameraRuntimeHealth recordState(UUID cameraId, CameraHealthState state,
                                           String reasonCode, Instant occurredAt) {
        try (var session = sessionFactory.openSession()) {
            var transaction = session.beginTransaction();
            try {
                CameraRuntimeHealthEntity health = session.find(CameraRuntimeHealthEntity.class, cameraId);
                boolean changed;
                if (health == null) {
                    health = CameraRuntimeHealthEntity.initial(cameraId, state, reasonCode, occurredAt);
                    session.persist(health);
                    changed = true;
                } else {
                    String effectiveReason = state == CameraHealthState.ONLINE
                            && (health.state() == CameraHealthState.OFFLINE
                            || health.state() == CameraHealthState.FROZEN
                            || health.state() == CameraHealthState.RECONNECTING)
                            ? "RECOVERED" : reasonCode;
                    changed = health.transition(state, effectiveReason, occurredAt);
                    reasonCode = effectiveReason;
                }
                if (changed) session.persist(CameraHealthEventEntity.of(cameraId, state, reasonCode, occurredAt));
                transaction.commit();
                return health.toRecord(Instant.now());
            } catch (RuntimeException failure) {
                if (transaction.isActive()) transaction.rollback();
                throw failure;
            }
        }
    }

    public void recordFrame(UUID cameraId, Instant observedAt) {
        try (var session = sessionFactory.openSession()) {
            var transaction = session.beginTransaction();
            CameraRuntimeHealthEntity health = session.find(CameraRuntimeHealthEntity.class, cameraId);
            if (health != null) health.seen(observedAt);
            transaction.commit();
        }
    }

    public Map<UUID, CameraRuntimeHealth> findAll() {
        Instant now = Instant.now();
        try (var session = sessionFactory.openSession()) {
            List<CameraRuntimeHealthEntity> rows = session.createQuery(
                    "from CameraRuntimeHealthEntity", CameraRuntimeHealthEntity.class).list();
            return rows.stream().map(row -> row.toRecord(now))
                    .collect(Collectors.toUnmodifiableMap(CameraRuntimeHealth::cameraId, health -> health));
        }
    }
}
