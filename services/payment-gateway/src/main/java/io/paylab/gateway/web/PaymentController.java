package io.paylab.gateway.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.paylab.api.ledger.TrialBalanceReport;
import io.paylab.common.PayLab;
import io.paylab.gateway.idempotency.IdempotencyService;
import io.paylab.gateway.idempotency.IdempotencyService.Outcome;
import io.paylab.gateway.rpc.RpcClients;
import io.paylab.gateway.service.PaymentService;
import io.paylab.gateway.web.PaymentDtos.CreatePaymentRequest;
import io.paylab.gateway.web.PaymentDtos.PaymentEventResponse;
import io.paylab.gateway.web.PaymentDtos.PaymentResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The only public HTTP surface of PayLab. All mutating endpoints require an Idempotency-Key
 * header and run through {@link #idempotent}; reads pass straight to the service layer.
 */
@RestController
@RequestMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class PaymentController {

    public static final String REPLAY_HEADER = "X-Idempotent-Replay";

    private final PaymentService paymentService;
    private final IdempotencyService idempotency;
    private final RpcClients rpc;
    private final ObjectMapper mapper;

    public PaymentController(
            PaymentService paymentService, IdempotencyService idempotency, RpcClients rpc, ObjectMapper mapper) {
        this.paymentService = paymentService;
        this.idempotency = idempotency;
        this.rpc = rpc;
        this.mapper = mapper;
    }

    /**
     * Creates a payment (201). The request hash covers the full body, so reusing a key with
     * different payment details is rejected rather than silently replayed.
     */
    @PostMapping("/payments")
    public ResponseEntity<String> create(
            @RequestHeader(PayLab.IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {
        String hash = IdempotencyService.sha256("POST/payments|" + request);
        return idempotent(
                idempotencyKey,
                "POST/payments",
                hash,
                HttpStatus.CREATED,
                () -> PaymentResponse.from(paymentService.create(
                        request.payerId(),
                        request.merchantId(),
                        request.sourceCurrency(),
                        request.targetCurrency(),
                        request.amount())));
    }

    /** Captures a RISK_APPROVED payment (200): locks FX, posts to the ledger, sets CAPTURED. */
    @PostMapping("/payments/{id}/capture")
    public ResponseEntity<String> capture(
            @RequestHeader(PayLab.IDEMPOTENCY_KEY_HEADER) String idempotencyKey, @PathVariable String id) {
        String hash = IdempotencyService.sha256("POST/payments/" + id + "/capture");
        return idempotent(
                idempotencyKey,
                "POST/payments/capture",
                hash,
                HttpStatus.OK,
                () -> PaymentResponse.from(paymentService.capture(id)));
    }

    /** Fully refunds a CAPTURED payment (200) by reversing its ledger entry. */
    @PostMapping("/payments/{id}/refund")
    public ResponseEntity<String> refund(
            @RequestHeader(PayLab.IDEMPOTENCY_KEY_HEADER) String idempotencyKey, @PathVariable String id) {
        String hash = IdempotencyService.sha256("POST/payments/" + id + "/refund");
        return idempotent(
                idempotencyKey,
                "POST/payments/refund",
                hash,
                HttpStatus.OK,
                () -> PaymentResponse.from(paymentService.refund(id)));
    }

    /** Fetches a single payment, 404 if unknown. */
    @GetMapping("/payments/{id}")
    public PaymentResponse get(@PathVariable String id) {
        return PaymentResponse.from(paymentService.get(id));
    }

    /** Lists payments newest-first, optionally scoped to one merchant. */
    @GetMapping("/payments")
    public List<PaymentResponse> list(
            @RequestParam(required = false) String merchantId, @RequestParam(defaultValue = "50") int limit) {
        return paymentService.list(merchantId, limit).stream()
                .map(PaymentResponse::from)
                .toList();
    }

    /** Status-transition timeline for one payment (the audit trail). */
    @GetMapping("/payments/{id}/events")
    public List<PaymentEventResponse> events(@PathVariable String id) {
        return paymentService.timeline(id).stream()
                .map(PaymentEventResponse::from)
                .toList();
    }

    /** Read-only proxy to the ledger so the outside world (portal, e2e) stays REST-only. */
    @GetMapping("/trial-balance")
    public TrialBalanceReport trialBalance() {
        return rpc.ledger().trialBalance();
    }

    /**
     * Replay-or-execute wrapper around every mutating endpoint: first call runs the supplier
     * and stores the serialized response under the key; replays return the stored bytes with
     * X-Idempotent-Replay: true. On business failure the key is released so the client may
     * retry with the same key — only successes are pinned.
     */
    private ResponseEntity<String> idempotent(
            String key,
            String endpoint,
            String requestHash,
            HttpStatus successStatus,
            Supplier<PaymentResponse> action) {
        Outcome outcome = idempotency.begin(key, endpoint, requestHash);
        if (outcome instanceof Outcome.Replay replay) {
            return ResponseEntity.status(replay.httpStatus())
                    .header(REPLAY_HEADER, "true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(replay.responseBody());
        }
        try {
            PaymentResponse body = action.get();
            String json = serialize(body);
            idempotency.complete(key, successStatus.value(), json, body.id());
            return ResponseEntity.status(successStatus)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json);
        } catch (RuntimeException businessFailure) {
            idempotency.release(key);
            throw businessFailure;
        }
    }

    /** Serializes the response once so the live reply and the stored replay are byte-identical. */
    private String serialize(PaymentResponse body) {
        try {
            return mapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("response serialization failed", e);
        }
    }
}
