package org.example.aisurv.edgeclient;

import java.net.URI;

public record EdgeEndpoint(URI baseUri) {
    public EdgeEndpoint {
        if (baseUri == null || !"http".equalsIgnoreCase(baseUri.getScheme())
                || baseUri.getHost() == null || baseUri.getUserInfo() != null) {
            throw new IllegalArgumentException("AISURV_EDGE_BASE_URI must be an HTTP URI without credentials");
        }
        if (!isLoopbackLiteral(baseUri.getHost())) {
            throw new IllegalArgumentException("M1 desktop may connect only to a literal loopback edge API");
        }
    }

    public static EdgeEndpoint fromEnvironment() {
        String configured = System.getenv("AISURV_EDGE_BASE_URI");
        return new EdgeEndpoint(URI.create(
                configured == null || configured.isBlank() ? "http://127.0.0.1:8080" : configured.trim()));
    }

    URI resolve(String path) {
        return baseUri.resolve(path);
    }

    private static boolean isLoopbackLiteral(String host) {
        String normalized = host.startsWith("[") && host.endsWith("]")
                ? host.substring(1, host.length() - 1) : host;
        if ("::1".equals(normalized)) return true;
        String[] octets = normalized.split("\\.", -1);
        if (octets.length != 4 || !"127".equals(octets[0])) return false;
        for (String octet : octets) {
            try {
                int value = Integer.parseInt(octet);
                if (value < 0 || value > 255) return false;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }
}
