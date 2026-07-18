package org.example.aisurv.persistence.repositories;

import org.example.aisurv.persistence.entities.CameraEntity;
import org.hibernate.SessionFactory;

import java.util.List;

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
            transaction.commit();
            return camera;
        }
    }
}
