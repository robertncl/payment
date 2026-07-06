package io.paylab.gateway.idempotency;

import io.paylab.gateway.repo.IdempotencyRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Insert-first idempotency: the key row is claimed in its own transaction before the business
 * logic runs, so a concurrent duplicate either replays the stored response, conflicts on a
 * different body (422), or sees an in-flight marker (409).
 */
@Service
public class IdempotencyService {

    public sealed interface Outcome {
        record FirstCall() implements Outcome {}

        record Replay(int httpStatus, String responseBody) implements Outcome {}
    }

    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) {
            super(message);
        }
    }

    public static class InFlightException extends RuntimeException {
        public InFlightException(String message) {
            super(message);
        }
    }

    private final IdempotencyRepository repository;

    public IdempotencyService(IdempotencyRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outcome begin(String key, String endpoint, String requestHash) {
        Optional<IdempotencyRecord> existing = repository.findById(key);
        if (existing.isEmpty()) {
            try {
                repository.saveAndFlush(new IdempotencyRecord(key, endpoint, requestHash, Instant.now()));
                return new Outcome.FirstCall();
            } catch (DataIntegrityViolationException raced) {
                existing = repository.findById(key);
            }
        }
        IdempotencyRecord record = existing.orElseThrow();
        if (!record.getEndpoint().equals(endpoint) || !record.getRequestHash().equals(requestHash)) {
            throw new ConflictException("Idempotency-Key was already used with a different request");
        }
        if (record.getHttpStatus() == null) {
            throw new InFlightException("original request with this Idempotency-Key is still in flight");
        }
        return new Outcome.Replay(record.getHttpStatus(), record.getResponseBody());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String key, int httpStatus, String responseBody, String paymentId) {
        IdempotencyRecord record = repository.findById(key).orElseThrow();
        record.complete(httpStatus, responseBody, paymentId);
        repository.save(record);
    }

    /** On business failure the claim is released so the client may retry with the same key. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(String key) {
        repository.deleteById(key);
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
