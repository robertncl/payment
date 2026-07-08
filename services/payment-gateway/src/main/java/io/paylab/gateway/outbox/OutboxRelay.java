package io.paylab.gateway.outbox;

import io.paylab.gateway.repo.OutboxRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * DB-polling relay (spec allows this for MVP). "Publishing" is a structured log line until a
 * broker exists; consumers (recon, portal feeds) arrive in Phase 3. At-least-once by design.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger("paylab.outbox");

    private final OutboxRepository outbox;

    public OutboxRelay(OutboxRepository outbox) {
        this.outbox = outbox;
    }

    /**
     * Polls up to 50 unpublished outbox rows every second, "publishes" each (a log line for
     * now), and marks them published. Publish and mark share one transaction, so a crash
     * mid-batch re-delivers the whole batch on the next tick — consumers must deduplicate.
     */
    @Scheduled(fixedDelayString = "${paylab.outbox.poll-ms:1000}")
    @Transactional
    public void drain() {
        List<OutboxEvent> batch = outbox.findUnpublished(PageRequest.of(0, 50));
        Instant now = Instant.now();
        for (OutboxEvent event : batch) {
            log.info(
                    "outbox-publish type={} aggregate={}/{} payload={}",
                    event.getEventType(),
                    event.getAggregateType(),
                    event.getAggregateId(),
                    event.getPayload());
            event.markPublished(now);
        }
        if (!batch.isEmpty()) {
            outbox.saveAll(batch);
        }
    }
}
