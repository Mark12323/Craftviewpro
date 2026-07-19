package org.example.aisurv.edge.config;

public final class EdgeBindingPolicy {
    public EdgeBindingPolicy(String bindAddress) {
        if (bindAddress == null || bindAddress.isBlank()) {
            throw new IllegalArgumentException("Edge bind address must not be blank");
        }
        if (!isLoopbackLiteral(bindAddress)) {
            throw new IllegalArgumentException(
                    "M1 edge API must bind to a literal loopback address until transport security is implemented");
        }
    }

    private static boolean isLoopbackLiteral(String address) {
        String normalized = address.trim();
        if ("::1".equals(normalized) || "[::1]".equals(normalized)) return true;
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
