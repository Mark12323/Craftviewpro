package org.example.aisurv.detection;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.bytedeco.opencv.global.opencv_core.BORDER_CONSTANT;
import static org.bytedeco.opencv.global.opencv_core.copyMakeBorder;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2RGB;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;
import static org.bytedeco.opencv.global.opencv_imgproc.resize;

public class PersonDetector implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(PersonDetector.class);
    private static final int INPUT_SIZE = 640;
    private static final float CONFIDENCE_THRESHOLD = 0.55f;
    private static final float NMS_THRESHOLD = 0.35f;
    private static final int PERSON_CLASS_ID = 0;

    private final OrtEnvironment env;
    private final OrtSession session;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicReference<OrtSession.RunOptions> activeRun =
            new java.util.concurrent.atomic.AtomicReference<>();

    public PersonDetector(String modelPath) throws OrtException {
        this.env = OrtEnvironment.getEnvironment();
        try (OrtSession.SessionOptions opts = new OrtSession.SessionOptions()) {
            opts.setIntraOpNumThreads(2);
            this.session = env.createSession(modelPath, opts);
        }
    }

    public static class BoundingBox {
        public final float x;
        public final float y;
        public final float width;
        public final float height;
        public final float confidence;

        public BoundingBox(float x, float y, float width, float height, float confidence) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.confidence = confidence;
        }
    }

    public static class DetectionException extends RuntimeException {
        public DetectionException(String message) {
            super(message);
        }

        public DetectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public List<BoundingBox> detect(Mat frame) {
        if (frame == null || frame.empty()) {
            return List.of();
        }
        if (closed.get()) {
            throw new DetectionException("Person detector is closed");
        }
        int origW = frame.cols();
        int origH = frame.rows();
        Preprocessed prep = preprocess(frame);

        try (OnnxTensor inputTensor = OnnxTensor.createTensor(
                env, FloatBuffer.wrap(prep.data), new long[]{1, 3, INPUT_SIZE, INPUT_SIZE});
             OrtSession.RunOptions runOptions = new OrtSession.RunOptions()) {
            activeRun.set(runOptions);
            if (closed.get()) {
                cancel(runOptions);
            }
            try (OrtSession.Result result = session.run(Map.of("images", inputTensor), runOptions)) {
                Optional<OnnxValue> outputOptional = result.get("output0");
                if (outputOptional.isEmpty()) {
                    throw new DetectionException("Person detector output is missing");
                }
                if (!(outputOptional.get() instanceof OnnxTensor outputTensor)) {
                    throw new DetectionException("Person detector output has an unsupported type");
                }
                long[] shape = outputTensor.getInfo().getShape();
                Object value = outputTensor.getValue();
                if (!(value instanceof float[][][] raw) || shape.length != 3 || raw.length == 0) {
                    throw new DetectionException("Person detector output has an unsupported shape");
                }
                float[][] output = raw[0];
                int dim1 = (int) shape[1];
                int dim2 = (int) shape[2];
                if (dim1 == 84) {
                    return postprocess84xN(output, prep.scale, prep.padX, prep.padY, origW, origH);
                } else if (dim2 == 84) {
                    return postprocessNx84(output, prep.scale, prep.padX, prep.padY, origW, origH);
                }
                throw new DetectionException("Unsupported person detector output shape: " + Arrays.toString(shape));
            } finally {
                activeRun.compareAndSet(runOptions, null);
            }
        } catch (OrtException e) {
            throw new DetectionException("Person inference failed", e);
        }
    }

    public void cancelActiveInference() {
        OrtSession.RunOptions runOptions = activeRun.get();
        if (runOptions != null) {
            cancel(runOptions);
        }
    }

    private void cancel(OrtSession.RunOptions runOptions) {
        try {
            runOptions.setTerminate(true);
        } catch (OrtException e) {
            LOGGER.debug("Unable to cancel active person inference", e);
        }
    }

    private static class Preprocessed {
        final float[] data;
        final float scale;
        final int padX;
        final int padY;

        Preprocessed(float[] data, float scale, int padX, int padY) {
            this.data = data;
            this.scale = scale;
            this.padX = padX;
            this.padY = padY;
        }
    }

    private Preprocessed preprocess(Mat frame) {
        int origW = frame.cols();
        int origH = frame.rows();
        float scale = Math.min((float) INPUT_SIZE / origW, (float) INPUT_SIZE / origH);
        int newW = Math.round(origW * scale);
        int newH = Math.round(origH * scale);
        int padX = (INPUT_SIZE - newW) / 2;
        int padY = (INPUT_SIZE - newH) / 2;
        int right = INPUT_SIZE - newW - padX;
        int bottom = INPUT_SIZE - newH - padY;

        Mat resized = new Mat();
        Mat rgb = new Mat();
        Mat padded = new Mat();
        try {
            resize(frame, resized, new Size(newW, newH));
            cvtColor(resized, rgb, COLOR_BGR2RGB);
            copyMakeBorder(rgb, padded, padY, bottom, padX, right, BORDER_CONSTANT,
                    new Scalar(114, 114, 114, 0));
            padded.convertTo(padded, org.bytedeco.opencv.global.opencv_core.CV_32FC3);

            float[] data = new float[3 * INPUT_SIZE * INPUT_SIZE];
            for (int y = 0; y < INPUT_SIZE; y++) {
                for (int x = 0; x < INPUT_SIZE; x++) {
                    int pixelIndex = y * INPUT_SIZE + x;
                    float r = padded.ptr(y, x).getFloat(0);
                    float g = padded.ptr(y, x).getFloat(1);
                    float b = padded.ptr(y, x).getFloat(2);
                    data[pixelIndex] = r / 255f;
                    data[INPUT_SIZE * INPUT_SIZE + pixelIndex] = g / 255f;
                    data[2 * INPUT_SIZE * INPUT_SIZE + pixelIndex] = b / 255f;
                }
            }
            return new Preprocessed(data, scale, padX, padY);
        } finally {
            resized.close();
            rgb.close();
            padded.close();
        }
    }

    private List<BoundingBox> postprocess84xN(float[][] output, float scale, int padX, int padY, int origW, int origH) {
        List<BoundingBox> candidates = new ArrayList<>();
        int numPredictions = output[0].length;
        for (int i = 0; i < numPredictions; i++) {
            float cx = output[0][i];
            float cy = output[1][i];
            float w = output[2][i];
            float h = output[3][i];
            float maxScore = 0f;
            int bestClass = -1;
            for (int c = 0; c < 80; c++) {
                float score = output[4 + c][i];
                if (score > maxScore) {
                    maxScore = score;
                    bestClass = c;
                }
            }
            if (bestClass == PERSON_CLASS_ID && maxScore >= CONFIDENCE_THRESHOLD) {
                BoundingBox box = convertAndClampBox(cx, cy, w, h, scale, padX, padY, origW, origH, maxScore);
                if (box != null) {
                    candidates.add(box);
                }
            }
        }
        return nms(candidates);
    }

    private List<BoundingBox> postprocessNx84(float[][] output, float scale, int padX, int padY, int origW, int origH) {
        List<BoundingBox> candidates = new ArrayList<>();
        for (float[] prediction : output) {
            float cx = prediction[0];
            float cy = prediction[1];
            float w = prediction[2];
            float h = prediction[3];
            float maxScore = 0f;
            int bestClass = -1;
            for (int c = 0; c < 80; c++) {
                float score = normalizeScore(prediction[4 + c]);
                if (score > maxScore) {
                    maxScore = score;
                    bestClass = c;
                }
            }
            if (bestClass == PERSON_CLASS_ID && maxScore >= CONFIDENCE_THRESHOLD) {
                BoundingBox box = convertAndClampBox(cx, cy, w, h, scale, padX, padY, origW, origH, maxScore);
                if (box != null) {
                    candidates.add(box);
                }
            }
        }
        return nms(candidates);
    }

    private BoundingBox convertAndClampBox(float cx, float cy, float w, float h, float scale, int padX, int padY, int origW, int origH, float confidence) {
        float x = (cx - w / 2f - padX) / scale;
        float y = (cy - h / 2f - padY) / scale;
        float bw = w / scale;
        float bh = h / scale;
        float x1 = Math.max(0, x);
        float y1 = Math.max(0, y);
        float x2 = Math.min(origW, x + bw);
        float y2 = Math.min(origH, y + bh);
        float finalW = x2 - x1;
        float finalH = y2 - y1;
        if (finalW <= 0 || finalH <= 0) {
            return null;
        }
        return new BoundingBox(x1, y1, finalW, finalH, confidence);
    }

    private List<BoundingBox> nms(List<BoundingBox> boxes) {
        boxes.sort((a, b) -> Float.compare(b.confidence, a.confidence));
        List<BoundingBox> kept = new ArrayList<>();
        boolean[] removed = new boolean[boxes.size()];
        for (int i = 0; i < boxes.size(); i++) {
            if (removed[i]) {
                continue;
            }
            BoundingBox current = boxes.get(i);
            kept.add(current);
            for (int j = i + 1; j < boxes.size(); j++) {
                if (!removed[j] && iou(current, boxes.get(j)) > NMS_THRESHOLD) {
                    removed[j] = true;
                }
            }
        }
        return kept;
    }

    private float iou(BoundingBox a, BoundingBox b) {
        float interX = Math.max(0, Math.min(a.x + a.width, b.x + b.width) - Math.max(a.x, b.x));
        float interY = Math.max(0, Math.min(a.y + a.height, b.y + b.height) - Math.max(a.y, b.y));
        float intersection = interX * interY;
        float union = a.width * a.height + b.width * b.height - intersection;
        return union > 0 ? intersection / union : 0f;
    }

    private float normalizeScore(float score) {
        if (score >= 0f && score <= 1f) {
            return score;
        }
        return sigmoid(score);
    }

    private float sigmoid(float x) {
        return (float) (1.0 / (1.0 + Math.exp(-x)));
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            cancelActiveInference();
            try {
                session.close();
            } catch (OrtException e) {
                LOGGER.warn("Unable to close person detector session", e);
            }
        }
    }
}
