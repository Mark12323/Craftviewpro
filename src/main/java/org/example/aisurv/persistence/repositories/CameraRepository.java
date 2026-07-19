package org.example.aisurv.persistence.repositories;

import org.example.aisurv.persistence.entities.CameraEntity;
import org.example.aisurv.persistence.entities.CameraConfigurationAuditEntity;
import org.example.aisurv.camera.CameraAuditAction;
import org.hibernate.SessionFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.example.aisurv.camera.CameraNotFoundException;
import org.example.aisurv.camera.CameraVersionConflictException;

public class CameraRepository {
    private final SessionFactory sessionFactory;

    public CameraRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public List<CameraEntity> findAll() {
        try (var session = sessionFactory.openSession()) {
            return session.createQuery("from CameraEntity order by displayName", CameraEntity.class).list();
        }
    }

    public List<CameraEntity> findEnabled() {
        try (var session = sessionFactory.openSession()) {
            return session.createQuery(
                    "from CameraEntity where enabled = true order by displayName",
                    CameraEntity.class
            ).list();
        }
    }

    public CameraEntity save(CameraEntity camera) {
        try (var session = sessionFactory.openSession()) {
            var transaction = session.beginTransaction();
            session.persist(camera);
            session.flush();
            session.persist(CameraConfigurationAuditEntity.of(
                    camera.id(), CameraAuditAction.REGISTERED, null, camera.version()));
            transaction.commit();
            return camera;
        }
    }

    public Optional<CameraEntity> findById(UUID id) {
        try (var session = sessionFactory.openSession()) {
            return Optional.ofNullable(session.find(CameraEntity.class, id));
        }
    }

    public boolean duplicateName(String name, UUID excludedId) {
        try (var session = sessionFactory.openSession()) {
            return session.createQuery("select count(c) from CameraEntity c where lower(trim(c.displayName)) = :name and c.id <> :id", Long.class)
                    .setParameter("name", name.trim().toLowerCase(java.util.Locale.ROOT))
                    .setParameter("id", excludedId == null ? new UUID(0, 0) : excludedId)
                    .getSingleResult() > 0;
        }
    }

    public boolean duplicateStream(String url, UUID excludedId) {
        try (var session = sessionFactory.openSession()) {
            return session.createQuery("select count(c) from CameraEntity c where trim(c.rtspUrl) = :url and c.id <> :id", Long.class)
                    .setParameter("url", url.trim())
                    .setParameter("id", excludedId == null ? new UUID(0, 0) : excludedId)
                    .getSingleResult() > 0;
        }
    }

    public CameraEntity update(UUID id, long expectedVersion, CameraAuditAction action,
                               Consumer<CameraEntity> mutation) {
        try (var session = sessionFactory.openSession()) {
            var transaction = session.beginTransaction();
            try {
                CameraEntity camera = session.find(CameraEntity.class, id);
                if (camera == null) throw new CameraNotFoundException();
                if (camera.version() != expectedVersion) throw new CameraVersionConflictException();
                mutation.accept(camera);
                session.flush();
                session.persist(CameraConfigurationAuditEntity.of(
                        id, action, expectedVersion, camera.version()));
                transaction.commit();
                return camera;
            } catch (RuntimeException failure) {
                if (transaction.isActive()) transaction.rollback();
                if (optimisticConflict(failure)) throw new CameraVersionConflictException();
                throw failure;
            }
        }
    }

    public void delete(UUID id, long expectedVersion) {
        try (var session = sessionFactory.openSession()) {
            var tx = session.beginTransaction();
            try {
                CameraEntity camera = session.find(CameraEntity.class, id);
                if (camera == null) throw new CameraNotFoundException();
                if (camera.version() != expectedVersion) throw new CameraVersionConflictException();
                session.persist(CameraConfigurationAuditEntity.of(
                        id, CameraAuditAction.DELETED, expectedVersion, null));
                session.remove(camera);
                session.flush();
                tx.commit();
            } catch (RuntimeException failure) {
                if (tx.isActive()) tx.rollback();
                if (optimisticConflict(failure)) throw new CameraVersionConflictException();
                throw failure;
            }
        }
    }

    private static boolean optimisticConflict(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof jakarta.persistence.OptimisticLockException
                    || cause instanceof org.hibernate.StaleStateException) return true;
        }
        return false;
    }
}
