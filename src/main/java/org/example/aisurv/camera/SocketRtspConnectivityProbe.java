package org.example.aisurv.camera;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public final class SocketRtspConnectivityProbe implements RtspConnectivityProbe {
    private static final int TIMEOUT_MILLIS = 3_000;

    @Override
    public void validate(String rtspUrl) {
        URI endpoint = URI.create(rtspUrl);
        int port = endpoint.getPort() > 0 ? endpoint.getPort()
                : "rtsps".equalsIgnoreCase(endpoint.getScheme()) ? 322 : 554;
        SocketFactory factory = "rtsps".equalsIgnoreCase(endpoint.getScheme())
                ? SSLSocketFactory.getDefault() : SocketFactory.getDefault();
        try (Socket socket = factory.createSocket()) {
            socket.connect(new InetSocketAddress(endpoint.getHost(), port), TIMEOUT_MILLIS);
            socket.setSoTimeout(TIMEOUT_MILLIS);
            if (socket instanceof SSLSocket tls) {
                var parameters = tls.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                tls.setSSLParameters(parameters);
                tls.startHandshake();
            }
            OutputStreamWriter output = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII);
            output.write("DESCRIBE " + endpoint.toASCIIString()
                    + " RTSP/1.0\r\nCSeq: 1\r\nAccept: application/sdp\r\nUser-Agent: AISurv-Edge\r\n\r\n");
            output.flush();
            String status = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.US_ASCII)).readLine();
            if (status == null || !status.startsWith("RTSP/1.0 2")) {
                throw new IllegalArgumentException("Camera stream validation failed: RTSP endpoint is not ready");
            }
        } catch (IllegalArgumentException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalArgumentException("Camera stream validation failed: endpoint is unavailable");
        }
    }
}
