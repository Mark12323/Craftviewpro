package org.example.aisurv.frame;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameQueueTest {
    @Test
    void dropOldestReplacesQueuedFrameWhenFull() {
        FrameQueue queue = new FrameQueue(1, FrameDropPolicy.DROP_OLDEST);
        FramePacket oldest = frame("oldest");
        FramePacket newest = frame("newest");

        assertTrue(queue.offer(oldest));
        assertTrue(queue.offer(newest));
        assertSame(newest, queue.poll());
        assertNull(queue.poll());
    }

    @Test
    void dropNewestRejectsIncomingFrameAndKeepsQueuedFrame() {
        FrameQueue queue = new FrameQueue(1, FrameDropPolicy.DROP_NEWEST);
        FramePacket oldest = frame("oldest");

        assertTrue(queue.offer(oldest));
        assertFalse(queue.offer(frame("newest")));
        assertSame(oldest, queue.poll());
    }

    @Test
    void nonPositiveCapacityStillProvidesOneSlot() {
        FrameQueue queue = new FrameQueue(0, FrameDropPolicy.DROP_NEWEST);

        assertTrue(queue.offer(frame("first")));
        assertFalse(queue.offer(frame("second")));
    }

    private static FramePacket frame(String camera) {
        return new FramePacket(camera, Instant.EPOCH, null);
    }
}
