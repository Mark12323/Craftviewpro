package org.example.aisurv.camera;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OnvifCameraDiscoveryService implements CameraDiscoveryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(OnvifCameraDiscoveryService.class);
    private static final String WS_DISCOVERY_ADDRESS = "239.255.255.250";
    private static final int WS_DISCOVERY_PORT = 3702;
    private static final Pattern XADDRS_PATTERN = Pattern.compile("<[^>]*XAddrs[^>]*>(.*?)</[^>]*XAddrs>", Pattern.DOTALL);
    private static final Pattern SCOPES_PATTERN = Pattern.compile("<[^>]*Scopes[^>]*>(.*?)</[^>]*Scopes>", Pattern.DOTALL);

    @Override
    public List<DiscoveredCamera> discover(Duration timeout) {
        Map<String, DiscoveredCamera> discovered = new LinkedHashMap<>();
        byte[] probe = discoveryProbe().getBytes(StandardCharsets.UTF_8);

        try (DatagramSocket socket = new DatagramSocket()) {
            DatagramPacket packet = new DatagramPacket(
                    probe,
                    probe.length,
                    InetAddress.getByName(WS_DISCOVERY_ADDRESS),
                    WS_DISCOVERY_PORT
            );
            socket.send(packet);

            Instant deadline = Instant.now().plus(timeout);
            while (Instant.now().isBefore(deadline)) {
                long remainingMillis = Duration.between(Instant.now(), deadline).toMillis();
                if (remainingMillis <= 0) break;
                socket.setSoTimeout(Math.toIntExact(Math.min(Integer.MAX_VALUE, Math.max(1, remainingMillis))));
                byte[] buffer = new byte[8192];
                DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(response);
                    DiscoveredCamera camera = parseResponse(response);
                    if (camera != null) {
                        discovered.putIfAbsent(camera.onvifServiceUrl(), camera);
                    }
                } catch (SocketTimeoutException ignored) {
                    break;
                }
            }
        } catch (IOException | ArithmeticException e) {
            LOGGER.warn("ONVIF discovery failed: {}", e.getMessage());
            throw new IllegalStateException("ONVIF discovery could not access the local network", e);
        }

        return new ArrayList<>(discovered.values());
    }

    DiscoveredCamera parseResponse(DatagramPacket response) {
        String body = new String(response.getData(), response.getOffset(), response.getLength(), StandardCharsets.UTF_8);
        String xaddr = firstMatch(XADDRS_PATTERN, body);
        if (xaddr == null || xaddr.isBlank()) {
            return null;
        }

        String serviceUrl = xaddr.trim().split("\\s+")[0];
        URI serviceEndpoint;
        try {
            serviceEndpoint = URI.create(serviceUrl);
        } catch (IllegalArgumentException failure) {
            return null;
        }
        if (serviceEndpoint.getUserInfo() != null || serviceEndpoint.getHost() == null
                || !("http".equalsIgnoreCase(serviceEndpoint.getScheme())
                || "https".equalsIgnoreCase(serviceEndpoint.getScheme()))) return null;
        String scopes = firstMatch(SCOPES_PATTERN, body);
        String manufacturer = scopeValue(scopes, "hardware");
        String model = scopeValue(scopes, "name");

        return new DiscoveredCamera(
                UUID.nameUUIDFromBytes(serviceUrl.getBytes(StandardCharsets.UTF_8)).toString(),
                manufacturer,
                model,
                response.getAddress().getHostAddress(),
                serviceUrl,
                true,
                Instant.now()
        );
    }

    private String firstMatch(Pattern pattern, String body) {
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String scopeValue(String scopes, String key) {
        if (scopes == null || scopes.isBlank()) {
            return null;
        }
        for (String scope : scopes.split("\\s+")) {
            int index = scope.toLowerCase().lastIndexOf("/" + key + "/");
            if (index >= 0) {
                return scope.substring(index + key.length() + 2).replace('_', ' ');
            }
        }
        return null;
    }

    private String discoveryProbe() {
        String messageId = "uuid:" + UUID.randomUUID();
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <e:Envelope xmlns:e="http://www.w3.org/2003/05/soap-envelope"
                            xmlns:w="http://schemas.xmlsoap.org/ws/2004/08/addressing"
                            xmlns:d="http://schemas.xmlsoap.org/ws/2005/04/discovery"
                            xmlns:dn="http://www.onvif.org/ver10/network/wsdl">
                    <e:Header>
                        <w:MessageID>%s</w:MessageID>
                        <w:To>urn:schemas-xmlsoap-org:ws:2005:04:discovery</w:To>
                        <w:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</w:Action>
                    </e:Header>
                    <e:Body>
                        <d:Probe>
                            <d:Types>dn:NetworkVideoTransmitter</d:Types>
                        </d:Probe>
                    </e:Body>
                </e:Envelope>
                """.formatted(messageId);
    }
}
