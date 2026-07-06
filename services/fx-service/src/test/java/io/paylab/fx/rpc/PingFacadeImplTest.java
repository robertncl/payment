package io.paylab.fx.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PingFacadeImplTest {

    @Test
    void pingEchoesCaller() {
        assertEquals("fx-service:pong:e2e", new PingFacadeImpl().ping("e2e"));
    }
}
