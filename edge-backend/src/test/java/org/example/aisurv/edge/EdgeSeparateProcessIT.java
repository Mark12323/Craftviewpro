package org.example.aisurv.edge;

import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EdgeSeparateProcessIT {
    @Test
    void packagedEdgeRunsIndependentlyOnLoopback() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            port = socket.getLocalPort();
        }
        Path jar = Path.of("target", "aisurv-edge-backend-1.0-SNAPSHOT.jar").toAbsolutePath();
        String executable = System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java";
        String java = Path.of(System.getProperty("java.home"), "bin", executable).toString();
        Process process = new ProcessBuilder(
                java, "-jar", jar.toString(),
                "--server.port=" + port,
                "--aisurv.edge.database-probe-delay-ms=60000")
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
            URI healthUri = URI.create("http://127.0.0.1:" + port + "/api/v1/health");
            boolean reachable = false;
            // The fat edge JAR contains native video runtimes and can start slowly on cold Windows filesystems.
            long deadline = System.nanoTime() + Duration.ofSeconds(120).toNanos();
            while (System.nanoTime() < deadline && process.isAlive()) {
                try {
                    HttpResponse<String> response = client.send(
                            HttpRequest.newBuilder(healthUri).timeout(Duration.ofSeconds(2)).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200 && response.body().contains("\"major\":1")) {
                        reachable = true;
                        break;
                    }
                } catch (java.io.IOException ignored) {
                    Thread.sleep(200);
                }
            }
            assertTrue(reachable, "Packaged edge health endpoint did not become reachable");
            assertTrue(process.isAlive(), "Edge process must remain alive independently of API clients");
        } finally {
            process.destroy();
            if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroyForcibly();
        }
    }
}
