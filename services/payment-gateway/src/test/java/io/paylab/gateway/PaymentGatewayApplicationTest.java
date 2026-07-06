package io.paylab.gateway;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.paylab.api.PingFacade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Boots the full SOFABoot context (runtime + RPC starter) against the file-based local registry,
 * so SOFA auto-configuration breakage is caught before docker compose.
 */
@SpringBootTest
class PaymentGatewayApplicationTest {

    @Autowired
    private PingFacade pingFacade;

    @Test
    void contextLoadsAndPublishesPingFacade() {
        assertNotNull(pingFacade);
    }
}
