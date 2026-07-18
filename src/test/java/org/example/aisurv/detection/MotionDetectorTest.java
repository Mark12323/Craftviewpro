package org.example.aisurv.detection;

import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.junit.jupiter.api.Test;

import static org.bytedeco.opencv.global.opencv_core.CV_8UC3;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MotionDetectorTest {
    @Test
    void resolutionChangeEstablishesANewBaseline() {
        try (MotionDetector detector = new MotionDetector();
             Mat first = image(100, 100, 0);
             Mat resized = image(120, 160, 255)) {
            assertFalse(detector.detect(first));
            assertDoesNotThrow(() -> assertFalse(detector.detect(resized)));
        }
    }

    @Test
    void resetDiscardsThePreviousStreamBaseline() {
        try (MotionDetector detector = new MotionDetector();
             Mat dark = image(100, 100, 0);
             Mat bright = image(100, 100, 255)) {
            assertFalse(detector.detect(dark));
            detector.reset();
            assertFalse(detector.detect(bright));
        }
    }

    private static Mat image(int rows, int columns, double value) {
        return new Mat(rows, columns, CV_8UC3, new Scalar(value, value, value, 0));
    }
}
