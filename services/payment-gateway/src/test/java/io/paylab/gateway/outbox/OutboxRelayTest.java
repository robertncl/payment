package io.paylab.gateway.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.paylab.gateway.repo.OutboxRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

@DataJpaTest(properties = "spring.test.database.replace=NONE")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OutboxRelayTest {

    @Autowired
    private OutboxRepository outbox;

    private OutboxEvent pending(String eventType) {
        return outbox.save(new OutboxEvent("payment", "pay_1", eventType, "{}", Instant.now()));
    }

    @Test
    void drainMarksPendingEventsPublishedExactlyOnce() {
        OutboxEvent first = pending("payment.created");
        OutboxEvent second = pending("payment.captured");
        OutboxRelay relay = new OutboxRelay(outbox);

        relay.drain();

        assertNotNull(outbox.findById(first.getId()).orElseThrow().getPublishedAt());
        assertNotNull(outbox.findById(second.getId()).orElseThrow().getPublishedAt());
        assertTrue(outbox.findUnpublished(PageRequest.of(0, 10)).isEmpty());

        // a second tick must not touch already-published rows
        Instant publishedAt = outbox.findById(first.getId()).orElseThrow().getPublishedAt();
        relay.drain();
        assertEquals(publishedAt, outbox.findById(first.getId()).orElseThrow().getPublishedAt());
    }

    @Test
    void drainProcessesAtMostFiftyPerTick() {
        for (int i = 0; i < 51; i++) {
            pending("payment.created");
        }
        OutboxRelay relay = new OutboxRelay(outbox);

        relay.drain();
        assertEquals(1, outbox.findUnpublished(PageRequest.of(0, 100)).size());

        relay.drain();
        assertTrue(outbox.findUnpublished(PageRequest.of(0, 100)).isEmpty());
    }
}
