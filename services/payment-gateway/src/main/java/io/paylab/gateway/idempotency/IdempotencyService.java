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

    /** Result of {@link #begin}: either run the business logic or return the stored response. */
    public sealed interface Outcome {
        /** The key is new (or newly claimed) — the caller must execute the request. */
        record FirstCall() implements Outcome {}

        /** The key already completed — return this stored response verbatim, do not re-execute. */
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

    /**
     * Claims the idempotency key in its own committed transaction (REQUIRES_NEW) before any
     * business logic runs. Four possible outcomes: (1) key is new — the row is inserted with
     * no response yet and {@link Outcome.FirstCall} is returned; (2) the insert races a
     * concurrent duplicate — the loser falls through and is treated like an existing key;
     * (3) the key exists but was used for a different endpoint/body — {@link
     * ConflictException} (422), keys must not be reused; (4) the key exists with a stored
     * response — {@link Outcome.Replay}, or {@link InFlightException} (409) if the original
     * call has not finished yet.
     */
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

    /**
     * Stores the successful response against the key, turning future {@link #begin} calls
     * into replays. Committed in its own transaction after the business result is known.
     */
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

    /** Hex SHA-256 of the canonical request string; used to detect key reuse with a different body. */
    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
