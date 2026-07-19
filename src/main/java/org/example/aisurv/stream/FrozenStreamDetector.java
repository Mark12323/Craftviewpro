package org.example.aisurv.stream;

import java.time.Duration;
import java.util.function.LongSupplier;

public final class FrozenStreamDetector {
    private final long thresholdNanos;
    private final LongSupplier nanoTime;
    private long lastSourceTimestamp = Long.MIN_VALUE;
    private long stalledSinceNanos;

    public FrozenStreamDetector(Duration threshold, LongSupplier nanoTime) {
        if (threshold == null || threshold.isZero() || threshold.isNegative()) {
            throw new IllegalArgumentException("Frozen-stream threshold must be positive");
        }
        this.thresholdNanos = threshold.toNanos();
        this.nanoTime = nanoTime;
    }

    public boolean observe(long sourceTimestamp) {
        if (sourceTimestamp <= 0) return false;
        long now = nanoTime.getAsLong();
        if (sourceTimestamp != lastSourceTimestamp) {
            lastSourceTimestamp = sourceTimestamp;
            stalledSinceNanos = now;
            return false;
        }
        return now - stalledSinceNanos >= thresholdNanos;
    }

    public void reset() {
        lastSourceTimestamp = Long.MIN_VALUE;
        stalledSinceNanos = 0;
    }
}
