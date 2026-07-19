package org.example.aisurv.persistence;

import org.example.aisurv.persistence.entities.CameraEntity;
import org.example.aisurv.persistence.entities.CameraRuntimeHealthEntity;
import org.example.aisurv.persistence.entities.CameraHealthEventEntity;
import org.example.aisurv.persistence.entities.CameraConfigurationAuditEntity;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

public class HibernateSessionFactory implements AutoCloseable {
    private final SessionFactory sessionFactory;

    public HibernateSessionFactory(DatabaseSettings settings) {
        Configuration configuration = new Configuration();
        configuration.addAnnotatedClass(CameraEntity.class);
        configuration.addAnnotatedClass(CameraRuntimeHealthEntity.class);
        configuration.addAnnotatedClass(CameraHealthEventEntity.class);
        configuration.addAnnotatedClass(CameraConfigurationAuditEntity.class);
        configuration.setProperty("hibernate.connection.driver_class", "org.postgresql.Driver");
        configuration.setProperty("hibernate.connection.url", settings.jdbcUrl());
        configuration.setProperty("hibernate.connection.username", settings.username());
        configuration.setProperty("hibernate.connection.password", settings.password());
        configuration.setProperty("hibernate.connection.provider_class", "org.hibernate.hikaricp.internal.HikariCPConnectionProvider");
        configuration.setProperty("hibernate.hikari.maximumPoolSize", "10");
        configuration.setProperty("hibernate.hikari.minimumIdle", "1");
        configuration.setProperty("hibernate.hikari.connectionTimeout", "5000");
        configuration.setProperty("hibernate.hikari.validationTimeout", "3000");
        configuration.setProperty("hibernate.hikari.initializationFailTimeout", "5000");
        configuration.setProperty("hibernate.hbm2ddl.auto", "validate");
        configuration.setProperty("hibernate.show_sql", "false");
        this.sessionFactory = configuration.buildSessionFactory();
    }

    public SessionFactory sessionFactory() {
        return sessionFactory;
    }

    @Override
    public void close() {
        sessionFactory.close();
    }
}
