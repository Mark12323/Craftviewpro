package org.example.aisurv.edge.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EdgeBindingPolicyTest {
    @Test
    void acceptsLoopbackAndRejectsLanOrWildcardBinding() {
        assertDoesNotThrow(() -> new EdgeBindingPolicy("127.0.0.1"));
        assertDoesNotThrow(() -> new EdgeBindingPolicy("::1"));
        assertThrows(IllegalArgumentException.class, () -> new EdgeBindingPolicy("localhost"));
        assertThrows(IllegalArgumentException.class, () -> new EdgeBindingPolicy("0.0.0.0"));
        assertThrows(IllegalArgumentException.class, () -> new EdgeBindingPolicy("192.0.2.1"));
    }
}
