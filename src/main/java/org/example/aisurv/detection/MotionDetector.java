package org.example.aisurv.detection;

import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Size;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.bytedeco.opencv.global.opencv_core.absdiff;
import static org.bytedeco.opencv.global.opencv_core.countNonZero;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2GRAY;
import static org.bytedeco.opencv.global.opencv_imgproc.GaussianBlur;
import static org.bytedeco.opencv.global.opencv_imgproc.MORPH_RECT;
import static org.bytedeco.opencv.global.opencv_imgproc.THRESH_BINARY;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;
import static org.bytedeco.opencv.global.opencv_imgproc.erode;
import static org.bytedeco.opencv.global.opencv_imgproc.getStructuringElement;
import static org.bytedeco.opencv.global.opencv_imgproc.threshold;

public class MotionDetector implements AutoCloseable {
    private Mat previousGray;
    private final Mat gray = new Mat();
    private final Mat diff = new Mat();
    private final Mat thresh = new Mat();
    private final Size blurSize = new Size(21, 21);
    private final Size kernelSize = new Size(5, 5);
    private final Mat kernel = getStructuringElement(MORPH_RECT, kernelSize);
    private final AtomicBoolean closed = new AtomicBoolean();

    public boolean detect(Mat frame) {
        if (closed.get()) {
            throw new IllegalStateException("Motion detector is closed");
        }
        if (frame == null || frame.empty()) {
            return false;
        }
        cvtColor(frame, gray, COLOR_BGR2GRAY);
        GaussianBlur(gray, gray, blurSize, 0);

        if (previousGray == null || dimensionsChanged(previousGray, gray)) {
            replacePreviousGray();
            return false;
        }

        absdiff(previousGray, gray, diff);
        threshold(diff, thresh, 40, 255, THRESH_BINARY);
        erode(thresh, thresh, kernel);
        boolean currentMotion = countNonZero(thresh) > 3000;

        replacePreviousGray();
        return currentMotion;
    }

    public void reset() {
        closePreviousGray();
    }

    private boolean dimensionsChanged(Mat previous, Mat current) {
        return previous.rows() != current.rows()
                || previous.cols() != current.cols()
                || previous.type() != current.type();
    }

    private void replacePreviousGray() {
        closePreviousGray();
        previousGray = gray.clone();
    }

    private void closePreviousGray() {
        if (previousGray != null) {
            previousGray.close();
            previousGray = null;
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        closePreviousGray();
        gray.close();
        diff.close();
        thresh.close();
        kernel.close();
        blurSize.close();
        kernelSize.close();
    }
}
