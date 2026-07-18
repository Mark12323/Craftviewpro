package org.example.aisurv.frame;

import org.bytedeco.opencv.opencv_core.Mat;

import java.time.Instant;

public class FramePacket implements AutoCloseable {
    private final String cameraName;
    private final Instant capturedAt;
    private final Mat frame;

    public FramePacket(String cameraName, Instant capturedAt, Mat frame) {
        this.cameraName = cameraName;
        this.capturedAt = capturedAt;
        this.frame = frame;
    }

    public String cameraName() {
        return cameraName;
    }

    public Instant capturedAt() {
        return capturedAt;
    }

    public Mat frame() {
        return frame;
    }

    @Override
    public void close() {
        if (frame != null) {
            frame.close();
        }
    }
}
