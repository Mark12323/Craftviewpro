package org.example.aisurv.persistence;

import org.flywaydb.core.Flyway;
import org.postgresql.ds.PGSimpleDataSource;

public class DatabaseMigrator {
    private final DatabaseSettings settings;

    public DatabaseMigrator(DatabaseSettings settings) {
        this.settings = settings;
    }

    public void migrate() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(settings.jdbcUrl());
        dataSource.setUser(settings.username());
        dataSource.setPassword(settings.password());
        dataSource.setConnectTimeout(5);
        dataSource.setSocketTimeout(30);
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }
}
