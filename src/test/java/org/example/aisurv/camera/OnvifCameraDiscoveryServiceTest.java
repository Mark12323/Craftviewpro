package org.example.aisurv.camera;

import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnvifCameraDiscoveryServiceTest {
    private final OnvifCameraDiscoveryService discovery = new OnvifCameraDiscoveryService();

    @Test
    void parsesServiceUrlHostAndScopes() throws Exception {
        String xml = """
                <Envelope><ProbeMatch>
                  <XAddrs>http://10.20.1.18/onvif/device_service http://backup/onvif</XAddrs>
                  <Scopes>onvif://www.onvif.org/hardware/QNO-8080R onvif://www.onvif.org/name/North_Perimeter</Scopes>
                </ProbeMatch></Envelope>
                """;

        DiscoveredCamera camera = discovery.parseResponse(packet(xml, "10.20.1.18"));

        assertEquals("http://10.20.1.18/onvif/device_service", camera.onvifServiceUrl());
        assertEquals("10.20.1.18", camera.host());
        assertEquals("QNO-8080R", camera.manufacturer());
        assertEquals("North Perimeter", camera.model());
        assertTrue(camera.requiresAuthentication());
    }

    @Test
    void ignoresResponsesWithoutServiceAddress() throws Exception {
        assertNull(discovery.parseResponse(packet("<Envelope><Scopes>camera</Scopes></Envelope>", "10.0.0.2")));
    }

    private DatagramPacket packet(String body, String address) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return new DatagramPacket(bytes, bytes.length, InetAddress.getByName(address), 3702);
    }
}
