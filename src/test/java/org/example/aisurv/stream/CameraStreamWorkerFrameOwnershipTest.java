package org.example.aisurv.stream;

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.Mat;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class CameraStreamWorkerFrameOwnershipTest {
    @Test
    void closingOwnedMatsDoesNotCloseTheConvertersCachedMat() {
        try (Frame frame = new Frame(32, 24, Frame.DEPTH_UBYTE, 3);
             OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat()) {
            try (Mat first = CameraStreamWorker.copyFrameForProcessing(frame, converter)) {
                assertFalse(first.empty());
            }
            try (Mat second = CameraStreamWorker.copyFrameForProcessing(frame, converter)) {
                assertFalse(second.empty());
            }
        }
    }

    @Test
    void displayFramesDoNotReuseTheConvertersMutableBuffer() {
        try (Frame frame = new Frame(32, 24, Frame.DEPTH_UBYTE, 3);
             Java2DFrameConverter converter = new Java2DFrameConverter()) {
            BufferedImage first = CameraStreamWorker.copyDisplayFrame(frame, converter);
            BufferedImage second = CameraStreamWorker.copyDisplayFrame(frame, converter);

            assertNotSame(first, second);
        }
    }
}
