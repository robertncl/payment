package io.paylab.risk.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PingFacadeImplTest {

    @Test
    void pingEchoesCaller() {
        assertEquals("risk-service:pong:e2e", new PingFacadeImpl().ping("e2e"));
    }
}
