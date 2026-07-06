package io.paylab.gateway.idempotency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.paylab.gateway.idempotency.IdempotencyService.Outcome;
import io.paylab.gateway.repo.IdempotencyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest(properties = "spring.test.database.replace=NONE")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class IdempotencyServiceTest {

    @Autowired
    private IdempotencyRepository repository;

    private IdempotencyService service() {
        return new IdempotencyService(repository);
    }

    @Test
    void firstCallThenReplay() {
        IdempotencyService service = service();
        assertInstanceOf(Outcome.FirstCall.class, service.begin("k1", "POST/x", "h1"));

        service.complete("k1", 201, "{\"id\":\"pay_1\"}", "pay_1");

        Outcome outcome = service.begin("k1", "POST/x", "h1");
        Outcome.Replay replay = assertInstanceOf(Outcome.Replay.class, outcome);
        assertEquals(201, replay.httpStatus());
        assertEquals("{\"id\":\"pay_1\"}", replay.responseBody());
    }

    @Test
    void sameKeyDifferentBodyIsConflict() {
        IdempotencyService service = service();
        service.begin("k2", "POST/x", "hash-a");
        service.complete("k2", 201, "{}", null);
        assertThrows(IdempotencyService.ConflictException.class, () -> service.begin("k2", "POST/x", "hash-b"));
    }

    @Test
    void inFlightKeyIsRejected() {
        IdempotencyService service = service();
        service.begin("k3", "POST/x", "h");
        assertThrows(IdempotencyService.InFlightException.class, () -> service.begin("k3", "POST/x", "h"));
    }

    @Test
    void releasedKeyCanBeRetried() {
        IdempotencyService service = service();
        service.begin("k4", "POST/x", "h");
        service.release("k4");
        assertInstanceOf(Outcome.FirstCall.class, service.begin("k4", "POST/x", "h"));
    }
}
