package org.example.aisurv.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class BackgroundTaskTracker {
    private static final Logger LOGGER = LoggerFactory.getLogger(BackgroundTaskTracker.class);

    private final Map<Thread, Runnable> tasks = new LinkedHashMap<>();
    private boolean stopRequested;

    public synchronized Thread start(Runnable task, String threadName) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(threadName, "threadName");
        if (stopRequested) {
            throw new IllegalStateException("Background task shutdown has started");
        }

        Thread thread = new Thread(() -> {
            try {
                task.run();
            } finally {
                synchronized (BackgroundTaskTracker.this) {
                    tasks.remove(Thread.currentThread());
                }
            }
        }, threadName);
        thread.setDaemon(true);
        tasks.put(thread, task);
        try {
            thread.start();
        } catch (RuntimeException | Error failure) {
            tasks.remove(thread);
            throw failure;
        }
        return thread;
    }

    public void requestStop() {
        List<Map.Entry<Thread, Runnable>> snapshot;
        synchronized (this) {
            stopRequested = true;
            snapshot = new ArrayList<>(tasks.entrySet());
        }
        for (Map.Entry<Thread, Runnable> entry : snapshot) {
            if (entry.getValue() instanceof Future<?> future) {
                future.cancel(true);
            }
            entry.getKey().interrupt();
        }
    }

    public void awaitUntil(long deadlineNanos) {
        List<Thread> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(tasks.keySet());
        }

        boolean interrupted = false;
        for (Thread thread : snapshot) {
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                break;
            }
            long millis = TimeUnit.NANOSECONDS.toMillis(remainingNanos);
            int nanos = (int) (remainingNanos - TimeUnit.MILLISECONDS.toNanos(millis));
            try {
                thread.join(millis, nanos);
            } catch (InterruptedException e) {
                interrupted = true;
                break;
            }
        }

        for (Thread thread : snapshot) {
            if (thread.isAlive()) {
                LOGGER.warn("Background task {} remains active after the shutdown deadline", thread.getName());
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    public synchronized int activeTaskCount() {
        return (int) tasks.keySet().stream().filter(Thread::isAlive).count();
    }
}
