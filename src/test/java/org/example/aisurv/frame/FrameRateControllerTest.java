package org.example.aisurv.frame;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameRateControllerTest {
    @Test
    void processesFirstFrameAndThenUsesElapsedTime() {
        AtomicLong now = new AtomicLong();
        FrameRateController controller = new FrameRateController(Duration.ofMillis(500), now::get);

        assertTrue(controller.shouldProcessCurrentFrame());
        now.set(Duration.ofMillis(499).toNanos());
        assertFalse(controller.shouldProcessCurrentFrame());
        now.set(Duration.ofMillis(500).toNanos());
        assertTrue(controller.shouldProcessCurrentFrame());
        now.set(Duration.ofMillis(999).toNanos());
        assertFalse(controller.shouldProcessCurrentFrame());
        now.set(Duration.ofSeconds(1).toNanos());
        assertTrue(controller.shouldProcessCurrentFrame());
    }

    @Test
    void resetAllowsTheNextFrameImmediately() {
        AtomicLong now = new AtomicLong();
        FrameRateController controller = new FrameRateController(Duration.ofSeconds(1), now::get);

        assertTrue(controller.shouldProcessCurrentFrame());
        assertFalse(controller.shouldProcessCurrentFrame());
        controller.reset();
        assertTrue(controller.shouldProcessCurrentFrame());
    }

    @Test
    void rejectsNonPositiveIntervals() {
        assertThrows(IllegalArgumentException.class, () -> new FrameRateController(Duration.ZERO));
    }
}
