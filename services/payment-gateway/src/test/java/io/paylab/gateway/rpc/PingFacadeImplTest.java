package io.paylab.gateway.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PingFacadeImplTest {

    @Test
    void pingEchoesCaller() {
        assertEquals("payment-gateway:pong:e2e", new PingFacadeImpl().ping("e2e"));
    }
}
