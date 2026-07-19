package org.example.aisurv.contract.v1;

public record ApiVersionV1(int major, int minor) {
    public static final ApiVersionV1 CURRENT = new ApiVersionV1(1, 3);

    public boolean isSupportedByV1Client() {
        return major == CURRENT.major;
    }
}
