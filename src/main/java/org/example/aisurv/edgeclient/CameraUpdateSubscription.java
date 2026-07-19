package org.example.aisurv.edgeclient;

import org.example.aisurv.contract.v1.CameraUpdateEventV1;

import java.util.function.Consumer;

public interface CameraUpdateSubscription extends AutoCloseable {
    void consume(Consumer<CameraUpdateEventV1> consumer);

    @Override
    void close();
}
