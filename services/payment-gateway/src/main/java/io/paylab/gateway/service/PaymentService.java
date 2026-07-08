package io.paylab.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.paylab.api.fx.FxQuote;
import io.paylab.api.fx.LockQuoteRequest;
import io.paylab.api.ledger.CapturePostingCommand;
import io.paylab.common.Money;
import io.paylab.gateway.domain.Payment;
import io.paylab.gateway.domain.PaymentEvent;
import io.paylab.gateway.domain.PaymentStateMachine;
import io.paylab.gateway.domain.PaymentStatus;
import io.paylab.gateway.outbox.OutboxEvent;
import io.paylab.gateway.repo.OutboxRepository;
import io.paylab.gateway.repo.PaymentEventRepository;
import io.paylab.gateway.repo.PaymentRepository;
import io.paylab.gateway.rpc.RpcClients;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Payment lifecycle orchestration. Pre-Seata (ADR-0002): RPC side effects are idempotent on
 * the callee (ledger entries keyed by payment+type) and run BEFORE the local state commit,
 * so a crash between the two is healed by client retry, not by distributed transaction.
 * Phase 2 replaces this ordering with a Seata AT scope.
 */
@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final PaymentEventRepository events;
    private final OutboxRepository outbox;
    private final RpcClients rpc;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;

    public PaymentService(
            PaymentRepository payments,
            PaymentEventRepository events,
            OutboxRepository outbox,
            RpcClients rpc,
            TransactionTemplate tx,
            ObjectMapper mapper) {
        this.payments = payments;
        this.events = events;
        this.outbox = outbox;
        this.rpc = rpc;
        this.tx = tx;
        this.mapper = mapper;
    }

    /**
     * Step 1 of the lifecycle: validates the request (supported ISO currencies, cross-border
     * only, positive DECIMAL(20,4) amount), then in a single local transaction persists the
     * payment in CREATED, auto-approves it to RISK_APPROVED (Phase 1 risk stub), records both
     * transitions as {@link PaymentEvent} rows, and enqueues a {@code payment.created} outbox
     * event. The fee is fixed at creation time by {@link FeePolicy}; no FX or ledger work
     * happens yet — money only moves at capture.
     */
    public Payment create(
            String payerId, String merchantId, String sourceCurrency, String targetCurrency, BigDecimal amount) {
        Money.requireSupportedCurrency(sourceCurrency);
        Money.requireSupportedCurrency(targetCurrency);
        if (sourceCurrency.equals(targetCurrency)) {
            throw new IllegalArgumentException("source and target currency must differ (cross-border only)");
        }
        Money.requireValidAmount(amount);

        return tx.execute(status -> {
            Instant now = Instant.now();
            Payment payment = new Payment(
                    "pay_" + UUID.randomUUID(),
                    payerId,
                    merchantId,
                    sourceCurrency,
                    targetCurrency,
                    Money.round4(amount),
                    FeePolicy.feeFor(amount),
                    now);
            events.save(new PaymentEvent(payment.getId(), null, PaymentStatus.CREATED, null, now));

            // Phase 1 stub: risk-service is wired in Phase 2; approve unconditionally.
            // Transition BEFORE the first save: persist-then-modify in one tx loses the
            // status update when the IDENTITY event inserts force early execution of the
            // payment INSERT (observed on OceanBase; the events above keep the full trail).
            payment.transitionTo(PaymentStatus.RISK_APPROVED, now);
            events.save(new PaymentEvent(
                    payment.getId(),
                    PaymentStatus.CREATED,
                    PaymentStatus.RISK_APPROVED,
                    "risk stub: auto-approve (Phase 2 wires risk-service)",
                    now));

            enqueue("payment.created", payment);
            return payments.save(payment);
        });
    }

    /**
     * Step 2: moves the money. Runs in three phases, deliberately in this order (ADR-0002):
     * <ol>
     *   <li>Guard — reject the capture up front if the payment is not in a state that allows
     *       RISK_APPROVED → CAPTURED, before any remote side effect.</li>
     *   <li>Remote side effects — lock a 60s FX quote (captures always reference a locked
     *       quote, never a live rate), compute the target amount from it, and post the
     *       double-entry capture to the ledger. The ledger call is idempotent per
     *       (paymentId, CAPTURE), so a crash after this point is healed by client retry.</li>
     *   <li>Local commit — re-read the payment inside a transaction (a concurrent capture may
     *       have won the race; if so return it as-is), transition to CAPTURED, attach the FX
     *       details, append the audit event, and enqueue {@code payment.captured}.</li>
     * </ol>
     */
    public Payment capture(String paymentId) {
        Payment payment = get(paymentId);
        // reject illegal transitions before any side effect
        PaymentStateMachine.assertTransition(payment.getStatus(), PaymentStatus.CAPTURED);

        // capture must reference a locked quote — never a live rate
        FxQuote quote = rpc.fx()
                .lockQuote(new LockQuoteRequest(
                        payment.getSourceCurrency(), payment.getTargetCurrency(), payment.getAmount()));
        BigDecimal targetAmount = Money.round4(payment.getAmount().multiply(quote.getRate()));

        CapturePostingCommand cmd = new CapturePostingCommand();
        cmd.setPaymentId(payment.getId());
        cmd.setPayerId(payment.getPayerId());
        cmd.setMerchantId(payment.getMerchantId());
        cmd.setSourceCurrency(payment.getSourceCurrency());
        cmd.setTargetCurrency(payment.getTargetCurrency());
        cmd.setAmount(payment.getAmount());
        cmd.setFeeAmount(payment.getFeeAmount());
        cmd.setFxRate(quote.getRate());
        cmd.setFxQuoteId(quote.getQuoteId());
        cmd.setTargetAmount(targetAmount);
        rpc.ledger().postCapture(cmd);

        return tx.execute(status -> {
            Payment fresh = get(paymentId);
            if (fresh.getStatus() == PaymentStatus.CAPTURED) {
                return fresh; // lost a benign race; ledger side was idempotent
            }
            Instant now = Instant.now();
            fresh.transitionTo(PaymentStatus.CAPTURED, now);
            fresh.attachCapture(quote.getQuoteId(), quote.getRate(), targetAmount);
            events.save(new PaymentEvent(
                    fresh.getId(),
                    PaymentStatus.RISK_APPROVED,
                    PaymentStatus.CAPTURED,
                    "fx " + quote.getRate() + " (" + quote.getQuoteId() + ")",
                    now));
            enqueue("payment.captured", fresh);
            return payments.save(fresh);
        });
    }

    /**
     * Full refund of a CAPTURED payment. Same ordering as {@link #capture}: the ledger posts
     * the reversal first (it re-derives the reversed legs from the original CAPTURE entry, so
     * no amounts are passed), then the local CAPTURED → REFUNDED transition commits. Both
     * sides are idempotent, so a retry after a mid-flight crash converges.
     */
    public Payment refund(String paymentId) {
        Payment payment = get(paymentId);
        PaymentStateMachine.assertTransition(payment.getStatus(), PaymentStatus.REFUNDED);

        rpc.ledger().postRefund(paymentId);

        return tx.execute(status -> {
            Payment fresh = get(paymentId);
            if (fresh.getStatus() == PaymentStatus.REFUNDED) {
                return fresh;
            }
            Instant now = Instant.now();
            fresh.transitionTo(PaymentStatus.REFUNDED, now);
            events.save(new PaymentEvent(
                    fresh.getId(),
                    PaymentStatus.CAPTURED,
                    PaymentStatus.REFUNDED,
                    "full refund; ledger reversal posted",
                    now));
            enqueue("payment.refunded", fresh);
            return payments.save(fresh);
        });
    }

    /** Loads a payment or translates absence into an HTTP 404. */
    public Payment get(String paymentId) {
        return payments.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "payment not found: " + paymentId));
    }

    /** Newest-first listing, optionally filtered by merchant; limit is clamped to [1, 200]. */
    public List<Payment> list(String merchantId, int limit) {
        PageRequest page = PageRequest.of(0, Math.min(Math.max(limit, 1), 200));
        return merchantId == null || merchantId.isBlank()
                ? payments.findAllByOrderByCreatedAtDesc(page)
                : payments.findByMerchantIdOrderByCreatedAtDesc(merchantId, page);
    }

    /** Full audit trail of status transitions for one payment, in the order they happened. */
    public List<PaymentEvent> timeline(String paymentId) {
        get(paymentId); // 404 if unknown
        return events.findByPaymentIdOrderByIdAsc(paymentId);
    }

    /**
     * Writes a domain event to the transactional outbox inside the caller's transaction, so
     * the event is published if and only if the state change commits. {@link
     * io.paylab.gateway.outbox.OutboxRelay} delivers it asynchronously.
     */
    private void enqueue(String eventType, Payment payment) {
        try {
            String payload = mapper.writeValueAsString(Map.of(
                    "paymentId", payment.getId(),
                    "status", payment.getStatus().name(),
                    "merchantId", payment.getMerchantId(),
                    "amount", payment.getAmount(),
                    "sourceCurrency", payment.getSourceCurrency(),
                    "targetCurrency", payment.getTargetCurrency()));
            outbox.save(new OutboxEvent("payment", payment.getId(), eventType, payload, Instant.now()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize outbox payload", e);
        }
    }
}
