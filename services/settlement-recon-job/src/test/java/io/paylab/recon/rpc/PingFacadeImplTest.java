package io.paylab.recon.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PingFacadeImplTest {

    @Test
    void pingEchoesCaller() {
        assertEquals("settlement-recon-job:pong:e2e", new PingFacadeImpl().ping("e2e"));
    }
}
