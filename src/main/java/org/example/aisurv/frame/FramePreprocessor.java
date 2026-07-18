package org.example.aisurv.frame;

import org.bytedeco.opencv.opencv_core.Mat;

public class FramePreprocessor {
    public Mat preprocess(Mat frame) {
        if (frame == null || frame.empty()) {
            return frame;
        }
        return frame;
    }
}
