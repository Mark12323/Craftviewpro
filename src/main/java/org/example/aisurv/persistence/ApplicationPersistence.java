package org.example.aisurv.persistence;

import org.example.aisurv.persistence.repositories.CameraRepository;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

public final class ApplicationPersistence implements CameraRepositoryProvider, AutoCloseable {
    private final Object lock = new Object();
    private final Runnable migration;
    private final Supplier<HibernateSessionFactory> factorySupplier;
    private HibernateSessionFactory hibernate;
    private CameraRepository cameraRepository;
    private CompletableFuture<CameraRepository> initialization;
    private boolean closed;

    public ApplicationPersistence(DatabaseSettings settings) {
        Objects.requireNonNull(settings, "settings");
        this.migration = () -> new DatabaseMigrator(settings).migrate();
        this.factorySupplier = () -> new HibernateSessionFactory(settings);
    }

    ApplicationPersistence(Runnable migration, Supplier<HibernateSessionFactory> factorySupplier) {
        this.migration = Objects.requireNonNull(migration, "migration");
        this.factorySupplier = Objects.requireNonNull(factorySupplier, "factorySupplier");
    }

    @Override
    public CameraRepository cameraRepository() {
        CompletableFuture<CameraRepository> pending;
        boolean initialize = false;
        synchronized (lock) {
            ensureOpen();
            if (cameraRepository != null) {
                return cameraRepository;
            }
            if (initialization == null) {
                initialization = new CompletableFuture<>();
                initialize = true;
            }
            pending = initialization;
        }

        if (initialize) {
            initialize(pending);
        }
        return await(pending);
    }

    private void initialize(CompletableFuture<CameraRepository> pending) {
        HibernateSessionFactory candidate = null;
        try {
            migration.run();
            candidate = factorySupplier.get();
            CameraRepository repository = new CameraRepository(candidate.sessionFactory());
            synchronized (lock) {
                if (closed) {
                    candidate.close();
                    pending.completeExceptionally(new IllegalStateException("Application persistence is closed"));
                    return;
                }
                hibernate = candidate;
                cameraRepository = repository;
                initialization = null;
                pending.complete(repository);
            }
        } catch (Throwable failure) {
            if (candidate != null) {
                candidate.close();
            }
            synchronized (lock) {
                if (initialization == pending) {
                    initialization = null;
                }
            }
            pending.completeExceptionally(failure);
        }
    }

    private CameraRepository await(CompletableFuture<CameraRepository> pending) {
        try {
            return pending.join();
        } catch (CompletionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw failure;
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Application persistence is closed");
        }
    }

    @Override
    public void close() {
        HibernateSessionFactory toClose;
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            if (initialization != null) {
                initialization.completeExceptionally(new IllegalStateException("Application persistence is closed"));
            }
            toClose = hibernate;
            hibernate = null;
            cameraRepository = null;
        }
        if (toClose != null) {
            toClose.close();
        }
    }
}
