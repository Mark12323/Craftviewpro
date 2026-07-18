package org.example.aisurv.persistence;

import org.example.aisurv.persistence.repositories.CameraRepository;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplicationPersistenceTest {
    @Test
    void reusesOneFactoryAndClosesItOnce() {
        AtomicInteger migrations = new AtomicInteger();
        HibernateSessionFactory hibernate = hibernateFactory();
        ApplicationPersistence persistence = new ApplicationPersistence(migrations::incrementAndGet, () -> hibernate);

        CameraRepository first = persistence.cameraRepository();
        CameraRepository second = persistence.cameraRepository();

        assertSame(first, second);
        assertEquals(1, migrations.get());
        persistence.close();
        persistence.close();
        verify(hibernate).close();
    }

    @Test
    void retriesInitializationAfterFailure() {
        AtomicInteger attempts = new AtomicInteger();
        HibernateSessionFactory hibernate = hibernateFactory();
        ApplicationPersistence persistence = new ApplicationPersistence(() -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("database unavailable");
            }
        }, () -> hibernate);

        assertThrows(IllegalStateException.class, persistence::cameraRepository);
        persistence.cameraRepository();

        assertEquals(2, attempts.get());
        persistence.close();
        verify(hibernate).close();
    }

    @Test
    void closesFactoryThatFinishesInitializationAfterShutdown() throws Exception {
        HibernateSessionFactory hibernate = hibernateFactory();
        CountDownLatch factoryRequested = new CountDownLatch(1);
        CountDownLatch allowFactory = new CountDownLatch(1);
        CountDownLatch factoryClosed = new CountDownLatch(1);
        doAnswer(invocation -> {
            factoryClosed.countDown();
            return null;
        }).when(hibernate).close();
        ApplicationPersistence persistence = new ApplicationPersistence(() -> {
        }, () -> {
            factoryRequested.countDown();
            await(allowFactory);
            return hibernate;
        });

        CompletableFuture<CameraRepository> initialization = CompletableFuture.supplyAsync(persistence::cameraRepository);
        assertTrue(factoryRequested.await(Duration.ofSeconds(2).toMillis(), TimeUnit.MILLISECONDS));
        persistence.close();
        allowFactory.countDown();

        assertThrows(RuntimeException.class, initialization::join);
        assertTrue(factoryClosed.await(Duration.ofSeconds(2).toMillis(), TimeUnit.MILLISECONDS));
        assertThrows(IllegalStateException.class, persistence::cameraRepository);
    }

    private static HibernateSessionFactory hibernateFactory() {
        HibernateSessionFactory hibernate = mock(HibernateSessionFactory.class);
        when(hibernate.sessionFactory()).thenReturn(mock(SessionFactory.class));
        return hibernate;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while awaiting test latch", e);
        }
    }
}
