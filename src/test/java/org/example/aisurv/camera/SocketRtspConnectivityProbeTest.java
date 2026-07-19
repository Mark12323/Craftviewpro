package org.example.aisurv.camera;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class SocketRtspConnectivityProbeTest {
    @Test
    void acceptsAResponsiveRtspEndpoint() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Thread responder = Thread.ofPlatform().start(() -> {
                try (var socket = server.accept()) {
                    BufferedReader input = new BufferedReader(new InputStreamReader(
                            socket.getInputStream(), StandardCharsets.US_ASCII));
                    while (!input.readLine().isEmpty()) { }
                    socket.getOutputStream().write("RTSP/1.0 200 OK\r\nCSeq: 1\r\nContent-Length: 0\r\n\r\n"
                            .getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                } catch (Exception failure) {
                    throw new AssertionError(failure);
                }
            });

            assertDoesNotThrow(() -> new SocketRtspConnectivityProbe().validate(
                    "rtsp://127.0.0.1:" + server.getLocalPort() + "/live"));
            responder.join();
        }
    }
}
