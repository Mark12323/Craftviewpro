package org.example.aisurv.frame;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class FrameQueue {
    private final BlockingQueue<FramePacket> queue;
    private final FrameDropPolicy dropPolicy;

    public FrameQueue(int capacity, FrameDropPolicy dropPolicy) {
        this.queue = new ArrayBlockingQueue<>(Math.max(1, capacity));
        this.dropPolicy = dropPolicy;
    }

    public boolean offer(FramePacket framePacket) {
        if (queue.offer(framePacket)) {
            return true;
        }

        if (dropPolicy == FrameDropPolicy.DROP_OLDEST) {
            close(queue.poll());
            return queue.offer(framePacket);
        }

        return false;
    }

    public FramePacket poll() {
        return queue.poll();
    }

    public void clear() {
        FramePacket packet;
        while ((packet = queue.poll()) != null) {
            close(packet);
        }
    }

    private static void close(FramePacket packet) {
        if (packet != null) {
            packet.close();
        }
    }
}
