package org.example.aisurv.stream;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrozenStreamDetectorTest {
    @Test
    void detectsAStalledPositiveSourceTimestampAndResetsOnProgress() {
        AtomicLong time = new AtomicLong();
        FrozenStreamDetector detector = new FrozenStreamDetector(Duration.ofSeconds(30), time::get);

        assertFalse(detector.observe(100));
        time.set(Duration.ofSeconds(29).toNanos());
        assertFalse(detector.observe(100));
        time.set(Duration.ofSeconds(30).toNanos());
        assertTrue(detector.observe(100));
        assertFalse(detector.observe(101));
    }

    @Test
    void ignoresSourcesWithoutUsableTimestamps() {
        FrozenStreamDetector detector = new FrozenStreamDetector(Duration.ofSeconds(1), () -> Long.MAX_VALUE);

        assertFalse(detector.observe(0));
        assertFalse(detector.observe(-1));
    }
}
