package org.example.aisurv.camera;

@FunctionalInterface
public interface RtspConnectivityProbe {
    void validate(String rtspUrl);
}
