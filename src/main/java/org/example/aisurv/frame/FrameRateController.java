package org.example.aisurv.frame;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

public class FrameRateController {
    private final long intervalNanos;
    private final LongSupplier nanoTime;
    private boolean firstFrame = true;
    private long lastProcessedAt;

    public FrameRateController(Duration interval) {
        this(interval, System::nanoTime);
    }

    FrameRateController(Duration interval, LongSupplier nanoTime) {
        Objects.requireNonNull(interval, "interval");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        if (interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("interval must be positive");
        }
        this.intervalNanos = interval.toNanos();
    }

    public boolean shouldProcessCurrentFrame() {
        long now = nanoTime.getAsLong();
        if (firstFrame || now - lastProcessedAt >= intervalNanos) {
            firstFrame = false;
            lastProcessedAt = now;
            return true;
        }
        return false;
    }

    public void reset() {
        firstFrame = true;
    }
}
